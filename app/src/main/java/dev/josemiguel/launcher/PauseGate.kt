package dev.josemiguel.launcher

import android.os.SystemClock

/**
 * Decides when a considered app gets the pause screen, however it was opened:
 * from home, a notification, recents, or (on a TV) the stock launcher. Choosing
 * **open** starts a session; coming back within [AWAY_MS] of leaving continues
 * it, so a glance at a notification or an input switch doesn't pause again.
 *
 * Shared by the launcher (which opens through the pause) and the guard (which
 * catches every other way in); both run in the app's one process. A session
 * lost with the process only costs an extra pause.
 */
object PauseGate {

    private const val AWAY_MS = 5 * 60_000L

    /** Below this, a repeat prompt for the same app is the same arrival firing twice. */
    private const val PROMPT_DEBOUNCE_MS = 3_000L

    /** Per app with an open session: when it was last approved or last left. */
    private val sessions = mutableMapOf<String, Long>()

    private var promptedPkg: String? = null
    private var promptedAt = 0L

    /** They chose open at the pause: [pkg]'s session starts now. */
    fun approve(pkg: String) {
        sessions[pkg] = SystemClock.elapsedRealtime()
    }

    /** [pkg] left the foreground; its session, if any, now times out from here. */
    fun left(pkg: String) {
        if (pkg in sessions) sessions[pkg] = SystemClock.elapsedRealtime()
    }

    /** Whether [pkg], just come to the front, should be paused (once per arrival). */
    fun shouldPrompt(pkg: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val session = sessions[pkg]
        if (session != null) {
            if (now - session < AWAY_MS) return false
            sessions.remove(pkg)
        }
        if (pkg == promptedPkg && now - promptedAt < PROMPT_DEBOUNCE_MS) return false
        promptedPkg = pkg
        promptedAt = now
        return true
    }
}
