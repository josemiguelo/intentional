package dev.josemiguel.intentional.desktop

import dev.josemiguel.intentional.core.Friction
import dev.josemiguel.intentional.core.Settings
import dev.josemiguel.intentional.core.WindDown
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toKString
import platform.posix.ECHO
import platform.posix.ICANON
import platform.posix.STDIN_FILENO
import platform.posix.STDOUT_FILENO
import platform.posix.TCSANOW
import platform.posix.TIOCGWINSZ
import platform.posix.X_OK
import platform.posix.access
import platform.posix.ioctl
import platform.posix.readlink
import platform.posix.tcgetattr
import platform.posix.tcsetattr
import platform.posix.termios
import platform.posix.winsize
import kotlin.math.PI
import kotlin.math.sin

/**
 * The desktop's pause, in a terminal of its own: the considered app's window
 * waits hidden while this breathes for the app's friction (Friction, as on
 * the phone: a pause, a long pause, typing the app's name, or locked during
 * wind-down, whose rungs also double then), timed by the shared Settings. **c** closes, **o** opens once
 * the pause is done; the decision goes to the daemon, which shows or closes
 * the window. Closing this window without deciding closes the app.
 */
object PauseUi {

    /** The pause terminal's window class: the daemon recognises it, and doesn't count it. */
    const val CLASS = "org.intentional.pause"

    /** The shared settings (sync), or the phone's defaults before any arrive. */
    fun settings(): Settings = readText(Paths.settings)?.let { runCatching { Settings.parse(it) }.getOrNull() } ?: Settings()

    /** Opens the pause for [app] in the first terminal found that takes a window class. */
    fun launch(app: String) {
        val self = selfPath()
        val args = listOf("pause", app)
        val terminal = listOf("kitty", "alacritty", "ghostty", "foot").firstOrNull(::onPath)
            ?: return log("pause: no terminal found (kitty, alacritty, ghostty or foot)")
        val command = when (terminal) {
            "kitty" -> listOf("kitty", "--class", CLASS, "-e", self) + args
            "alacritty" -> listOf("alacritty", "--class", CLASS, "-e", self) + args
            "ghostty" -> listOf("ghostty", "--class=$CLASS", "-e", self) + args
            else -> listOf("foot", "--app-id=$CLASS", self) + args
        }
        spawn(*command.toTypedArray())
    }

    fun run(app: String) {
        val name = app.removePrefix("hypr:")
        val friction = Considered.frictionOf(app) ?: Friction.PAUSE
        val settings = settings()
        val windDown = settings.windDown && WindDown.isActive(settings.windDownStartTime, settings.windDownEndTime)
        val locked = friction == Friction.NIGHT_LOCK && windDown
        val base = if (friction == Friction.LONG_PAUSE || friction == Friction.NIGHT_LOCK) settings.longPauseSeconds else settings.pauseSeconds
        val seconds = if (windDown) base * 2 else base
        val typing = friction == Friction.TYPE_NAME

        val start = monotonicMs()
        var typed = ""
        var decision: Boolean? = null
        withRawTerminal {
            while (decision == null) {
                val elapsed = (monotonicMs() - start) / 1000.0
                val left = (seconds - elapsed).toInt().coerceAtLeast(0) + if (elapsed < seconds) 1 else 0
                val nameOk = !typing || typed.equals(name, ignoreCase = true)
                val canOpen = !locked && left == 0 && nameOk
                draw(name, elapsed, left, locked, typing, typed, canOpen)
                for (key in readKeys(timeoutMs = 100)) {
                    when {
                        key == 'c' && !(typing && left == 0) -> decision = false
                        key == 27.toChar() -> decision = false
                        (key == 'o' && !typing || key == '\r' || key == '\n') && canOpen -> decision = true
                        typing && key == 127.toChar() -> typed = typed.dropLast(1)
                        typing && key.code >= 32 && typed.length < 64 -> typed += key
                    }
                }
            }
        }
        val daemon = connectUnix(Paths.socket)
        if (daemon >= 0) {
            val line = """{"type":"pause","app":"$app","decision":"${if (decision == true) "open" else "close"}"}""" + "\n"
            writeAll(daemon, line.encodeToByteArray())
            platform.posix.close(daemon)
        }
    }

    private fun draw(name: String, elapsed: Double, left: Int, locked: Boolean, typing: Boolean, typed: String, canOpen: Boolean) {
        val (rows, cols) = size()
        // The breath: a disc swelling and settling over 8 seconds.
        val r = 3.0 + 2.0 * sin(elapsed * 2 * PI / 8.0)
        val disc = (-5..5).map { y ->
            (-11..11).joinToString("") { x -> if ((x / 2.0) * (x / 2.0) + y * y <= r * r) "█" else " " }
        }
        val lines = buildList {
            addAll(disc)
            add("")
            add("b r e a t h e")
            add("what do you want from $name?")
            add("")
            when {
                locked -> add("locked until morning")
                typing -> add("type its name: ${typed}_")
            }
            add("")
            val open = when {
                locked -> ""
                left > 0 -> "open in ${left}s"
                canOpen -> if (typing) "[enter] open" else "[o] open"
                else -> "type its name to open"
            }
            add(if (typing) "[esc] close      $open" else "[c] close      $open")
        }
        val top = ((rows - lines.size) / 2).coerceAtLeast(0)
        val out = StringBuilder("\u001b[2J")
        lines.forEachIndexed { i, line ->
            val col = ((cols - line.length) / 2).coerceAtLeast(0) + 1
            out.append("\u001b[${top + i + 1};${col}H").append(line)
        }
        print(out)
        platform.posix.fflush(platform.posix.stdout)
    }

    private fun size(): Pair<Int, Int> = memScoped {
        val ws = alloc<winsize>()
        if (ioctl(STDOUT_FILENO, TIOCGWINSZ.convert(), ws.ptr) != 0 || ws.ws_row.toInt() == 0) 24 to 80
        else ws.ws_row.toInt() to ws.ws_col.toInt()
    }

    private fun readKeys(timeoutMs: Int): List<Char> {
        if (pollReadable(listOf(STDIN_FILENO), timeoutMs).isEmpty()) return emptyList()
        val buf = ByteArray(64)
        val n = readInto(STDIN_FILENO, buf)
        return if (n <= 0) emptyList() else buf.decodeToString(0, n).toList()
    }

    /** Keys one at a time, unechoed, the cursor hidden; all restored after. */
    private fun withRawTerminal(block: () -> Unit) = memScoped {
        val saved = alloc<termios>()
        tcgetattr(STDIN_FILENO, saved.ptr)
        val raw = alloc<termios>()
        tcgetattr(STDIN_FILENO, raw.ptr)
        raw.c_lflag = raw.c_lflag and (ICANON or ECHO).toUInt().inv()
        tcsetattr(STDIN_FILENO, TCSANOW, raw.ptr)
        print("\u001b[?25l")
        try {
            block()
        } finally {
            tcsetattr(STDIN_FILENO, TCSANOW, saved.ptr)
            print("\u001b[?25h\u001b[2J")
            platform.posix.fflush(platform.posix.stdout)
        }
    }

    private fun onPath(name: String): Boolean =
        (env("PATH") ?: "/usr/bin").split(':').any { access("$it/$name", X_OK) == 0 }

    /** This binary, to run the pause with: /proc/self/exe. */
    private fun selfPath(): String = memScoped {
        val buf = allocArray<ByteVar>(4096)
        val n = readlink("/proc/self/exe", buf, 4095.convert())
        if (n <= 0) "intentional" else { buf[n.toInt()] = 0; buf.toKString() }
    }
}
