package dev.josemiguel.intentional.desktop

import dev.josemiguel.intentional.core.Friction
import dev.josemiguel.intentional.core.sync.parseConsideredJson
import dev.josemiguel.intentional.core.sync.toConsideredJson
import platform.posix.exit

/**
 * The considered apps: they open through a pause (PauseUi), each at its rung
 * of the friction ladder. One list for every device (sync shares it); here
 * in considered.json, app id to Friction ordinal, desktop apps as
 * "hypr:<window class>". `intentional consider` edits it.
 */
object Considered {

    fun load(): Map<String, Int> =
        readText(Paths.considered)?.let { runCatching { parseConsideredJson(it) }.getOrNull() }.orEmpty()

    fun save(apps: Map<String, Int>) = writeText(Paths.considered, apps.toConsideredJson())

    fun frictionOf(app: String): Friction? = load()[app]?.let(Friction::of)

    /** `intentional consider [<class>|--focused] [pause|long|type|night]`, `unconsider`, `considered`. */
    fun run(command: String, args: List<String>) {
        when (command) {
            "considered" -> list()
            "consider" -> {
                val cls = if ("--focused" in args) Hyprland.activeWindow()?.cls ?: fail("no focused window")
                else args.firstOrNull { !it.startsWith("--") && it !in RUNGS } ?: fail(USAGE)
                val rung = args.firstOrNull { it in RUNGS }?.let { RUNGS.getValue(it) } ?: Friction.PAUSE
                save(load() + (appId(cls) to rung.ordinal))
                spawn("notify-send", "-a", "intentional", "intentional", "${cls.removePrefix(PREFIX)} · opens through a ${rung.label}")
                list()
            }
            "unconsider" -> {
                val app = appId(args.firstOrNull() ?: fail(USAGE))
                val apps = load()
                if (app !in apps) fail("${app.removePrefix(PREFIX)} isn't considered")
                save(apps - app)
                list()
            }
        }
    }

    private fun list() {
        val apps = load()
        if (apps.isEmpty()) return println("no considered apps")
        apps.toList().sortedBy { it.first }.forEach { (app, f) ->
            val where = if (app.startsWith(PREFIX)) "desktop  ${app.removePrefix(PREFIX)}" else "android  $app"
            println("${where.padEnd(44)} ${Friction.of(f).label}")
        }
    }

    private fun appId(app: String) = if (app.startsWith(PREFIX)) app else "$PREFIX$app"

    private fun fail(message: String): Nothing {
        println(message)
        exit(1)
        error(message)
    }

    private const val PREFIX = "hypr:"
    private const val USAGE = "usage: intentional consider <class>|--focused [pause|long|type|night] · intentional unconsider <class>"
    private val RUNGS = mapOf(
        "pause" to Friction.PAUSE, "long" to Friction.LONG_PAUSE,
        "type" to Friction.TYPE_NAME, "night" to Friction.NIGHT_LOCK,
    )
}
