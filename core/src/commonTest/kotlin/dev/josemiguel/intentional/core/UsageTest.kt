package dev.josemiguel.intentional.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsageTest {

    private val youtube = AppGroup("youtube", setOf("youtube-app"), 30, 0L, setOf("youtube.com", "youtu.be"))

    @Test
    fun accumulates() {
        val day = DayUsage().plusApp("kitty", 1_000).plusApp("kitty", 500).plusSite("x.com", 2_000)
        assertEquals(1_500L, day.apps["kitty"])
        assertEquals(2_000L, day.sites["x.com"])
    }

    @Test
    fun groupSpendsItsAppsAndSitesWithSubdomains() {
        val day = DayUsage(
            apps = mapOf("youtube-app" to 60_000L, "kitty" to 999_999L),
            sites = mapOf("m.youtube.com" to 30_000L, "youtu.be" to 10_000L, "notyoutube.com" to 5_000L),
        )
        assertEquals(100_000L, youtube.usedMs(day))
    }

    @Test
    fun overBudgetAtTheLimit() {
        assertFalse(youtube.isOverBudget(DayUsage(sites = mapOf("youtube.com" to 30 * 60_000L - 1))))
        assertTrue(youtube.isOverBudget(DayUsage(sites = mapOf("youtube.com" to 30 * 60_000L))))
    }

    @Test
    fun roundTrips() {
        val day = DayUsage(apps = mapOf("kitty" to 5L), sites = mapOf("x.com" to 7L))
        assertEquals(day, DayUsage.parse(day.toJson()))
        assertEquals(DayUsage(), DayUsage.parse("{}"))
    }
}
