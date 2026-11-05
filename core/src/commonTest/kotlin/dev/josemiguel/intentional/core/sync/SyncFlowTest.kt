package dev.josemiguel.intentional.core.sync

import dev.josemiguel.intentional.core.AppGroup
import dev.josemiguel.intentional.core.DayUsage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * An in-memory stand-in for Firebase: sign-in and token refresh, the database's GET
 * and PUT under /users/u1/, the server timestamp, and the rules' week
 * between limit changes.
 */
private class FakeFirebase(var now: Long = 1_000_000_000_000L) {
    val tree = mutableMapOf<String, JsonElement>()

    val engine = MockEngine { req -> handle(req) }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.handle(req: HttpRequestData) = when {
        req.url.host == "identitytoolkit.googleapis.com" ->
            json("""{"localId":"u1","idToken":"t","refreshToken":"r","expiresIn":"3600"}""")
        req.url.host == "securetoken.googleapis.com" ->
            json("""{"user_id":"u1","id_token":"t2","refresh_token":"r2","expires_in":"3600"}""")
        else -> {
            val path = req.url.encodedPath.removePrefix("/users/u1/").removeSuffix(".json")
            if (req.method == HttpMethod.Get) json(read(path)?.toString() ?: "null")
            else {
                val value = resolve(Json.parseToJsonElement((req.body as TextContent).text))
                val old = if (path.startsWith("limits/")) tree[path]?.jsonObject else null
                if (old != null && now - old["changedAt"]!!.jsonPrimitive.long < WEEK) {
                    respond("""{"error":"Permission denied"}""", HttpStatusCode.Unauthorized)
                } else {
                    tree[path] = value
                    json(value.toString())
                }
            }
        }
    }

    /** A written path, or the children written one level below it. */
    private fun read(path: String): JsonElement? = tree[path] ?: tree
        .filterKeys { it.startsWith("$path/") }
        .mapKeys { it.key.removePrefix("$path/") }
        .takeIf { it.isNotEmpty() }
        ?.let(::JsonObject)

    private fun resolve(e: JsonElement): JsonElement =
        if (e is JsonObject && e.containsKey(".sv")) JsonPrimitive(now)
        else if (e is JsonObject) JsonObject(e.mapValues { resolve(it.value) })
        else e

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.json(body: String) =
        respond(body, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))

    companion object {
        const val WEEK = 7 * 86_400_000L
    }
}

class SyncFlowTest {

    private val key = Vault.newKey()
    private val server = FakeFirebase()

    private fun device(name: String) = Sync(
        SyncConfig("https://db.example", "api-key", "me@example.com", "pw", key, name),
        HttpClient(server.engine) { expectSuccess = false },
    )

    private val youtube = AppGroup("youtube", setOf("com.google.android.youtube"), 30, 5L, setOf("youtube.com"))

    @Test
    fun devicesSeeEachOthersDayAndTheServerSeesBlobs() = runTest {
        val phone = device("phone")
        val desktop = device("desktop")
        phone.exchangeUsage("2026-11-05", DayUsage(sites = mapOf("youtube.com" to 60_000L)), server.now)
        val seen = desktop.exchangeUsage("2026-11-05", DayUsage(sites = mapOf("youtube.com" to 30_000L)), server.now)
        assertEquals(mapOf("phone" to DayUsage(sites = mapOf("youtube.com" to 60_000L))), seen)
        assertTrue(server.tree.values.none { "youtube" in it.toString() }, "only ciphertext reaches the server")
        assertEquals(setOf("desktop"), phone.exchangeUsage("2026-11-05", DayUsage(), server.now).keys)
    }

    @Test
    fun groupsTravelAndTheNewestEditWins() = runTest {
        val phone = device("phone")
        val tv = device("tv")

        // The phone syncs first: its groups go up.
        val first = phone.reconcileGroups(listOf(youtube), synced = null, server.now)
        assertEquals(GroupsChange.PUSHED, first.change)

        // A fresh TV takes them rather than pushing its empty list.
        val fresh = tv.reconcileGroups(emptyList(), synced = null, server.now)
        assertEquals(GroupsChange.ADOPTED, fresh.change)
        assertEquals(listOf(youtube), fresh.groups)

        // Nothing changed: nothing moves.
        assertEquals(GroupsChange.NONE, tv.reconcileGroups(fresh.groups, fresh.synced, server.now).change)

        // An edit on the TV goes up, and the phone takes it on its next cycle.
        server.now += 1_000
        val edited = listOf(youtube.copy(sites = youtube.sites + "youtu.be"))
        assertEquals(GroupsChange.PUSHED, tv.reconcileGroups(edited, fresh.synced, server.now).change)
        val back = phone.reconcileGroups(first.groups, first.synced.copy(updatedAt = first.synced.updatedAt - 1), server.now)
        assertEquals(GroupsChange.ADOPTED, back.change)
        assertEquals(edited, back.groups)
    }

