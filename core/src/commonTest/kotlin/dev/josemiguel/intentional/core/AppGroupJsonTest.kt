package dev.josemiguel.intentional.core

import kotlin.test.Test
import kotlin.test.assertEquals

class AppGroupJsonTest {

    @Test
    fun readsTheStoredFormat() {
        val raw = """[{"name":"x","packages":["com.twitter.android"],"limit":30,"changedAt":1730000000000,"sites":["twitter.com","x.com"]}]"""
        assertEquals(
            listOf(AppGroup("x", setOf("com.twitter.android"), 30, 1_730_000_000_000L, setOf("twitter.com", "x.com"))),
            parseGroups(raw),
        )
    }

    @Test
    fun readsGroupsSavedBeforeSites() {
        val raw = """[{"name":"social","packages":[],"limit":45,"changedAt":0}]"""
        assertEquals(emptySet(), parseGroups(raw).single().sites)
    }

    @Test
    fun writesEveryField() {
        val json = listOf(AppGroup("social", emptySet(), 45, 0L)).toGroupsJson()
        assertEquals("""[{"name":"social","packages":[],"limit":45,"changedAt":0,"sites":[]}]""", json)
    }

    @Test
    fun roundTrips() {
        val groups = listOf(
            AppGroup("youtube", setOf("com.google.android.youtube"), 30, 5L, setOf("youtube.com", "youtu.be")),
            AppGroup("reddit", emptySet(), 60, 0L, setOf("reddit.com")),
        )
        assertEquals(groups, parseGroups(groups.toGroupsJson()))
    }
}
