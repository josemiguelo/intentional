package dev.josemiguel.intentional.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryTest {

    private val youtube = AppGroup("youtube", setOf("yt"), 30, 0L, setOf("youtube.com"))
    private val x = AppGroup("x", emptySet(), 30, 0L, setOf("x.com"))
    private val groups = listOf(youtube, x)
    private fun min(m: Long) = m * 60_000L

    @Test
    fun recentDaysOneByOneThenWholeWeeks() {
        // Saturday Nov 7, 3 days asked: Thu 5 back to its Monday, Nov 2. The weeks come before.
        val byDay = mapOf(
            "2026-11-07" to DayUsage(apps = mapOf("yt" to min(10)), opened = 2),
            "2026-11-05" to DayUsage(apps = mapOf("yt" to min(45)), sites = mapOf("x.com" to min(5)), closed = 1),
            "2026-11-03" to DayUsage(apps = mapOf("yt" to min(50))),
            // The week of Oct 26.
            "2026-10-26" to DayUsage(apps = mapOf("yt" to min(100))),
            "2026-11-01" to DayUsage(sites = mapOf("x.com" to min(20))),
            // Two weeks earlier, with a week of nothing between.
            "2026-10-13" to DayUsage(apps = mapOf("yt" to min(7))),
        )
        val h = history(byDay, groups, LocalDate(2026, 11, 7), days = 3, weeks = 3)
        assertEquals(
            listOf(
                HistoryDay(LocalDate(2026, 11, 7), listOf(GroupMinutes("youtube", 10, false)), 2, 0),
                HistoryDay(LocalDate(2026, 11, 5), listOf(GroupMinutes("youtube", 45, true), GroupMinutes("x", 5, false)), 0, 1),
                HistoryDay(LocalDate(2026, 11, 3), listOf(GroupMinutes("youtube", 50, true)), 0, 0),
            ),
            h.days,
        )
        assertEquals(
            listOf(
                HistoryWeek(LocalDate(2026, 10, 26), listOf(GroupMinutes("youtube", 100, false), GroupMinutes("x", 20, false)), 2),
                HistoryWeek(LocalDate(2026, 10, 12), listOf(GroupMinutes("youtube", 7, false)), 1),
            ),
            h.weeks,
        )
    }

    @Test
    fun formatsMinutes() {
        assertEquals("45m", formatMinutes(45))
        assertEquals("1h 05m", formatMinutes(65))
    }

    @Test
    fun cacheRoundTrips() {
        val byDay = mapOf("2026-11-07" to DayUsage(apps = mapOf("yt" to 5L), opened = 1))
        assertEquals(byDay, parseHistoryJson(byDay.toHistoryJson()))
    }
}
