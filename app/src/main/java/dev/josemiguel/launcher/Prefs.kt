package dev.josemiguel.launcher

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime

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

    /** Favorites that stay visible during wind-down. Empty set = show all. */
    var essentials: Set<String>
        get() = sp.getStringSet("essentials", emptySet())!!
        set(value) = sp.edit().putStringSet("essentials", value).apply()

    fun toggleEssential(packageName: String) {
        essentials = if (packageName in essentials) essentials - packageName
        else essentials + packageName
    }

    /** Apps whose notifications are batched. Seeded from considered on first read. */
    var batched: Set<String>
        get() = sp.getStringSet("batched", null) ?: considered
        set(value) = sp.edit().putStringSet("batched", value).apply()

    fun toggleBatched(packageName: String) {
        batched = if (packageName in batched) batched - packageName
        else batched + packageName
    }

    var strictMode: Boolean
        get() = sp.getBoolean("strict_mode", false)
        set(value) = sp.edit().putBoolean("strict_mode", value).apply()

    /** The user's own words, written while motivated; shown back at the door. */
    var strictReasons: String
        get() = sp.getString("strict_reasons", "")!!
        set(value) = sp.edit().putString("strict_reasons", value).apply()

    var strictSince: Long
        get() = sp.getLong("strict_since", 0L)
        set(value) = sp.edit().putLong("strict_since", value).apply()

    /** Times the user reached the leaving flow and chose to stay. */
    var strictStays: Int
        get() = sp.getInt("strict_stays", 0)
        set(value) = sp.edit().putInt("strict_stays", value).apply()

    var windDownEnabled: Boolean
        get() = sp.getBoolean("winddown_enabled", true)
        set(value) = sp.edit().putBoolean("winddown_enabled", value).apply()

    var windDownStart: LocalTime
        get() = minutesToTime(sp.getInt("winddown_start", 22 * 60))
        set(value) = sp.edit().putInt("winddown_start", timeToMinutes(value)).apply()

    var windDownEnd: LocalTime
        get() = minutesToTime(sp.getInt("winddown_end", 6 * 60))
        set(value) = sp.edit().putInt("winddown_end", timeToMinutes(value)).apply()

    var pauseSeconds: Int
        get() = sp.getInt("pause_seconds", 8)
        set(value) = sp.edit().putInt("pause_seconds", value.coerceIn(1, 300)).apply()

    var longPauseSeconds: Int
        get() = sp.getInt("long_pause_seconds", 25)
        set(value) = sp.edit().putInt("long_pause_seconds", value.coerceIn(1, 600)).apply()

    /** Batch delivery times for held notifications, sorted. */
    var dropTimes: List<LocalTime>
        get() = sp.getString("drop_times", "540,780,1140")!!
            .split(',')
            .filter { it.isNotBlank() }
            .map { minutesToTime(it.trim().toInt()) }
            .sorted()
        set(value) = sp.edit()
            .putString("drop_times", value.map(::timeToMinutes).distinct().sorted().joinToString(","))
            .apply()

    fun resetToday() {
        sp.edit()
            .remove("intentional-${todaySuffix()}")
            .remove("paused-${todaySuffix()}")
            .apply()
    }

    private fun minutesToTime(minutes: Int) = LocalTime.of(minutes / 60, minutes % 60)
    private fun timeToMinutes(time: LocalTime) = time.hour * 60 + time.minute

    fun frictionFor(packageName: String): Friction =
        Friction.of(sp.getInt("friction-$packageName", 0))

    fun cycleFriction(packageName: String) {
        sp.edit().putInt("friction-$packageName", frictionFor(packageName).next().ordinal).apply()
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

    /** Daily counters for the last [days] days, most recent first. */
    fun history(days: Int): List<DayCounts> {
        val today = LocalDate.now()
        return (0 until days).map { back ->
            val date = today.minusDays(back.toLong())
            DayCounts(
                date = date,
                intentional = sp.getInt("intentional-$date", 0),
                pausedClosed = sp.getInt("paused-$date", 0),
            )
        }
    }
}

data class DayCounts(
    val date: LocalDate,
    val intentional: Int,
    val pausedClosed: Int,
)
