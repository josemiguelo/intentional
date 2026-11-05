package dev.josemiguel.intentional.desktop

import dev.josemiguel.intentional.core.AppGroup
import dev.josemiguel.intentional.core.DayUsage
import dev.josemiguel.intentional.core.isOverBudget
import dev.josemiguel.intentional.core.parseGroups
import dev.josemiguel.intentional.core.sync.SyncConfig
import kotlin.concurrent.AtomicInt
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.staticCFunction
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.posix.POLLIN
import platform.posix.SIGCHLD
import platform.posix.SIGINT
import platform.posix.SIGPIPE
import platform.posix.SIGTERM
import platform.posix.SIG_IGN
import platform.posix.close
import platform.posix.fflush
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.signal
import platform.posix.stdout
import platform.posix.unlink

private val stopping = AtomicInt(0)

/**
 * The desktop guard. Every second it books the focused window's time: to the
 * active tab's site while a Brave window has focus (the extension reports
 * the tab), else to the window's class. Every couple of seconds it checks the
 * groups: an over-budget app's window is closed, and over-budget sites go to
 * the extension, which moves their tabs off and refuses them until tomorrow.
 *
 * Nothing counts while the screen is locked, nor the screensaver's own
 * window: Omarchy's screensaver takes focus after a couple of idle minutes,
 * which ends a site's or app's time by itself, and its lock kills the
 * screensaver, leaving focus to whatever is under the lock.
 *
 * Groups are read from [Paths.groups] (re-read when the file changes), the
 * day's time is kept in [Paths.usage] and saved every [SAVE_MS].
 */
class Daemon {

    private var groupsRaw: String? = null
    private var groups = emptyList<AppGroup>()

    private var today = date()
    private var usage = loadUsage(today)

    /** The focused window: its class from activewindow, its address from activewindowv2. */
    private var focusedClass: String? = null
    private var focused: Hyprland.Window? = null

    /** The active tab's host in the last focused Brave window, per the extension. */
    private var browserHost: String? = null

    private var overGroups = emptySet<String>()
    private var blocked = emptyList<String>()
    private val closedAt = mutableMapOf<String, Long>()

    private val clients = mutableMapOf<Int, LineReader>()

    /** Sync with the other devices, when this machine has a [Paths.syncConfig]. */
    private val sync = readText(Paths.syncConfig)?.let { raw ->
        runCatching { SyncWorker(SyncConfig.parse(raw)) }
            .onFailure { log("sync: unreadable ${Paths.syncConfig}: ${it.message}") }
            .getOrNull()
    }

    private var locked = false
    private var lastLockCheck = 0L

    private val startedAt = monotonicMs()

    /** Per considered app with an open session: when it last had focus (see [onOpen]). */
    private val sessions = mutableMapOf<String, Long>()

    /** Considered apps whose windows wait, hidden, on their pause ([PauseUi]). */
    private val pending = mutableMapOf<String, Pending>()

    private class Pending {
        /** Each hidden window's address and the workspace it opened on. */
        val windows = mutableListOf<Pair<String, String>>()
        val since = monotonicMs()
        var pauseWindow: String? = null
        var pauseGoneAt: Long? = null
    }

    private var lastTick = monotonicMs()
    private var lastEnforce = 0L
    private var lastSave = monotonicMs()

    fun run() {
        signal(SIGCHLD, SIG_IGN)
        signal(SIGPIPE, SIG_IGN)
        signal(SIGTERM, staticCFunction { _: Int -> stopping.value = 1 })
        signal(SIGINT, staticCFunction { _: Int -> stopping.value = 1 })
        mkdirs(Paths.usageDir)
        reloadGroups()

        val events = Hyprland.connectEvents()
        check(events >= 0) { "can't reach Hyprland's event socket" }
        focused = Hyprland.activeWindow()
        focusedClass = focused?.cls
        val listener = listenUnix(Paths.socket)
        sync?.start()
        log("running: ${groups.size} groups, focused ${focused?.cls}, sync ${if (sync != null) "on" else "off"}")

        val eventLines = LineReader()
        val buf = ByteArray(8192)
        while (stopping.value == 0) {
            val fds = listOf(events, listener) + clients.keys
            val ready = pollReadable(fds, timeoutMs = 1000)
            for (fd in ready) {
                when (fd) {
                    events -> {
                        val n = readInto(events, buf)
                        check(n > 0) { "Hyprland's event socket closed" }
                        eventLines.feed(buf, n).forEach(::onEvent)
                    }
                    listener -> acceptClient(listener).takeIf { it >= 0 }?.let(::onConnect)
                    else -> {
                        val n = readInto(fd, buf)
                        if (n <= 0) {
                            close(fd)
                            clients.remove(fd)
                        } else clients[fd]?.feed(buf, n)?.forEach(::onMessage)
                    }
                }
            }
            tick()
        }
        save()
        unlink(Paths.socket)
        log("stopped")
    }

