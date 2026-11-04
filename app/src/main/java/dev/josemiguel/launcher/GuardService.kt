package dev.josemiguel.launcher

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import dev.josemiguel.launcher.guard.GuardBypass
import dev.josemiguel.launcher.guard.GuardPolicy
import dev.josemiguel.launcher.guard.ScreenInfo

/**
 * The launcher's watchdog. It does three jobs, all triggered by foreground-app
 * changes:
 *
 * 1. Strict mode (always on): landing on the screen that switches the default
 *    launcher — or the accessibility / device-admin page that could disable
 *    this service or uninstall the app, or the VPN / Private DNS / browser
 *    secure-DNS settings that could switch off site budgets — bounces back home. Which screen counts
 *    as "the launcher switcher" is decided per-OEM by [GuardPolicy]; everything
 *    else in Settings (the general "Default apps" list included) stays reachable.
 *
 * 2. Hard time limits: while any app is in the foreground, its today's usage is
 *    polled against [Prefs.limitMinutes]; once over, the app is closed to home.
 *    This is what makes the per-app hour cap a real constraint rather than a
 *    launch-time suggestion — notifications, recents, and links can't get around
 *    it. Requires usage access; without it, limits silently don't enforce.
 *
 * 3. Website time: the browser's address bar feeds [SiteTracker], which counts
 *    on-screen time per site toward group budgets; a site whose group is over
 *    budget bounces home like an over-limit app.
 *
 * Hard-won platform notes (Samsung, Android 13+):
 * - Sideloaded apps need the ACCESS_RESTRICTED_SETTINGS appop allowed or the
 *   system silently drops event delivery even though the service binds:
 *   `adb shell appops set dev.josemiguel.launcher ACCESS_RESTRICTED_SETTINGS allow`
 * - Registering only typeWindowStateChanged without isAccessibilityTool got
 *   zero events on One UI; the current guard_service.xml config is the one
 *   that works. Don't narrow it without retesting.
 * - After every reinstall the service must be rebind-cycled (see README).
 */
class GuardService : AccessibilityService() {

    private val policy = GuardPolicy.forDevice()
    private val handler = Handler(Looper.getMainLooper())

    /** The app currently being polled for its time limit, or null. */
    private var monitoredPackage: String? = null

    /** Cached set of launchable packages, so limits apply only to real apps. */
    private var launchable: Set<String> = emptySet()
    private var launchableRefreshedAt = 0L

    private var lastPipCheck = 0L

    /** The overlay hiding a blocked screen while HOME animates, or null. */
    private var shield: View? = null

    private val siteTracker by lazy {
        SiteTracker(this, handler) {
            siteChecked = null
            enforceSiteBudget()
        }
    }
    private var lastSiteRead = 0L

    /** The site last checked against its group budget, and when, so a long visit re-checks every [POLL_MS]. */
    private var siteChecked: String? = null
    private var siteCheckedAt = 0L

