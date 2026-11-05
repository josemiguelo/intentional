package dev.josemiguel.intentional.core.sync

import dev.josemiguel.intentional.core.AppGroup
import dev.josemiguel.intentional.core.DayUsage
import dev.josemiguel.intentional.core.parseGroups
import dev.josemiguel.intentional.core.toGroupsJson
import io.ktor.client.HttpClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * One budget across devices. Under /users/<uid>/ in the Realtime Database:
 *
 * - `usage/<day>/<device>`: each device's day, sealed. Each device writes
 *   only its own, so nothing ever conflicts; a group's use is the sum.
 * - `groups`: {blob, updatedAt}, sealed; the newest edit wins.
 * - `limits/<group id>`: {minutes, changedAt} in the clear, because the
 *   server's rules enforce the once-a-week change on them.
 *
 * Calls are plain suspend functions: callers run them off their main loop
 * and keep enforcing with the last known numbers when offline.
 */
class Sync(private val config: SyncConfig, http: HttpClient = defaultHttpClient()) {

    private val vault = Vault(config.key)
    private val firebase = Firebase(config, http)

    val device: String get() = config.device

    /** Publishes this device's [mine] for [day]; returns every other device's. */
    suspend fun exchangeUsage(day: String, mine: DayUsage, nowMs: Long): Map<String, DayUsage> {
        firebase.write("usage/$day/${config.device}", JsonPrimitive(vault.seal(mine.toJson())), nowMs)
        val all = firebase.read("usage/$day", nowMs)?.jsonObject ?: return emptyMap()
        return all.filterKeys { it != config.device }.mapNotNull { (device, sealed) ->
            // One device's unreadable entry (an old key) mustn't stop the rest.
            runCatching { device to DayUsage.parse(vault.open(sealed.jsonPrimitive.content)) }.getOrNull()
        }.toMap()
    }

    suspend fun groups(nowMs: Long): SyncedGroups? {
        val o = firebase.read("groups", nowMs)?.jsonObject ?: return null
        val blob = o["blob"]?.jsonPrimitive?.content ?: return null
        return SyncedGroups(parseGroups(vault.open(blob)), o["updatedAt"]?.jsonPrimitive?.long ?: 0L)
    }

    suspend fun putGroups(groups: List<AppGroup>, updatedAt: Long, nowMs: Long) {
        firebase.write(
            "groups",
            buildJsonObject {
                put("blob", vault.seal(groups.toGroupsJson()))
                put("updatedAt", updatedAt)
            },
            nowMs,
        )
    }

    /** The server's limits, by group id ([groupId]). */
    suspend fun limits(nowMs: Long): Map<String, Limit> {
        val o = firebase.read("limits", nowMs)?.jsonObject ?: return emptyMap()
        return o.mapNotNull { (id, v) ->
            runCatching {
                val l = v.jsonObject
                id to Limit(l["minutes"]!!.jsonPrimitive.int, l["changedAt"]!!.jsonPrimitive.long)
            }.getOrNull()
        }.toMap()
    }

    /**
     * Sets a group's limit. A change is stamped with the server's clock and
     * refused within a week of the last one (false). [carriedChangedAt]
     * instead carries over a limit set before sync, which the rules accept
     * only for a group the server doesn't know yet.
     */
    suspend fun putLimit(name: String, minutes: Int, nowMs: Long, carriedChangedAt: Long? = null): Boolean =
        firebase.write(
            "limits/${groupId(name)}",
            buildJsonObject {
                put("minutes", minutes)
                if (carriedChangedAt != null) put("changedAt", carriedChangedAt)
                else put("changedAt", buildJsonObject { put(".sv", "timestamp") })
            },
            nowMs,
        )

    fun groupId(name: String): String = vault.groupId(name)

    /**
     * This device's groups against the synced ones. [synced] is what this
     * device last agreed with the server (null before its first sync). The
     * first sync takes the server's groups if it has any (another device got
     * there first); after that a local edit (local differs from [synced])
     * goes up, and a newer edit from elsewhere comes down. The caller stores
     * [GroupsOutcome.groups] and [GroupsOutcome.synced].
     */
    suspend fun reconcileGroups(local: List<AppGroup>, synced: SyncedGroups?, nowMs: Long): GroupsOutcome {
        val remote = groups(nowMs)
        return when {
            synced == null && remote != null -> GroupsOutcome(remote.groups, remote, GroupsChange.ADOPTED)
            synced == null || local != synced.groups -> {
                putGroups(local, nowMs, nowMs)
                GroupsOutcome(local, SyncedGroups(local, nowMs), GroupsChange.PUSHED)
            }
            remote != null && remote.updatedAt > synced.updatedAt -> GroupsOutcome(remote.groups, remote, GroupsChange.ADOPTED)
            else -> GroupsOutcome(local, synced, GroupsChange.NONE)
        }
    }

    /**
     * Brings the server's limits to [groups] and returns them with those
     * limits (and their change dates). A group the server doesn't know yet
     * carries its limit over (the rules allow it once); when [editedHere], a
     * changed limit is asked of the server, which refuses within a week of
     * the last change ([onRefused]) and its limit stands.
     */
    suspend fun reconcileLimits(
        groups: List<AppGroup>,
        editedHere: Boolean,
        nowMs: Long,
        onRefused: (AppGroup, Limit) -> Unit = { _, _ -> },
    ): List<AppGroup> {
        val limits = limits(nowMs)
        for (g in groups) {
            val server = limits[groupId(g.name)]
            when {
                server == null -> putLimit(g.name, g.limitMinutes, nowMs, carriedChangedAt = g.limitChangedAt)
                editedHere && server.minutes != g.limitMinutes && !putLimit(g.name, g.limitMinutes, nowMs) ->
                    onRefused(g, server)
            }
        }
        return groups.withLimits(limits(nowMs), ::groupId)
    }
}

enum class GroupsChange { NONE, PUSHED, ADOPTED }

data class GroupsOutcome(val groups: List<AppGroup>, val synced: SyncedGroups, val change: GroupsChange)

@Serializable
data class Limit(val minutes: Int, val changedAt: Long)

data class SyncedGroups(val groups: List<AppGroup>, val updatedAt: Long)

/** The groups with the server's limits, which win over any local copy. */
fun List<AppGroup>.withLimits(limits: Map<String, Limit>, idOf: (String) -> String): List<AppGroup> = map { g ->
    limits[idOf(g.name)]?.let { g.copy(limitMinutes = it.minutes, limitChangedAt = it.changedAt) } ?: g
}