    private fun onEvent(line: String) {
        val (name, data) = line.split(">>", limit = 2).takeIf { it.size == 2 } ?: return
        when (name) {
            "openwindow" -> onOpen(data)
            "closewindow" -> onClose("0x$data")
            "activewindow" -> focusedClass = data.substringBefore(',').ifEmpty { null }
            "activewindowv2" -> {
                credit()
                val cls = focusedClass
                focused = if (data.isEmpty() || cls == null) null else Hyprland.Window(cls, "0x$data")
                enforce()
            }
        }
    }

    /**
     * "openwindow>>ADDRESS,WORKSPACE,CLASS,TITLE". A considered app's new
     * window is hidden and its pause comes up, unless it has a session (its
     * pause said open, and it hasn't been away [AWAY_MS] since) or the
     * daemon only just started (apps autostarting with the session).
     */
    private fun onOpen(data: String) {
        val parts = data.split(',', limit = 4)
        if (parts.size < 3) return
        val address = "0x${parts[0]}"
        val workspace = parts[1]
        val cls = parts[2]
        if (cls == PauseUi.CLASS) {
            pending.values.firstOrNull { it.pauseWindow == null }?.pauseWindow = address
            return
        }
        val app = appId(cls)
        if (monotonicMs() - startedAt < STARTUP_GRACE_MS) return
        if (Considered.frictionOf(app) == null) return
        val session = sessions[app]
        if (session != null && monotonicMs() - session < AWAY_MS) return
        sessions.remove(app)
        Hyprland.hide(address)
        val waiting = pending.getOrPut(app) {
            log("pause: $cls")
            PauseUi.launch(app)
            Pending()
        }
        waiting.windows += address to workspace
    }

    /** A pause window closing without a decision counts as close, once its decision has had time to arrive. */
    private fun onClose(address: String) {
        pending.values.firstOrNull { it.pauseWindow == address }?.pauseGoneAt = monotonicMs()
        pending.values.forEach { p -> p.windows.removeAll { it.first == address } }
    }

    /** {"type":"pause","app":"hypr:steam","decision":"open"|"close"}, from the pause. */
    private fun onDecision(app: String, open: Boolean) {
        val waiting = pending.remove(app) ?: return
        log("pause: ${app.removePrefix("hypr:")} · ${if (open) "open" else "close"}")
        if (open) {
            sessions[app] = monotonicMs()
            waiting.windows.forEach { (address, workspace) -> Hyprland.restore(address, workspace) }
        } else {
            waiting.windows.forEach { (address, _) -> Hyprland.closeWindow(address) }
        }
    }

    private fun onConnect(fd: Int) {
        clients[fd] = LineReader()
        send(fd)
    }

    /** {"type":"tab","host":"x.com","focused":true}: the active tab of the last focused Brave window. */
    private fun onMessage(line: String) {
        val o = runCatching { Json.parseToJsonElement(line).jsonObject }.getOrNull() ?: return
        if (o["type"]?.jsonPrimitive?.contentOrNull == "pause") {
            val app = o["app"]?.jsonPrimitive?.contentOrNull ?: return
            return onDecision(app, o["decision"]?.jsonPrimitive?.contentOrNull == "open")
        }
        if (o["type"]?.jsonPrimitive?.contentOrNull != "tab") return
        if (o["focused"]?.jsonPrimitive?.booleanOrNull == false) return
        credit()
        browserHost = o["host"]?.jsonPrimitive?.contentOrNull
    }

    private fun tick() {
        val now = monotonicMs()
        if (now - lastLockCheck >= LOCK_CHECK_MS) {
            lastLockCheck = now
            // Credit up to the change, so the time on either side lands right.
            val nowLocked = Hyprland.isLocked()
            if (nowLocked != locked) {
                credit()
                locked = nowLocked
                log(if (locked) "locked: not counting" else "unlocked")
            }
        }
        credit()
        // A pause left without a decision (its window closed, or forgotten) closes its app.
        pending.filterValues { p ->
            p.pauseGoneAt?.let { now - it > DECISION_GRACE_MS } == true || now - p.since > PAUSE_MAX_MS
        }.keys.forEach { onDecision(it, open = false) }
        if (now - lastEnforce >= ENFORCE_MS) enforce()
        if (now - lastSave >= SAVE_MS) save()
    }

