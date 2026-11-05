package dev.josemiguel.intentional.desktop

import dev.josemiguel.intentional.core.AppGroup
import dev.josemiguel.intentional.core.canChangeLimit
import dev.josemiguel.intentional.core.daysUntilChangeable
import dev.josemiguel.intentional.core.parseGroups
import dev.josemiguel.intentional.core.toGroupsJson
import dev.josemiguel.intentional.core.withApp
import dev.josemiguel.intentional.core.withGroup
import dev.josemiguel.intentional.core.withLimit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import platform.posix.exit

/**
 * `intentional apps` and `intentional group …`: the desktop's way to put its
 * own apps in groups (as "hypr:<window class>"), since the phone can't see
 * them. Edits land in groups.json; with sync on, the daemon pushes them
 * within a minute and every device takes them. `group add <group> --focused`
 * is meant for a keybinding: it adds whatever window has focus.
 */
object GroupsCli {

    fun apps() {
        val groups = load()
        val usage = loadUsage(date())
        val desktop = usage.apps.filterKeys { it.startsWith(PREFIX) }.toList().sortedByDescending { it.second }
        if (desktop.isEmpty()) return println("no desktop apps counted today")
        desktop.forEach { (app, ms) ->
            val group = groups.firstOrNull { app in it.packages }?.name?.let { " · $it" } ?: ""
            println("${app.removePrefix(PREFIX).padEnd(36)} ${(ms / 60_000).toString().padStart(4)}m$group")
        }
    }

    fun run(args: List<String>) {
        when (args.firstOrNull()) {
            null, "list" -> list()
            "new" -> edit { it.withGroup(arg(args, 1, "group name")) }
            "add" -> {
                val name = existing(arg(args, 1, "group"))
                val apps = if ("--focused" in args) {
                    listOf(Hyprland.activeWindow()?.cls ?: fail("no focused window"))
                } else args.drop(2).ifEmpty { fail("which app? a window class (see `intentional apps`) or --focused") }
                edit { groups -> apps.fold(groups) { g, app -> g.withApp(appId(app), name) } }
                apps.forEach { notify("${appId(it).removePrefix(PREFIX)} · added to $name") }
            }
            "remove" -> {
                val name = existing(arg(args, 1, "group"))
                val app = appId(arg(args, 2, "app"))
                edit { groups -> groups.map { if (it.name == name) it.copy(packages = it.packages - app) else it } }
            }
            "limit" -> {
                val name = existing(arg(args, 1, "group"))
                val minutes = arg(args, 2, "minutes").toIntOrNull() ?: fail("minutes must be a number")
                val group = load().single { it.name == name }
                val now = epochMs()
                if (!group.canChangeLimit(now)) fail("$name's limit is locked for ${group.daysUntilChangeable(now)} more day(s)")
                edit { it.withLimit(name, minutes, now) }
                if (readText(Paths.syncConfig) != null) println("(sync on: the server has the final word, within a minute)")
            }
            else -> fail("usage: intentional group [list | new <name> | add <group> <class…|--focused> | remove <group> <app> | limit <group> <minutes>]")
        }
    }

    private fun list() {
        val groups = load()
        if (groups.isEmpty()) return println("no groups in ${Paths.groups}")
        groups.forEach { g ->
            println("${g.name} · ${g.limitMinutes}m")
            g.packages.sorted().forEach { println("  ${if (it.startsWith(PREFIX)) "desktop  ${it.removePrefix(PREFIX)}" else "android  $it"}") }
            g.sites.sorted().forEach { println("  site     $it") }
        }
    }

    private fun load(): List<AppGroup> = readText(Paths.groups)?.let(::parseGroups).orEmpty()

    private fun edit(change: (List<AppGroup>) -> List<AppGroup>) {
        val before = load()
        val after = change(before)
        if (after == before) return println("nothing to change")
        writeText(Paths.groups, Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(after.toGroupsJson())))
        list()
    }

    private fun existing(name: String): String =
        load().firstOrNull { it.name.equals(name, ignoreCase = true) }?.name ?: fail("no group \"$name\" (intentional group list)")

    private fun arg(args: List<String>, i: Int, what: String): String = args.getOrNull(i) ?: fail("missing $what")

    private fun appId(app: String) = if (app.startsWith(PREFIX)) app else "$PREFIX$app"

    private fun notify(text: String) = spawn("notify-send", "-a", "intentional", "intentional", text)

    private fun fail(message: String): Nothing {
        println(message)
        exit(1)
        error(message)
    }

    private const val PREFIX = "hypr:"
}
