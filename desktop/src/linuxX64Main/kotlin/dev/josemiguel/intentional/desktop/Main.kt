package dev.josemiguel.intentional.desktop

import dev.josemiguel.intentional.core.parseGroups
import dev.josemiguel.intentional.core.usedMs

const val VERSION = "0.1.0"

/**
 * `intentional` runs the daemon; `intentional status` prints today's budget
 * use per group; `intentional --version` its version. Brave runs it as the native host, passing the extension's
 * origin ("chrome-extension://…/").
 */
fun main(args: Array<String>) {
    when {
        args.any { it.startsWith("chrome-extension://") } -> NativeHost.run()
        args.firstOrNull() == "status" -> status()
        args.firstOrNull() == "--version" -> println("intentional $VERSION")
        args.isEmpty() || args.first() == "daemon" -> Daemon().run()
        else -> println("usage: intentional [daemon | status | --version]")
    }
}

/** As of the daemon's last save (every 10 seconds). */
private fun status() {
    val groups = readText(Paths.groups)?.let(::parseGroups).orEmpty()
    if (groups.isEmpty()) return println("no groups in ${Paths.groups}")
    val usage = loadUsage(date())
    groups.forEach { g ->
        val used = g.usedMs(usage) / 60_000
        val mark = if (used >= g.limitMinutes) "  · used up" else ""
        println("${g.name.padEnd(12)} ${used}m / ${g.limitMinutes}m$mark")
    }
}
