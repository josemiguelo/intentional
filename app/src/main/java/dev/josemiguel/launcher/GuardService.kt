package dev.josemiguel.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import dev.josemiguel.launcher.guard.GuardBypass
import dev.josemiguel.launcher.guard.GuardPolicy

/**
 * The launcher's watchdog. It does two jobs, both triggered by foreground-app
 * changes:
 *
 * 1. Strict mode (always on): landing on the screen that switches the default
 *    launcher — or the accessibility / device-admin page that could disable
 *    this service or uninstall the app — bounces back home. Which screen counts
 *    as "the launcher switcher" is decided per-OEM by [GuardPolicy]; everything
 *    else in Settings (the general "Default apps" list included) stays reachable.
 *
 * 2. Hard time limits: while any app is in the foreground, its today's usage is
 *    polled against [Prefs.limitMinutes]; once over, the app is closed to home.
 *    This is what makes the per-app hour cap a real constraint rather than a
 *    launch-time suggestion — notifications, recents, and links can't get around
 *    it. Requires usage access; without it, limits silently don't enforce.
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

    private val limitPoll = object : Runnable {
        override fun run() {
            val pkg = monitoredPackage ?: return
            if (isOverLimit(pkg)) {
                Log.i(TAG, "limit reached: $pkg")
                Toast.makeText(
                    this@GuardService,
                    "daily limit reached — come back tomorrow",
                    Toast.LENGTH_SHORT,
                ).show()
                performGlobalAction(GLOBAL_ACTION_HOME)
                // Leave monitoredPackage set: if they reopen it, the next window
                // event re-arms the poll and kicks them out again.
            } else {
                handler.postDelayed(this, POLL_MS)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "connected, policy=${policy.javaClass.simpleName}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        updateLimitMonitoring(pkg)

        // Strict-mode blocking is scoped to system settings screens.
        val watched = pkg.contains("settings", ignoreCase = true) ||
            pkg.contains("permissioncontroller", ignoreCase = true)
        if (!watched) return
        if (!Prefs(this).strictMode) return

        val cls = event.className?.toString() ?: ""
        val screenText = event.text.joinToString(" ").lowercase()

        val isHomePicker = policy.isHomePickerScreen(pkg, cls, screenText)
        val isOwnAccessibilityScreen = cls.contains("Accessibility", ignoreCase = true) &&
            screenText.contains("intentional")
        // The admin add/deactivate screen's window text does NOT include the
        // app label (verified on One UI), so there's no way to scope this to
        // our own admin — while strict mode is on, the whole device-admin
        // page is off-limits except during our own activation bypass window.
        val isAdminScreen = cls.contains("DeviceAdmin", ignoreCase = true) &&
            !GuardBypass.adminScreenAllowed()
        // The Private DNS chooser is an AlertDialog (generic class), so match on
        // its window text — locks the DNS-based web filter against changes.
        val isPrivateDnsScreen = screenText.contains("private dns")

        if (isHomePicker || isOwnAccessibilityScreen || isAdminScreen || isPrivateDnsScreen) {
            Log.i(TAG, "blocked: $cls")
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
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
        val used = UsageStatsRepository.groupMinutesToday(this, group.packages)
        return used >= group.limitMinutes
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
        return super.onUnbind(intent)
    }

    private companion object {
        const val TAG = "GuardService"
        const val POLL_MS = 20_000L
        const val LAUNCHABLE_TTL_MS = 300_000L
    }
}
