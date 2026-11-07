package dev.josemiguel.launcher

import android.content.Context
import android.util.Log
import dev.josemiguel.intentional.core.DayUsage
import dev.josemiguel.intentional.core.parseHistoryJson
import dev.josemiguel.intentional.core.toHistoryJson
import java.io.File

/**
 * The synced history on this device: each past day's usage summed across
 * every device, as [SyncRunner] last fetched it (once a day). Today isn't
 * in it; the history screen adds today's live numbers.
 */
object HistoryStore {

    private const val TAG = "HistoryStore"

    private fun file(context: Context) = File(context.filesDir, "history.json")

    @Synchronized
    fun load(context: Context): Map<String, DayUsage> {
        val f = file(context)
        if (!f.exists()) return emptyMap()
        return runCatching { parseHistoryJson(f.readText()) }
            .onFailure { Log.w(TAG, "unreadable ${f.path}", it) }
            .getOrDefault(emptyMap())
    }

    /** The day to fetch from: the last day held, which may have grown since, or [keepFrom] when nothing is. */
    fun fetchFrom(context: Context, keepFrom: String): String =
        load(context).keys.maxOrNull()?.takeIf { it >= keepFrom } ?: keepFrom

    /** Takes [fetched] over what's held and drops days before [keepFrom]. */
    @Synchronized
    fun merge(context: Context, fetched: Map<String, DayUsage>, keepFrom: String) {
        val merged = (load(context) + fetched).filterKeys { it >= keepFrom }
        val f = file(context)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(merged.toHistoryJson())
        tmp.renameTo(f)
    }
}
