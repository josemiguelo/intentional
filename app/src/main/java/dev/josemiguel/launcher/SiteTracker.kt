package dev.josemiguel.launcher

import android.content.Context
import android.net.Uri
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
class SiteTracker(private val context: Context, private val handler: Handler) {

    /** The host time is currently accruing to, or null when not counting. */
    private var current: String? = null
    private var since = 0L

    private val flushTick = object : Runnable {
        override fun run() {
            credit()
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

        /** Browsers whose address bar is the Chromium `url_bar` view. */
        val BROWSERS = setOf("com.android.chrome", "com.brave.browser")

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
