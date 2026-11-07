package dev.josemiguel.launcher

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.util.Log
import dev.josemiguel.intentional.core.DayUsage
import dev.josemiguel.intentional.core.sync.GroupsChange
import dev.josemiguel.intentional.core.sync.Sync
import dev.josemiguel.intentional.core.sync.SyncConfig
import dev.josemiguel.intentional.core.sync.firstKeptDay
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking

/**
 * The phone's (and TV's) half of sync, run by the guard on its own thread
 * every [INTERVAL_MS] while the screen is on: groups and their limits, and
 * the considered apps, are reconciled with the server (the result lands in
 * [Prefs], which everything already reads), this device's group time goes up and the other
 * devices' comes down ([othersToday]), which [UsageStatsRepository]'s budget
 * use adds. Needs files/sync.json (a SyncConfig, dropped in over adb: see
 * [DropIn]); without it, nothing runs. Offline, the last numbers stand.
 */
object SyncRunner {

    private const val TAG = "SyncRunner"
    private const val INTERVAL_MS = 60_000L

    /**
     * After a failed cycle: soon, since right after a TV is switched on the
     * network isn't up yet, and until a cycle succeeds the other devices'
     * time is missing from every budget.
     */
    private const val RETRY_MS = 10_000L

    @Volatile
    private var others: Pair<String, DayUsage>? = null

    /** This device's day as last published, which may hold time its own records lost. */
    @Volatile
    private var mine: Pair<String, DayUsage>? = null

    /** Every successful cycle, for screens that show the synced numbers. */
    val synced = kotlinx.coroutines.flow.MutableStateFlow(0)

    private var thread: HandlerThread? = null
    private var lastError: String? = null

    /** The day old usage was last pruned (here and in Firebase). */
    private var prunedOn: String? = null

    /** One cycle at a time, even across a stop/start (a guard rebind) mid-cycle. */
    private val cycleLock = Any()

    /** What the other devices have spent today, as of the last good sync. */
    fun othersToday(): DayUsage? = others?.takeIf { it.first == LocalDate.now().toString() }?.second

    /** What this device last published for today ([dev.josemiguel.intentional.core.sync.Sync.exchangeUsage]). */
    fun mineToday(): DayUsage? = mine?.takeIf { it.first == LocalDate.now().toString() }?.second

    @Synchronized
    fun start(context: Context) {
        if (thread != null) return
        val app = context.applicationContext
        val config = configFile(app).takeIf { it.exists() }?.let { file ->
            runCatching { SyncConfig.parse(file.readText()) }
                .onFailure { Log.w(TAG, "unreadable ${file.path}", it) }
                .getOrNull()
        } ?: return
        val sync = Sync(config)
        val t = HandlerThread("sync").apply { start() }
        thread = t
        val handler = Handler(t.looper)
        handler.post(object : Runnable {
            override fun run() {
                val ok = !app.getSystemService(PowerManager::class.java).isInteractive || cycle(app, sync)
                handler.postDelayed(this, if (ok) INTERVAL_MS else RETRY_MS)
            }
        })
        Log.i(TAG, "sync on as ${config.device}")
    }

    @Synchronized
    fun stop() {
        thread?.quitSafely()
        thread = null
    }

    fun configFile(context: Context) = File(context.filesDir, "sync.json")

    /** One cycle; false when it failed. */
    private fun cycle(context: Context, sync: Sync): Boolean = synchronized(cycleLock) { runCycle(context, sync) }

    private fun runCycle(context: Context, sync: Sync): Boolean = runBlocking {
        try {
            val prefs = Prefs(context)
            val now = System.currentTimeMillis()
            val local = prefs.groups
            val outcome = sync.reconcileGroups(local, prefs.groupsSynced, now)
            if (outcome.change != GroupsChange.NONE) Log.i(TAG, "groups ${outcome.change.name.lowercase()}")
            val limited = sync.reconcileLimits(outcome.groups, outcome.change == GroupsChange.PUSHED, now) { g, server ->
                Log.i(TAG, "${g.name}'s limit stays ${server.minutes}m (changed less than a week ago)")
            }
            // The groups and their record move together, so the server's
            // limits landing here never read as an edit made here.
            if (limited != local) prefs.groups = limited
            prefs.groupsSynced = outcome.synced.copy(groups = limited)

            val consideredHere = prefs.consideredWithFriction
            val considered = sync.reconcileConsidered(consideredHere, prefs.consideredSynced, now)
            if (considered.change != GroupsChange.NONE) Log.i(TAG, "considered ${considered.change.name.lowercase()}")
            if (considered.apps != consideredHere) prefs.consideredWithFriction = considered.apps
            prefs.consideredSynced = considered.synced

            val settingsHere = prefs.sharedSettings
            sync.reconcileSettings(settingsHere, prefs.settingsSynced, now)?.let { settings ->
                if (settings.change != GroupsChange.NONE) Log.i(TAG, "settings ${settings.change.name.lowercase()}")
                if (settings.settings != settingsHere) prefs.sharedSettings = settings.settings
                prefs.settingsSynced = settings.synced
            }

            val packages = limited.flatMap { it.packages }.toSet()
            val (opened, closed) = prefs.countsToday()
            val here = DayUsage(
                apps = if (UsageStatsRepository.hasPermission(context)) {
                    UsageStatsRepository.foregroundMsToday(context, packages)
                } else emptyMap(),
                sites = prefs.sitesToday(),
                opened = opened,
                closed = closed,
            ).onlyFor(limited)
            val day = LocalDate.now().toString()
            if (prunedOn != day) {
                val keepFrom = firstKeptDay(day)
                val dropped = sync.pruneUsage(keepFrom, now) + prefs.pruneDailyTime(keepFrom)
                if (dropped > 0) Log.i(TAG, "pruned $dropped usage entries before $keepFrom")
                prunedOn = day
            }
            val exchange = sync.exchangeUsage(day, here, now)
            mine = day to exchange.mine
            others = day to exchange.others.values.fold(DayUsage()) { a, b -> a + b }
            synced.value++
            if (lastError != null) Log.i(TAG, "back")
            lastError = null
            true
        } catch (e: Exception) {
            val message = e.message ?: e.toString()
            if (message != lastError) Log.w(TAG, "sync failed: $message")
            lastError = message
            false
        }
    }
}
