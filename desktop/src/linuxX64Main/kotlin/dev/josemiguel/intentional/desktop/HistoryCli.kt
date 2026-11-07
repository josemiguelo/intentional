package dev.josemiguel.intentional.desktop

import dev.josemiguel.intentional.core.DayUsage
import dev.josemiguel.intentional.core.GroupMinutes
import dev.josemiguel.intentional.core.formatMinutes
import dev.josemiguel.intentional.core.history
import dev.josemiguel.intentional.core.parseGroups
import dev.josemiguel.intentional.core.sync.Sync
import dev.josemiguel.intentional.core.sync.SyncConfig
import dev.josemiguel.intentional.core.sync.firstKeptDay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

/**
 * `intentional history [days]`: each group's time a day across every
 * device, [days] (14) back one by one and the weeks before in sums. With
 * sync it reads the whole kept year from the server; without, or offline,
 * this machine's own day files (and the other devices' time as of each
 * day's last sync here).
 */
object HistoryCli {

    fun run(args: List<String>) {
        val days = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 365) ?: 14
        val groups = readText(Paths.groups)?.let(::parseGroups).orEmpty()
        if (groups.isEmpty()) return println("no groups in ${Paths.groups}")
        val today = date()
        val (byDay, source) = synced(today) ?: (local() to "this machine's records")
        // Today as the daemon has it now: the server's copy can be a minute old.
        val now = byDay + (today to (localDay(today).atLeast(byDay[today] ?: DayUsage())))
        val h = history(now, groups, LocalDate.parse(today), days = days, weeks = 52)
        println("($source)")
        h.days.forEach { d ->
            println("${d.day.dayOfWeek.name.take(3).lowercase()} ${d.day}  ${line(d.groups)}")
            println("               ${d.opened} intentional · ${d.closed} paused-and-closed")
        }
        if (h.weeks.isNotEmpty()) {
            println()
            h.weeks.forEach { w ->
                val partial = if (w.days < 7) "  (${w.days} of 7 days)" else ""
                println("week of ${w.start}  ${line(w.groups)}$partial")
            }
        }
    }

    private fun line(groups: List<GroupMinutes>) =
        if (groups.isEmpty()) "—"
        else groups.joinToString("  ") { "${it.group} ${formatMinutes(it.minutes)}${if (it.over) "!" else ""}" }

    /** Every device's days from the server, summed; null without sync or when it can't be reached. */
    private fun synced(today: String): Pair<Map<String, DayUsage>, String>? {
        val config = readText(Paths.syncConfig)?.let { runCatching { SyncConfig.parse(it) }.getOrNull() } ?: return null
        return runCatching {
            runBlocking { Sync(config).usageSince(firstKeptDay(today), epochMs()) }
                .mapValues { (_, devices) -> devices.values.fold(DayUsage()) { a, b -> a + b } }
        }.fold(
            onSuccess = { it to "all devices; ! is over today's limit" },
            onFailure = { println("(couldn't reach sync: ${it.message})"); null },
        )
    }

    private fun local(): Map<String, DayUsage> =
        listDir(Paths.usageDir).filter { it.endsWith(".json") && !it.endsWith(".remote.json") }
            .map { it.removeSuffix(".json") }
            .associateWith(::localDay)

    /** This machine's day plus the other devices' as of its last sync that day. */
    private fun localDay(day: String): DayUsage {
        val remote = readText(Paths.remoteUsage(day))?.let { runCatching { DayUsage.parse(it) }.getOrNull() }
        return loadUsage(day).let { if (remote != null) it + remote else it }
    }
}
