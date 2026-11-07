package dev.josemiguel.intentional.core

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * What the history view shows: each group's minutes a day across every
 * device, the recent days one by one and the weeks before in sums. A group
 * with no time on a day is left out of it. [over] compares with the group's
 * limit as it is now, which may not be the one that day had.
 */
data class GroupMinutes(val group: String, val minutes: Long, val over: Boolean)

data class HistoryDay(val day: LocalDate, val groups: List<GroupMinutes>, val opened: Int, val closed: Int)

/** A week from Monday [start]; [days] is how many of its days had any record. */
data class HistoryWeek(val start: LocalDate, val groups: List<GroupMinutes>, val days: Int)

data class History(val days: List<HistoryDay>, val weeks: List<HistoryWeek>)

/**
 * [byDay] (yyyy-mm-dd to every device's usage that day, summed) as days
 * one by one from [today] back at least [days] days, to the Monday that
 * week began, then up to [weeks] whole weeks before those, most recent
 * first. Days and weeks with no record at all are left out, so a history
 * that began recently is short.
 */
fun history(byDay: Map<String, DayUsage>, groups: List<AppGroup>, today: LocalDate, days: Int = 14, weeks: Int = 12): History {
    val earliest = today.minus(DatePeriod(days = days - 1))
    val monday = earliest.minus(DatePeriod(days = earliest.dayOfWeek.ordinal - DayOfWeek.MONDAY.ordinal))
    val dayRows = generateSequence(today) { it.minus(DatePeriod(days = 1)) }
        .takeWhile { it >= monday }
        .mapNotNull { date ->
            val usage = byDay[date.toString()] ?: return@mapNotNull null
            HistoryDay(date, minutesOf(groups, usage, limitDays = 1), usage.opened, usage.closed)
        }
        .toList()
    var weekEnd = monday
    val weekRows = mutableListOf<HistoryWeek>()
    repeat(weeks) {
        val start = weekEnd.minus(DatePeriod(days = 7))
        val inWeek = (0 until 7).mapNotNull { byDay[start.plus(DatePeriod(days = it)).toString()] }
        if (inWeek.isNotEmpty()) {
            weekRows += HistoryWeek(start, minutesOf(groups, inWeek.reduce(DayUsage::plus), limitDays = 7), inWeek.size)
        }
        weekEnd = start
    }
    return History(dayRows, weekRows)
}

private fun minutesOf(groups: List<AppGroup>, usage: DayUsage, limitDays: Int) = groups.mapNotNull { g ->
    val minutes = g.usedMs(usage) / 60_000
    if (minutes == 0L) null else GroupMinutes(g.name, minutes, minutes >= g.limitMinutes.toLong() * limitDays)
}

/** "45m", "1h 05m": minutes as a person reads them. */
fun formatMinutes(minutes: Long): String =
    if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"

/** A day-to-usage map as JSON, for a device's cache of the synced history. */
private val historyJson = Json { ignoreUnknownKeys = true }
private val historySerializer = MapSerializer(String.serializer(), DayUsage.serializer())

fun Map<String, DayUsage>.toHistoryJson(): String = historyJson.encodeToString(historySerializer, this)

fun parseHistoryJson(raw: String): Map<String, DayUsage> = historyJson.decodeFromString(historySerializer, raw)