    @Test
    fun aFirstSyncMergesSoNoDeviceLosesItsApps() = runTest {
        // The desktop got there first, with its own apps and no Android packages...
        val desktop = device("desktop")
        val desktopGroups = listOf(youtube.copy(packages = setOf("hypr:freetube")), AppGroup("reddit", emptySet(), 30, 0L, setOf("reddit.com")))
        desktop.reconcileGroups(desktopGroups, synced = null, server.now)

        // ...the phone's first sync keeps its packages and adds its own group.
        val phoneGroups = listOf(youtube, AppGroup("x", setOf("com.twitter.android"), 30, 0L, setOf("x.com")))
        val first = device("phone").reconcileGroups(phoneGroups, synced = null, server.now)
        assertEquals(GroupsChange.PUSHED, first.change)
        assertEquals(setOf("hypr:freetube", "com.google.android.youtube"), first.groups.single { it.name == "youtube" }.packages)
        assertEquals(listOf("youtube", "reddit", "x"), first.groups.map { it.name })

        // The desktop takes the merge on its next cycle.
        server.now += 1_000
        val back = desktop.reconcileGroups(desktopGroups, SyncedGroups(desktopGroups, 0L), server.now)
        assertEquals(GroupsChange.ADOPTED, back.change)
        assertEquals(first.groups, back.groups)
    }

    @Test
    fun consideredAppsTravelAndMergeOnAFirstSync() = runTest {
        val phone = device("phone")
        val desktop = device("desktop")
        val first = phone.reconcileConsidered(mapOf("com.twitter.android" to 0, "com.zhiliaoapp.musically.go" to 2), null, server.now)
        assertEquals(GroupsChange.PUSHED, first.change)

        // The desktop's first sync adds its own, keeping the phone's friction for a shared app.
        val merged = desktop.reconcileConsidered(mapOf("hypr:steam" to 1, "com.twitter.android" to 3), null, server.now)
        assertEquals(GroupsChange.PUSHED, merged.change)
        assertEquals(mapOf("com.twitter.android" to 0, "com.zhiliaoapp.musically.go" to 2, "hypr:steam" to 1), merged.apps)

        server.now += 1_000
        val back = phone.reconcileConsidered(first.apps, first.synced.copy(updatedAt = first.synced.updatedAt - 1), server.now)
        assertEquals(GroupsChange.ADOPTED, back.change)
        assertEquals(merged.apps, back.apps)
        assertTrue(server.tree.values.none { "steam" in it.toString() }, "only ciphertext reaches the server")
    }

    @Test
    fun limitsCarryOverOnceThenChangeOnlyWeekly() = runTest {
        val phone = device("phone")

        // Set before sync (changedAt 5): carried over as is.
        val carried = phone.reconcileLimits(listOf(youtube), editedHere = false, server.now)
        assertEquals(youtube, carried.single())

        // Carried over long ago, so the first change is allowed and stamped now.
        val changed = phone.reconcileLimits(listOf(youtube.copy(limitMinutes = 20)), editedHere = true, server.now)
        assertEquals(20, changed.single().limitMinutes)
        assertEquals(server.now, changed.single().limitChangedAt)

        // A second change within the week is refused, and the server's limit stands.
        server.now += 3 * 86_400_000L
        var refused = false
        val again = phone.reconcileLimits(listOf(youtube.copy(limitMinutes = 60)), editedHere = true, server.now) { _, _ -> refused = true }
        assertTrue(refused)
        assertEquals(20, again.single().limitMinutes)

        // A week on, it goes through.
        server.now += 5 * 86_400_000L
        val later = phone.reconcileLimits(listOf(youtube.copy(limitMinutes = 60)), editedHere = true, server.now)
        assertEquals(60, later.single().limitMinutes)
    }

    @Test
    fun aLimitOnlyChangesWhenEditedHere() = runTest {
        val phone = device("phone")
        phone.reconcileLimits(listOf(youtube), editedHere = false, server.now)
        // Another device's stale copy says 45, but it wasn't edited there: the server's 30 stands.
        val other = device("tv").reconcileLimits(listOf(youtube.copy(limitMinutes = 45)), editedHere = false, server.now)
        assertEquals(30, other.single().limitMinutes)
        assertFalse(server.tree.isEmpty())
    }
}
