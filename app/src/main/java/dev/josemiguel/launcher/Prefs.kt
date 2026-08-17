package dev.josemiguel.launcher

import android.content.Context
import java.time.LocalDate

/**
 * Persistence for the launcher's three pieces of state: the favorites list
 * (ordered), the "considered" set (apps that only open through the pause
 * screen), and per-day open counters. The counters are the launcher's whole
 * metric system: intentional opens vs. paused-and-closed — never minutes.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    var favorites: List<String>
        get() = sp.getString("favorites", "")!!.split('\n').filter { it.isNotBlank() }
        set(value) = sp.edit().putString("favorites", value.joinToString("\n")).apply()

    var considered: Set<String>
        get() = sp.getStringSet("considered", emptySet())!!
        set(value) = sp.edit().putStringSet("considered", value).apply()

    fun toggleFavorite(packageName: String) {
        favorites = if (packageName in favorites) favorites - packageName
        else favorites + packageName
    }

    fun toggleConsidered(packageName: String) {
        considered = if (packageName in considered) considered - packageName
        else considered + packageName
    }

    private fun todaySuffix() = LocalDate.now().toString()

    fun incrementIntentional() = increment("intentional-${todaySuffix()}")

    fun incrementPausedClosed() = increment("paused-${todaySuffix()}")

    private fun increment(key: String) {
        sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply()
    }

    /** (intentional opens, paused-and-closed) for today. */
    fun countsToday(): Pair<Int, Int> =
        sp.getInt("intentional-${todaySuffix()}", 0) to sp.getInt("paused-${todaySuffix()}", 0)
}
