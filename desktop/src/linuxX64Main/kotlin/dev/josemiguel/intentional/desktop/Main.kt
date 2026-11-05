package dev.josemiguel.intentional.desktop

import dev.josemiguel.intentional.core.DayUsage
import dev.josemiguel.intentional.core.parseGroups
import dev.josemiguel.intentional.core.usedMs

const val VERSION = "0.4.0"

/**
 * `intentional` runs the daemon; `intentional status` prints today's budget
 * use per group; `intentional apps` and `intentional group …` put desktop
 * apps in groups (GroupsCli); `intentional --version` its version. Brave runs it as the native host, passing the extension's
 * origin ("chrome-extension://…/").
 */
fun main(args: Array<String>) {
    when {
        args.any { it.startsWith("chrome-extension://") } -> NativeHost.run()
        args.firstOrNull() == "status" -> status()
        args.firstOrNull() == "apps" -> GroupsCli.apps()
        args.firstOrNull() == "pause" && args.size == 2 -> PauseUi.run(args[1])
        args.firstOrNull() == "group" -> GroupsCli.run(args.drop(1))
        args.firstOrNull() in setOf("consider", "unconsider", "considered") -> Considered.run(args.first(), args.drop(1))
        args.firstOrNull() == "--version" -> println("intentional $VERSION")
        args.isEmpty() || args.first() == "daemon" -> Daemon().run()
        else -> println("usage: intentional [daemon | status | apps | group … | consider … | unconsider … | considered | --version]")
    }
}

/**
 * As of the daemon's last save (every 10 seconds), plus the other devices'
 * time as of its last sync. With sync, groups.json's limits are the
 * server's.
 */
private fun status() {
    val groups = readText(Paths.groups)?.let(::parseGroups).orEmpty()
    if (groups.isEmpty()) return println("no groups in ${Paths.groups}")
    val day = date()
    val others = readText(Paths.remoteUsage(day))?.let { runCatching { DayUsage.parse(it) }.getOrNull() }
    val usage = loadUsage(day).let { if (others != null) it + others else it }
    if (others != null) println("(all devices)")
    groups.forEach { g ->
        val used = g.usedMs(usage) / 60_000
        val mark = if (used >= g.limitMinutes) "  · used up" else ""
        println("${g.name.padEnd(12)} ${used}m / ${g.limitMinutes}m$mark")
    }
}
