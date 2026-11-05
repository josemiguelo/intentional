package dev.josemiguel.launcher

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Measures on-screen time per website, the browser half of a group's budget
 * (the app half comes from UsageStats). The guard feeds it the browser's
 * address bar; time accrues to the current host while the browser stays in
 * front with the screen on, and stops the moment either ends.
 *
 * Verified live on Brave (Android 16, 2026-11): the address bar exposes the
 * host+path without scheme, in tabs, private tabs and Custom Tabs alike. It
 * vanishes from the tree while the toolbar is scrolled away, so a missing bar
 * keeps the last host rather than stopping the clock.
 */
class SiteTracker(
    private val context: Context,
    private val handler: Handler,
    /** Runs after each periodic save, so budgets are re-checked even when the page sits idle. */
    private val onTick: () -> Unit,
) {

    /** The host time is currently accruing to, or null when not counting. */
    var current: String? = null
        private set
    private var since = 0L

    private val flushTick = object : Runnable {
        override fun run() {
            credit()
            onTick()
            if (current != null) handler.postDelayed(this, FLUSH_MS)
        }
    }

    /**
     * Reads the address bar of [root] (a browser window) and switches the
     * clock to its host. A hidden bar, one being edited, or one showing a
     * non-address (about:blank) leaves the current host running; the empty
     * new-tab hint stops it.
     */
    fun onBrowserWindow(root: AccessibilityNodeInfo) {
        val pkg = root.packageName?.toString() ?: return
        val bar = root.findAccessibilityNodeInfosByViewId("$pkg:id/url_bar").firstOrNull()
            ?: return
        when {
            bar.isShowingHintText -> start(null)
            bar.isFocused -> Unit
            else -> hostOf(bar.text?.toString() ?: "")?.let(::start)
        }
    }

    /**
     * Points the tab in [root] at the browser's new-tab page by typing into its
     * address bar, so a blocked site isn't the tab the browser reopens on —
     * HOME alone leaves it there, and every reopen bounces straight back out.
     * False if it couldn't: the bar is scrolled away, an action is refused, or
     * the platform has no IME-enter action (API 29). True means the address
     * was sent, not that the page changed; the next bar read shows that.
     */
    fun redirectTab(root: AccessibilityNodeInfo): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val pkg = root.packageName?.toString() ?: return false
        val target = NEW_TAB[pkg] ?: return false
        val bar = root.findAccessibilityNodeInfosByViewId("$pkg:id/url_bar").firstOrNull()
            ?: return false
        if (bar.isFocused) return enter(bar, target)
        if (!bar.performAction(AccessibilityNodeInfo.ACTION_FOCUS) &&
            !bar.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        ) return false
        // The bar switches into edit mode after the focus returns, wiping
        // anything typed before it (verified on Brave): type once it settles.
        handler.postDelayed({
            bar.refresh()
            if (!enter(bar, target)) Log.w(TAG, "redirect refused after focus")
        }, EDIT_MODE_MS)
        return true
    }

    private fun enter(bar: AccessibilityNodeInfo, address: String): Boolean {
        val text = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, address)
        }
        if (!bar.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text)) return false
        return bar.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    /** The browser left the screen, or the screen turned off. */
    fun stop() = start(null)

    private fun start(host: String?) {
        if (host == current) return
        credit()
        current = host
        handler.removeCallbacks(flushTick)
        if (host != null) handler.postDelayed(flushTick, FLUSH_MS)
    }

    /** Books the time since the last credit to the current host. */
    private fun credit() {
        val now = SystemClock.elapsedRealtime()
        val host = current
        if (host != null && now > since) {
            val total = Prefs(context).addSiteTime(host, now - since)
            Log.i(TAG, "$host +${(now - since) / 1000}s (today ${total / 60_000}m ${total / 1000 % 60}s)")
        }
        since = now
    }

    companion object {
        private const val TAG = "SiteTracker"
        private const val FLUSH_MS = 30_000L
        private const val EDIT_MODE_MS = 300L

        /** Browsers whose address bar is the Chromium `url_bar` view. */
        val BROWSERS = setOf("com.android.chrome", "com.brave.browser")

        /** Each browser's new-tab page: its bar shows the hint text, which stops the clock. */
        private val NEW_TAB = mapOf(
            "com.android.chrome" to "chrome://newtab",
            "com.brave.browser" to "brave://newtab",
        )

        /** "m.facebook.com/?locale=es_LA" → "m.facebook.com"; null for non-addresses. */
        fun hostOf(text: String): String? {
            val t = text.trim().lowercase()
            if (t.isEmpty() || ' ' in t) return null
            val host = Uri.parse(if ("://" in t) t else "https://$t").host ?: return null
            if ('.' !in host) return null
            return host.removePrefix("www.")
        }
    }
}
