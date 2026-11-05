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

    @Volatile
    private var others: Pair<String, DayUsage>? = null

    private var thread: HandlerThread? = null
    private var lastError: String? = null

    /** One cycle at a time, even across a stop/start (a guard rebind) mid-cycle. */
    private val cycleLock = Any()

    /** What the other devices have spent today, as of the last good sync. */
    fun othersToday(): DayUsage? = others?.takeIf { it.first == LocalDate.now().toString() }?.second

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
                if (app.getSystemService(PowerManager::class.java).isInteractive) cycle(app, sync)
                handler.postDelayed(this, INTERVAL_MS)
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

    private fun cycle(context: Context, sync: Sync) = synchronized(cycleLock) { runCycle(context, sync) }

    private fun runCycle(context: Context, sync: Sync) = runBlocking {
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

            val packages = limited.flatMap { it.packages }.toSet()
            val mine = DayUsage(
                apps = if (UsageStatsRepository.hasPermission(context)) {
                    UsageStatsRepository.foregroundMsToday(context, packages)
                } else emptyMap(),
                sites = prefs.sitesToday(),
            ).onlyFor(limited)
            val day = LocalDate.now().toString()
            others = day to sync.exchangeUsage(day, mine, now).values.fold(DayUsage()) { a, b -> a + b }
            if (lastError != null) Log.i(TAG, "back")
            lastError = null
        } catch (e: Exception) {
            val message = e.message ?: e.toString()
            if (message != lastError) Log.w(TAG, "sync failed: $message")
            lastError = message
        }
    }
}
