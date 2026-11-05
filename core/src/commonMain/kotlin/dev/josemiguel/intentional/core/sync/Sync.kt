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
 * - `considered`: {blob, updatedAt}, sealed: the apps that open through a
 *   pause, each with its friction (an ordinal of Friction); likewise.
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

    suspend fun groups(nowMs: Long): SyncedGroups? =
        readSealed("groups", nowMs)?.let { (raw, at) -> SyncedGroups(parseGroups(raw), at) }

    suspend fun putGroups(groups: List<AppGroup>, updatedAt: Long, nowMs: Long) =
        writeSealed("groups", groups.toGroupsJson(), updatedAt, nowMs)

    /** A {blob, updatedAt} node, opened: the plain text and its time. */
    private suspend fun readSealed(path: String, nowMs: Long): Pair<String, Long>? {
        val o = firebase.read(path, nowMs)?.jsonObject ?: return null
        val blob = o["blob"]?.jsonPrimitive?.content ?: return null
        return vault.open(blob) to (o["updatedAt"]?.jsonPrimitive?.long ?: 0L)
    }

    private suspend fun writeSealed(path: String, plain: String, updatedAt: Long, nowMs: Long) {
        firebase.write(
            path,
            buildJsonObject {
                put("blob", vault.seal(plain))
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
     * device last agreed with the server (null before its first sync). A
     * first sync merges ([mergeGroups]: each device knows its own apps, so
     * neither side's may be lost) and pushes the merge if it adds anything;
     * after that a local edit (local differs from [synced]) goes up, and a
     * newer edit from elsewhere comes down. The caller stores
     * [GroupsOutcome.groups] and [GroupsOutcome.synced].
     */
    suspend fun reconcileGroups(local: List<AppGroup>, synced: SyncedGroups?, nowMs: Long): GroupsOutcome {
        val (value, record, change) = reconcile(
            local, synced?.let { it.groups to it.updatedAt }, groups(nowMs)?.let { it.groups to it.updatedAt },
            merge = ::mergeGroups,
        ) { putGroups(it, nowMs, nowMs); nowMs }
        return GroupsOutcome(value, SyncedGroups(record.first, record.second), change)
    }

    /**
     * The considered apps (app id to Friction ordinal) against the synced
     * ones, exactly as [reconcileGroups]: a first sync merges (each device
     * knows its own apps; the synced friction wins for an app both have),
     * then the newest edit wins.
     */
    suspend fun reconcileConsidered(
        local: Map<String, Int>,
        synced: SyncedConsidered?,
        nowMs: Long,
    ): ConsideredOutcome {
        val remote = readSealed("considered", nowMs)?.let { (raw, at) -> parseConsidered(raw) to at }
        val (value, record, change) = reconcile(
            local, synced?.let { it.apps to it.updatedAt }, remote,
            merge = { base, other -> other + base },
        ) { writeSealed("considered", considered.encodeToString(it), nowMs, nowMs); nowMs }
        return ConsideredOutcome(value, SyncedConsidered(record.first, record.second), change)
    }

    /**
     * The one rule for a sealed, newest-edit-wins value: [synced] is what
     * this device last agreed with the server (null before its first sync),
     * [remote] what the server has. A first sync merges and pushes the merge
     * if it adds anything; after that a local edit (local differs from
     * [synced]) goes up ([push], which returns its time), and a newer edit
     * from elsewhere comes down.
     */
    private suspend fun <T> reconcile(
        local: T,
        synced: Pair<T, Long>?,
        remote: Pair<T, Long>?,
        merge: (base: T, other: T) -> T,
        push: suspend (T) -> Long,
    ): Triple<T, Pair<T, Long>, GroupsChange> = when {
        synced == null && remote != null -> {
            val merged = merge(remote.first, local)
            if (merged == remote.first) Triple(merged, remote, GroupsChange.ADOPTED)
            else Triple(merged, merged to push(merged), GroupsChange.PUSHED)
        }
        synced == null || local != synced.first -> Triple(local, local to push(local), GroupsChange.PUSHED)
        remote != null && remote.second > synced.second -> Triple(remote.first, remote, GroupsChange.ADOPTED)
        else -> Triple(local, synced, GroupsChange.NONE)
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

/**
 * Two devices' groups, joined the first time one meets the other: a group
 * either has keeps its apps and sites from both (the [base] side's limit
 * and order first), and a group only one has is kept.
 */
fun mergeGroups(base: List<AppGroup>, other: List<AppGroup>): List<AppGroup> {
    val byName = other.associateBy { it.name }
    val merged = base.map { g ->
        val o = byName[g.name] ?: return@map g
        g.copy(packages = g.packages + o.packages, sites = g.sites + o.sites)
    }
    return merged + other.filter { o -> base.none { it.name == o.name } }
}

data class GroupsOutcome(val groups: List<AppGroup>, val synced: SyncedGroups, val change: GroupsChange)

/** Considered apps (app id to Friction ordinal) as last agreed with the server. */
data class SyncedConsidered(val apps: Map<String, Int>, val updatedAt: Long)

data class ConsideredOutcome(val apps: Map<String, Int>, val synced: SyncedConsidered, val change: GroupsChange)

private val considered = kotlinx.serialization.json.Json

private fun parseConsidered(raw: String): Map<String, Int> = considered.decodeFromString(raw)

/** Considered apps as JSON, for a device's own record of what it last synced. */
fun Map<String, Int>.toConsideredJson(): String = considered.encodeToString(this)

fun parseConsideredJson(raw: String): Map<String, Int> = parseConsidered(raw)

@Serializable
data class Limit(val minutes: Int, val changedAt: Long)

data class SyncedGroups(val groups: List<AppGroup>, val updatedAt: Long)

/** The groups with the server's limits, which win over any local copy. */
fun List<AppGroup>.withLimits(limits: Map<String, Limit>, idOf: (String) -> String): List<AppGroup> = map { g ->
    limits[idOf(g.name)]?.let { g.copy(limitMinutes = it.minutes, limitChangedAt = it.changedAt) } ?: g
}