    /** Books the time since the last credit to whatever had focus. */
    private fun credit() {
        val now = monotonicMs()
        val ms = now - lastTick
        lastTick = now
        rollDay()
        // A long gap means the loop didn't run (suspend): count none of it.
        if (ms <= 0 || ms > MAX_CREDIT_MS || locked) return
        val window = focused?.takeUnless { it.cls in NOT_COUNTED } ?: return
        // While a considered app has focus its session stays open.
        appId(window.cls).let { if (it in sessions) sessions[it] = now }
        usage = if (isBrowser(window.cls)) {
            val host = browserHost ?: return
            usage.plusSite(host, ms)
        } else usage.plusApp(appId(window.cls), ms)
    }

    private fun enforce() {
        lastEnforce = monotonicMs()
        reloadGroups()
        sync?.publish(today, usage.onlyFor(groups))
        // With sync, a group's budget is spent across devices (its limit, the
        // server's, arrives through groups.json).
        val spent = sync?.latest()?.takeIf { it.day == today }?.let { usage + it.others } ?: usage
        val over = groups.filter { it.isOverBudget(spent) }

        (over.map { it.name }.toSet() - overGroups).forEach { notify("$it · budget used today") }
        overGroups = over.map { it.name }.toSet()

        val window = focused
        if (window != null && !isBrowser(window.cls) && over.any { appId(window.cls) in it.packages }) {
            val now = monotonicMs()
            if (now - (closedAt[window.address] ?: 0L) > CLOSE_DEBOUNCE_MS) {
                closedAt[window.address] = now
                log("closing ${window.cls}")
                Hyprland.closeWindow(window.address)
            }
        }

        val sites = over.flatMap { it.sites }.distinct().sorted()
        if (sites != blocked) {
            blocked = sites
            log("blocked sites: $sites")
            clients.keys.forEach(::send)
        }
    }

    /** Tells a native host which sites are blocked. */
    private fun send(fd: Int) {
        val list = blocked.joinToString(",") { JsonPrimitive(it).toString() }
        writeAll(fd, "{\"blocked\":[$list]}\n".encodeToByteArray())
    }

    private fun reloadGroups() {
        val raw = readText(Paths.groups)
        if (raw == groupsRaw) return
        groupsRaw = raw
        groups = raw?.let { runCatching { parseGroups(it) }.getOrNull() } ?: emptyList()
        log("groups: ${groups.joinToString { "${it.name} ${it.limitMinutes}m" }}")
    }

    private fun rollDay() {
        val d = date()
        if (d == today) return
        save()
        today = d
        usage = loadUsage(d)
        closedAt.clear()
    }

    private fun save() {
        lastSave = monotonicMs()
        writeText(Paths.usage(today), usage.toJson())
    }

    private fun notify(text: String) = spawn("notify-send", "-a", "intentional", "intentional", text)

    private companion object {
        const val ENFORCE_MS = 2_000L
        const val SAVE_MS = 10_000L
        const val MAX_CREDIT_MS = 5_000L
        const val CLOSE_DEBOUNCE_MS = 3_000L
        const val LOCK_CHECK_MS = 2_000L
        const val AWAY_MS = 5 * 60_000L
        const val STARTUP_GRACE_MS = 30_000L
        const val DECISION_GRACE_MS = 2_000L
        const val PAUSE_MAX_MS = 15 * 60_000L

        /** Windows that are nobody's time: Omarchy's screensaver, the pause itself. */
        val NOT_COUNTED = setOf("org.omarchy.screensaver", PauseUi.CLASS)

        /** A window class as groups and the ledger name it, apart from Android packages. */
        fun appId(cls: String) = "hypr:$cls"

        /** Brave windows, its web-app windows included ("brave-claude.ai__-Default"). */
        fun isBrowser(cls: String) = cls == "brave-browser" || cls.startsWith("brave-")
    }
}

fun date(): String = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()

fun epochMs(): Long = Clock.System.now().toEpochMilliseconds()

fun loadUsage(day: String): DayUsage =
    readText(Paths.usage(day))?.let { runCatching { DayUsage.parse(it) }.getOrNull() } ?: DayUsage()

/** To stdout, flushed: under systemd that's the journal, which a buffer would hold back. */
fun log(message: String) {
    println("intentional: $message")
    fflush(stdout)
}

/** The fds among [fds] with input (or a hangup) waiting, after at most [timeoutMs]. */
fun pollReadable(fds: List<Int>, timeoutMs: Int): List<Int> = memScoped {
    val pfds = allocArray<pollfd>(fds.size)
    fds.forEachIndexed { i, fd ->
        pfds[i].fd = fd
        pfds[i].events = POLLIN.convert()
        pfds[i].revents = 0
    }
    if (poll(pfds, fds.size.convert(), timeoutMs) <= 0) return@memScoped emptyList()
    fds.filterIndexed { i, _ -> pfds[i].revents.toInt() != 0 }
}
