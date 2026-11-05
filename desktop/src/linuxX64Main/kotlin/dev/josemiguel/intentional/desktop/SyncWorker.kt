package dev.josemiguel.intentional.desktop

import dev.josemiguel.intentional.core.AppGroup
import dev.josemiguel.intentional.core.DayUsage
import dev.josemiguel.intentional.core.Settings
import dev.josemiguel.intentional.core.parseGroups
import dev.josemiguel.intentional.core.sync.GroupsChange
import dev.josemiguel.intentional.core.sync.Sync
import dev.josemiguel.intentional.core.sync.SyncConfig
import dev.josemiguel.intentional.core.sync.SyncedConsidered
import dev.josemiguel.intentional.core.sync.SyncedGroups
import dev.josemiguel.intentional.core.sync.SyncedSettings
import dev.josemiguel.intentional.core.sync.firstKeptDay
import dev.josemiguel.intentional.core.sync.parseConsideredJson
import dev.josemiguel.intentional.core.sync.toConsideredJson
import dev.josemiguel.intentional.core.toGroupsJson
import kotlin.concurrent.AtomicReference
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * The desktop's half of sync, on its own thread so the network never stalls
 * the focus loop. Every [INTERVAL_MS] it reconciles groups.json with the
 * synced groups and the server's limits (both written back into the file,
 * which the daemon re-reads) and considered.json with the synced considered
 * apps, publishes this machine's group time and
 * fetches the other devices' ([latest]). Offline, the daemon keeps the last
 * numbers and the next cycle catches up.
 */
class SyncWorker(private val config: SyncConfig) {

    /** What the other devices have spent today, as of the last good cycle. */
    data class Remote(val day: String, val others: DayUsage)

    private val outbox = AtomicReference<Pair<String, DayUsage>?>(null)

    /** The day old usage was last pruned (here and in Firebase): once a day is plenty. */
    private var prunedOn: String? = null
    private val inbox = AtomicReference<Remote?>(null)

    /** The daemon's day so far (only group time leaves the machine). */
    fun publish(day: String, mine: DayUsage) {
        outbox.value = day to mine
    }

    fun latest(): Remote? = inbox.value

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun start() {
        GlobalScope.launch(newSingleThreadContext("sync")) {
            val sync = Sync(config)
            var lastError: String? = null
            while (true) {
                try {
                    cycle(sync)
                    if (lastError != null) log("sync: back")
                    lastError = null
                } catch (e: Throwable) {
                    val message = e.message ?: e.toString()
                    if (message != lastError) log("sync: $message")
                    lastError = message
                }
                delay(INTERVAL_MS)
            }
        }
    }

    private suspend fun cycle(sync: Sync) {
        val now = epochMs()
        val local = readText(Paths.groups)?.let { runCatching { parseGroups(it) }.getOrNull() }.orEmpty()
        val synced = readText(Paths.groupsSynced)?.let { raw ->
            runCatching { json.decodeFromString(Record.serializer(), raw) }.getOrNull()
                ?.let { SyncedGroups(parseGroups(it.groups), it.updatedAt) }
        }
        val outcome = sync.reconcileGroups(local, synced, now)
        when (outcome.change) {
            GroupsChange.PUSHED -> log("sync: pushed this machine's groups")
            GroupsChange.ADOPTED -> log("sync: groups updated from another device")
            GroupsChange.NONE -> Unit
        }
        val limited = sync.reconcileLimits(outcome.groups, outcome.change == GroupsChange.PUSHED, now) { g, server ->
            log("sync: ${g.name}'s limit stays ${server.minutes}m (changed less than a week ago)")
        }
        // The file and the record move together, so the server's limits
        // landing here never read as a local edit.
        if (limited != local) writeText(Paths.groups, pretty(limited))
        record(SyncedGroups(limited, outcome.synced.updatedAt))

        val consideredHere = Considered.load()
        val syncedConsidered = readText(Paths.consideredSynced)?.let { raw ->
            runCatching { json.decodeFromString(ConsideredRecord.serializer(), raw) }.getOrNull()
                ?.let { SyncedConsidered(parseConsideredJson(it.apps), it.updatedAt) }
        }
        val considered = sync.reconcileConsidered(consideredHere, syncedConsidered, now)
        if (considered.change == GroupsChange.ADOPTED) log("sync: considered apps updated from another device")
        if (considered.apps != consideredHere) Considered.save(considered.apps)
        writeText(
            Paths.consideredSynced,
            json.encodeToString(ConsideredRecord.serializer(), ConsideredRecord(considered.synced.updatedAt, considered.synced.apps.toConsideredJson())),
        )

        // Absent until another device has shared some: then this one takes them.
        val settingsHere = readText(Paths.settings)?.let { runCatching { Settings.parse(it) }.getOrNull() }
        val syncedSettings = readText(Paths.settingsSynced)?.let { raw ->
            runCatching { json.decodeFromString(SettingsRecord.serializer(), raw) }.getOrNull()
                ?.let { SyncedSettings(Settings.parse(it.settings), it.updatedAt) }
        }
        sync.reconcileSettings(settingsHere, syncedSettings, now)?.let { outcome ->
            if (outcome.change == GroupsChange.ADOPTED) log("sync: settings updated from another device")
            if (outcome.settings != settingsHere) writeText(Paths.settings, outcome.settings.toJson())
            writeText(
                Paths.settingsSynced,
                json.encodeToString(SettingsRecord.serializer(), SettingsRecord(outcome.synced.updatedAt, outcome.synced.settings.toJson())),
            )
        }

        val (day, mine) = outbox.value ?: return
        if (prunedOn != day) {
            val keepFrom = firstKeptDay(day)
            val dropped = sync.pruneUsage(keepFrom, now)
            val local = pruneLocalUsage(keepFrom)
            if (dropped + local > 0) log("sync: pruned usage before $keepFrom ($dropped synced, $local local)")
            prunedOn = day
        }
        val others = sync.exchangeUsage(day, mine, now).values.fold(DayUsage()) { a, b -> a + b }
        writeText(Paths.remoteUsage(day), others.toJson())
        inbox.value = Remote(day, others)
    }

    private fun record(synced: SyncedGroups) = writeText(
        Paths.groupsSynced,
        json.encodeToString(Record.serializer(), Record(synced.updatedAt, synced.groups.toGroupsJson())),
    )

    @Serializable
    private data class Record(val updatedAt: Long, val groups: String)

    @Serializable
    private data class ConsideredRecord(val updatedAt: Long, val apps: String)

    @Serializable
    private data class SettingsRecord(val updatedAt: Long, val settings: String)

    private companion object {
        const val INTERVAL_MS = 60_000L
        val json = Json { ignoreUnknownKeys = true }

        fun pretty(groups: List<AppGroup>): String =
            Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(groups.toGroupsJson()))
    }
}
