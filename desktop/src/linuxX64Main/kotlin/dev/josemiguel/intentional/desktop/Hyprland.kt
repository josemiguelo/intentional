package dev.josemiguel.intentional.desktop

import kotlinx.serialization.json.Json
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

    /**
     * Asks the window to close, as SUPER+Q does. Hyprland 0.56+ takes a Lua
     * dispatcher expression (its Lua config); older ones the plain form.
     */
    fun closeWindow(address: String) {
        val lua = request("dispatch hl.dsp.window.close({ window = \"address:$address\" })")
        if (lua?.trim() == "ok") return
        val legacy = request("dispatch closewindow address:$address")
        if (legacy?.trim() != "ok") log("close $address failed: ${lua?.trim()} / ${legacy?.trim()}")
    }

    data class Window(val cls: String, val address: String)
}