    /** Screen off ends any website time, whatever the browser does. */
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = siteTracker.stop()
    }

    // Unbinding can come before onServiceConnected (a quick off/on toggle).
    private var screenOffRegistered = false

    /** Sessions we've attached a re-stop callback to, so play attempts bounce back. */
    private val watchedSessions = mutableSetOf<MediaSession.Token>()

    private val limitPoll = object : Runnable {
        override fun run() {
            val pkg = monitoredPackage ?: return
            if (isOverLimit(pkg)) {
                Log.i(TAG, "limit reached: $pkg")
                SiteBlockVpnService.refreshSoon()
                Toast.makeText(
                    this@GuardService,
                    "daily limit reached — come back tomorrow",
                    Toast.LENGTH_SHORT,
                ).show()
                performGlobalAction(GLOBAL_ACTION_HOME)
                // HOME doesn't close picture-in-picture apps (YouTube drops into
                // the mini-player and keeps playing), so stop its playback too.
                handler.postDelayed({ stopOverLimitMedia() }, MEDIA_SETTLE_MS)
                // Leave monitoredPackage set: if they reopen it, the next window
                // event re-arms the poll and kicks them out again.
            } else {
                handler.postDelayed(this, POLL_MS)
            }
        }
    }

    private var sessionsListener: MediaSessionManager.OnActiveSessionsChangedListener? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "connected, policy=${policy.javaClass.simpleName}")
        registerMediaSessionListener()
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        screenOffRegistered = true
    }

    /**
     * Listens for media sessions so an over-budget app's playback is stopped the
     * instant it starts — a session appearing fires the listener, and each
     * over-budget session gets a callback that re-stops it on any resume.
     * Needs notification access; without it this quietly does nothing.
     */
    private fun registerMediaSessionListener() {
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            controllers?.forEach(::stopIfOverLimit)
        }
        try {
            manager.addOnActiveSessionsChangedListener(
                listener,
                ComponentName(this, NotifListener::class.java),
                handler,
            )
            sessionsListener = listener
            manager.getActiveSessions(ComponentName(this, NotifListener::class.java))
                .forEach(::stopIfOverLimit)
        } catch (e: SecurityException) {
            // notification access not granted
        }
    }

    /**
     * Feeds [siteTracker]. Browser events fire many times a second, so the
     * address bar is read at most once per [SITE_READ_MS]. Another app's window
     * stops the clock only if it actually took over the active window — the
     * keyboard or a status-bar pull-down leave the browser active.
     */
    private fun trackSite(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg in SiteTracker.BROWSERS) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastSiteRead < SITE_READ_MS) return
            lastSiteRead = now
            val root = rootInActiveWindow ?: return
            if (root.packageName?.toString() in SiteTracker.BROWSERS) {
                if (Prefs(this).strictMode && isBrowserSecureDnsPage(root)) return block("browser secure DNS")
                siteTracker.onBrowserWindow(root)
                enforceSiteBudget()
            }
        } else if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (rootInActiveWindow?.packageName?.toString() !in SiteTracker.BROWSERS) siteTracker.stop()
        }
    }

    /**
     * Bounces home off a site whose group is over budget. Checked when the site
     * changes, every [POLL_MS] while the page fires events, and on each
     * SiteTracker save (an idle page fires none) — the budget query reads the
     * day's usage events, too costly for every address-bar read.
     */
    private fun enforceSiteBudget() {
        val host = siteTracker.current ?: return
        val now = SystemClock.elapsedRealtime()
        if (host == siteChecked && now - siteCheckedAt < POLL_MS) return
        siteChecked = host
        siteCheckedAt = now

        val group = Prefs(this).groupOfSite(host) ?: return
        if (UsageStatsRepository.groupUsedMinutes(this, group) < group.limitMinutes) return
        Log.i(TAG, "site budget used: $host")
        Toast.makeText(this, "$host · ${group.name.lowercase()} budget used today", Toast.LENGTH_SHORT).show()
        siteTracker.stop()
        SiteBlockVpnService.refreshSoon()
        // Reopening the browser on the same tab must re-check at once.
        siteChecked = null
        showShield()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val type = event.eventType
        trackSite(event)
        // PiP entering/leaving fires TYPE_WINDOWS_CHANGED, not a state change.
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            stopOverLimitMedia()
        }
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        updateLimitMonitoring(pkg)

        // Strict-mode blocking is scoped to system settings screens.
        val watched = pkg.contains("settings", ignoreCase = true) ||
            pkg.contains("permissioncontroller", ignoreCase = true) ||
            pkg == "com.android.vpndialogs"
        if (!watched) return
        if (!Prefs(this).strictMode) return

        val screen = ScreenInfo(
            packageName = pkg,
            className = event.className?.toString() ?: "",
            text = event.text.joinToString(" ").lowercase(),
        )

        // The device-admin page can't be scoped to our own admin (its text omits
        // the app label), so the whole page is off-limits except during our own
        // activation bypass window.
        val isAdminScreen = policy.isDeviceAdminScreen(screen) && !GuardBypass.adminScreenAllowed()

        val blocked = policy.isHomePicker(screen) ||
            policy.isOwnAccessibilityScreen(screen) ||
            isAdminScreen ||
            policy.isPrivateDnsScreen(screen) ||
            policy.isVpnControlScreen(screen)

        if (blocked) block(screen.className)
    }

    private fun block(what: String) {
        Log.i(TAG, "blocked: $what")
        showShield()
        // BACK closes the screen itself (HOME alone leaves it in Recents to
        // be reopened); HOME then lands on the launcher.
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /**
     * A browser's own secure-DNS setting, whose custom provider would resolve
     * around the site VPN. Browser settings are fragments that fire no window
     * change, so this reads the content: only checked when the window has no
     * address bar (a settings page, not a website).
     */
    private fun isBrowserSecureDnsPage(root: AccessibilityNodeInfo): Boolean {
        val pkg = root.packageName?.toString() ?: return false
        if (root.findAccessibilityNodeInfosByViewId("$pkg:id/url_bar").isNotEmpty()) return false
        return root.findAccessibilityNodeInfosByText("Choose another provider").isNotEmpty()
    }

    /**
     * Covers the screen with an opaque, touch-swallowing overlay the instant a
     * blocked screen is detected. The HOME transition takes ~800ms on One UI,
     * and the blocked screen stays tappable throughout it without this — enough
     * to hit "Deactivate" on the device-admin dialog. Removed on a timer so it
     * can never strand the phone behind it.
     */
    private fun showShield() {
        handler.removeCallbacks(hideShield)
        if (shield == null) {
            val view = View(this).apply {
                setBackgroundColor(Color.BLACK)
                setOnTouchListener { _, _ -> true }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.OPAQUE,
            ).apply {
                // Without these the overlay stops below the status bar / camera
                // cutout, leaving a strip where the HOME animation shows through.
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
            }
            try {
                getSystemService(WindowManager::class.java).addView(view, params)
                shield = view
            } catch (e: Exception) {
                Log.w(TAG, "shield failed", e)
                return
            }
        }
        handler.postDelayed(hideShield, SHIELD_MS)
    }

    private val hideShield = Runnable {
        shield?.let { getSystemService(WindowManager::class.java).removeView(it) }
        shield = null
    }

    /** (Re)arms or stops the time-limit poll for the newly-foreground app. */
    private fun updateLimitMonitoring(pkg: String) {
        handler.removeCallbacks(limitPoll)
        monitoredPackage = null

        if (pkg == packageName) return
        if (!UsageStatsRepository.hasPermission(this)) return
        if (pkg !in launchablePackages()) return
        if (Prefs(this).groupOf(pkg) == null) return

        monitoredPackage = pkg
        limitPoll.run()
    }

    private fun isOverLimit(pkg: String): Boolean {
        val group = Prefs(this).groupOf(pkg) ?: return false
        val used = UsageStatsRepository.groupUsedMinutes(this, group)
        return used >= group.limitMinutes
    }

    /**
     * Stops media playback for any app over its group budget. This is the fix
     * for PiP-capable apps (YouTube): HOME can't close them — they drop into the
     * mini-player and keep playing — and a normal service can't force-stop them.
     * Sending Stop to the app's media session (via our notification-listener
     * component) reliably halts playback, PiP or not. Needs notification access;
     * without it this silently no-ops.
     */
    private fun stopOverLimitMedia() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPipCheck < PIP_CHECK_THROTTLE_MS) return
        lastPipCheck = now
        if (!UsageStatsRepository.hasPermission(this)) return

        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        val controllers = try {
            manager.getActiveSessions(ComponentName(this, NotifListener::class.java))
        } catch (e: SecurityException) {
            return
        }
        controllers.forEach(::stopIfOverLimit)
    }

    private fun stopIfOverLimit(controller: MediaController) {
        val pkg = controller.packageName ?: return
        if (!isOverLimit(pkg)) return
        Log.i(TAG, "stopping media: $pkg")
        controller.transportControls.pause()
        controller.transportControls.stop()
        watchSession(controller, pkg)
    }

    /**
     * Attaches a callback that re-stops the session the moment it resumes, so
     * tapping play on a stopped over-budget app (PiP included) just bounces back
     * to stopped. Detaches itself once the app is no longer over budget (e.g.
     * the next day) so playback is allowed again.
     */
    private fun watchSession(controller: MediaController, pkg: String) {
        val token = controller.sessionToken
        if (!watchedSessions.add(token)) return
        controller.registerCallback(object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                val playing = state?.state == PlaybackState.STATE_PLAYING ||
                    state?.state == PlaybackState.STATE_BUFFERING
                if (!playing) return
                if (isOverLimit(pkg)) {
                    controller.transportControls.pause()
                    controller.transportControls.stop()
                } else {
                    controller.unregisterCallback(this)
                    watchedSessions.remove(token)
                }
            }

            override fun onSessionDestroyed() {
                watchedSessions.remove(token)
            }
        }, handler)
    }

    private fun launchablePackages(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (launchable.isEmpty() || now - launchableRefreshedAt > LAUNCHABLE_TTL_MS) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            launchable = packageManager.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName }
                .toSet()
            launchableRefreshedAt = now
        }
        return launchable
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(limitPoll)
        handler.removeCallbacks(hideShield)
        hideShield.run()
        siteTracker.stop()
        if (screenOffRegistered) unregisterReceiver(screenOff)
        screenOffRegistered = false
        sessionsListener?.let {
            getSystemService(MediaSessionManager::class.java)
                ?.removeOnActiveSessionsChangedListener(it)
        }
        return super.onUnbind(intent)
    }

    private companion object {
        const val TAG = "GuardService"
        const val POLL_MS = 20_000L
        const val LAUNCHABLE_TTL_MS = 300_000L
        const val PIP_CHECK_THROTTLE_MS = 800L
        const val MEDIA_SETTLE_MS = 700L
        const val SHIELD_MS = 1_200L
        const val SITE_READ_MS = 1_000L
    }
}
