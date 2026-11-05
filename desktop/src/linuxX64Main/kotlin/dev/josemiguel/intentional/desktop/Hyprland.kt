package dev.josemiguel.intentional.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Hyprland's two sockets: socket2 streams events ("activewindow>>class,title"
 * and friends), the other takes one request per connection ("j/activewindow",
 * "dispatch closewindow address:0x…") and answers it.
 */
object Hyprland {

    private val dir: String
        get() {
            val runtime = env("XDG_RUNTIME_DIR") ?: error("XDG_RUNTIME_DIR unset")
            val sig = env("HYPRLAND_INSTANCE_SIGNATURE") ?: error("HYPRLAND_INSTANCE_SIGNATURE unset: not in a Hyprland session")
            return "$runtime/hypr/$sig"
        }

    /** The event stream, or -1. */
    fun connectEvents(): Int = connectUnix("$dir/.socket2.sock")

    fun request(command: String): String? {
        val fd = connectUnix("$dir/.socket.sock")
        if (fd < 0) return null
        writeAll(fd, command.encodeToByteArray())
        val out = StringBuilder()
        val buf = ByteArray(4096)
        while (true) {
            val n = readInto(fd, buf)
            if (n <= 0) break
            out.append(buf.decodeToString(0, n))
        }
        platform.posix.close(fd)
        return out.toString()
    }

    /** The focused window at startup, before any event says so. */
    fun activeWindow(): Window? {
        val raw = request("j/activewindow") ?: return null
        val o = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val cls = o["class"]?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() } ?: return null
        val address = o["address"]?.jsonPrimitive?.content ?: return null
        return Window(cls, address)
    }

    /** Asks the window to close, as SUPER+Q does. */
    fun closeWindow(address: String) = dispatch(
        "hl.dsp.window.close({ window = \"address:$address\" })",
        "closewindow address:$address",
    )

    /** Out of sight, onto a special workspace nothing shows, without following it. */
    fun hide(address: String) = dispatch(
        "hl.dsp.window.move({ workspace = \"special:$HIDDEN\", follow = false, window = \"address:$address\" })",
        "movetoworkspacesilent special:$HIDDEN,address:$address",
    )

    /** Back onto [workspace], following it there, and focused. */
    fun restore(address: String, workspace: String) {
        dispatch(
            "hl.dsp.window.move({ workspace = \"$workspace\", follow = true, window = \"address:$address\" })",
            "movetoworkspace $workspace,address:$address",
        )
        dispatch("hl.dsp.focus({ window = \"address:$address\" })", "focuswindow address:$address")
    }

    /**
     * Hyprland 0.56+ takes a Lua dispatcher expression (its Lua config);
     * older ones the plain form.
     */
    private fun dispatch(lua: String, legacy: String) {
        val first = request("dispatch $lua")
        if (first?.trim() == "ok") return
        val second = request("dispatch $legacy")
        if (second?.trim() != "ok") log("dispatch failed: ${first?.trim()} / ${second?.trim()}")
    }

    private const val HIDDEN = "intentional"

    /** Whether a session lock (Omarchy's lock screen) is up; see [monitorsLocked]. */
    fun isLocked(): Boolean = request("j/monitors")?.let(::monitorsLocked) ?: false

    data class Window(val cls: String, val address: String)
}

/**
 * Reads a lock from `j/monitors`. Hyprland reports no lock state as such, but
 * an active ext-session-lock is one reason a monitor can't go solitary:
 * "LOCK" in its solitaryBlockedBy (Omarchy's omarchy-hyprland-session-locked
 * reads the same).
 */
fun monitorsLocked(json: String): Boolean {
    val monitors = runCatching { Json.parseToJsonElement(json).jsonArray }.getOrNull() ?: return false
    return monitors.any { m ->
        val blockers = runCatching { m.jsonObject["solitaryBlockedBy"]?.jsonArray }.getOrNull() ?: return@any false
        blockers.any { runCatching { it.jsonPrimitive.content }.getOrNull() == "LOCK" }
    }
}
