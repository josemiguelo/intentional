package dev.josemiguel.intentional.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GroupsTest {

    private val day = 86_400_000L
    private val groups = listOf(
        AppGroup("x", setOf("com.twitter.android"), 30, 0L, setOf("x.com", "twitter.com")),
        AppGroup("youtube", setOf("com.google.android.youtube"), 30, 0L, setOf("youtube.com")),
    )

    @Test
    fun sitesCoverTheirSubdomainsOnly() {
        assertTrue(siteCovers("youtube.com", "youtube.com"))
        assertTrue(siteCovers("youtube.com", "m.youtube.com"))
        assertFalse(siteCovers("youtube.com", "notyoutube.com"))
        assertFalse(siteCovers("youtube.com", "youtube.com.evil.net"))
    }

    @Test
    fun findsGroupsByAppAndSite() {
        assertEquals("x", groups.groupOf("com.twitter.android")?.name)
        assertNull(groups.groupOf("com.android.chrome"))
        assertEquals("youtube", groups.groupOfSite("m.youtube.com")?.name)
        assertNull(groups.groupOfSite("example.com"))
    }

    @Test
    fun mobileHostsMeanTheSite() {
        assertEquals("facebook.com", siteDomain("m.facebook.com"))
        assertEquals("example.com", siteDomain("example.com"))
        assertEquals("m.co", siteDomain("m.co"))
    }

    @Test
    fun anAppMovesBetweenGroups() {
        val moved = groups.withApp("com.twitter.android", "youtube")
        assertEquals(emptySet(), moved.single { it.name == "x" }.packages)
        assertEquals("youtube", moved.groupOf("com.twitter.android")?.name)
        assertNull(moved.withApp("com.twitter.android", null).groupOf("com.twitter.android"))
    }

    @Test
    fun aSiteMovesBetweenGroups() {
        val moved = groups.withSite("x.com", "youtube")
        assertEquals("youtube", moved.groupOfSite("x.com")?.name)
        assertEquals(setOf("twitter.com"), moved.single { it.name == "x" }.sites)
        assertEquals(setOf("twitter.com"), groups.withoutSite("x", "x.com").single { it.name == "x" }.sites)
    }

    @Test
    fun groupNamesAreUniqueIgnoringCase() {
        assertSame(groups, groups.withGroup("YouTube"))
        assertSame(groups, groups.withGroup("  "))
        val added = groups.withGroup(" reddit ").last()
        assertEquals(AppGroup("reddit", emptySet(), Budget.MAX_LIMIT_MINUTES, 0L), added)
        assertEquals(listOf("youtube"), groups.withoutGroup("x").map { it.name })
    }

    @Test
    fun limitsChangeOnceAWeekWithinBounds() {
        val now = 30 * day
        val changed = groups.withLimit("x", 500, now).single { it.name == "x" }
        assertEquals(Budget.MAX_LIMIT_MINUTES, changed.limitMinutes)
        assertEquals(now, changed.limitChangedAt)
        assertEquals(Budget.MIN_LIMIT_MINUTES, groups.withLimit("x", 1, now).single { it.name == "x" }.limitMinutes)

        // Locked for a week after a change.
        val locked = listOf(changed).withLimit("x", 10, now + 6 * day).single()
        assertEquals(changed, locked)
        assertEquals(10, listOf(changed).withLimit("x", 10, now + 7 * day).single().limitMinutes)
    }

    @Test
    fun daysUntilChangeableRoundsUp() {
        val group = AppGroup("x", emptySet(), 30, 10 * day)
        assertEquals(7, group.daysUntilChangeable(10 * day))
        assertEquals(1, group.daysUntilChangeable(16 * day + 1))
        assertEquals(0, group.daysUntilChangeable(17 * day))
        assertEquals(0, group.daysUntilChangeable(100 * day))
    }
}
