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

class UsageAcrossDevicesTest {

    private val groups = listOf(
        AppGroup("youtube", setOf("com.google.android.youtube", "hypr:freetube"), 30, 0L, setOf("youtube.com")),
    )

    @Test
    fun devicesAddUp() {
        val phone = DayUsage(apps = mapOf("com.google.android.youtube" to 600_000L), sites = mapOf("m.youtube.com" to 60_000L))
        val desktop = DayUsage(apps = mapOf("hypr:freetube" to 300_000L), sites = mapOf("m.youtube.com" to 40_000L, "www.youtube.com" to 5_000L))
        val total = phone + desktop
        assertEquals(100_000L, total.sites["m.youtube.com"])
        assertEquals(1_005_000L, groups.single().usedMs(total))
    }

    @Test
    fun onlyGroupTimeLeavesTheDevice() {
        val day = DayUsage(
            apps = mapOf("hypr:freetube" to 1L, "hypr:kitty" to 2L),
            sites = mapOf("m.youtube.com" to 3L, "github.com" to 4L),
        )
        assertEquals(DayUsage(apps = mapOf("hypr:freetube" to 1L), sites = mapOf("m.youtube.com" to 3L)), day.onlyFor(groups))
    }
}
