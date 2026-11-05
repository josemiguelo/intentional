package dev.josemiguel.intentional.desktop

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.AF_UNIX
import platform.posix.CLOCK_MONOTONIC
import platform.posix.O_CREAT
import platform.posix.O_RDONLY
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.SOCK_STREAM
import platform.posix.accept
import platform.posix.bind
import platform.posix.clock_gettime
import platform.posix.close
import platform.posix.connect
import platform.posix.execvp
import platform.posix.fork
import platform.posix.getenv
import platform.posix.listen
import platform.posix.memset
import platform.posix.mkdir
import platform.posix.open
import platform.posix.read
import platform.posix.rename
import platform.linux.sockaddr_un
import platform.posix.socket
import platform.posix.timespec
import platform.posix.unlink
import platform.posix.write
import platform.posix._exit

/** The thin POSIX layer the daemon and the native host stand on. */

fun env(name: String): String? = getenv(name)?.toKString()?.takeIf { it.isNotEmpty() }

val home: String get() = env("HOME") ?: error("HOME unset")

fun monotonicMs(): Long = memScoped {
    val ts = alloc<timespec>()
    clock_gettime(CLOCK_MONOTONIC, ts.ptr)
    ts.tv_sec * 1000L + ts.tv_nsec / 1_000_000L
}

private fun unixAddress(path: String, block: (CPointer<platform.posix.sockaddr>, UInt) -> Int): Int = memScoped {
    val addr = alloc<sockaddr_un>()
    memset(addr.ptr, 0, sizeOf<sockaddr_un>().convert())
    addr.sun_family = AF_UNIX.convert()
    val bytes = path.encodeToByteArray()
    require(bytes.size < 108) { "socket path too long: $path" }
    bytes.forEachIndexed { i, b -> addr.sun_path[i] = b }
    block(addr.ptr.reinterpret(), sizeOf<sockaddr_un>().convert())
}

/** A connected Unix stream socket, or -1. */
fun connectUnix(path: String): Int {
    val fd = socket(AF_UNIX, SOCK_STREAM, 0)
    if (fd < 0) return -1
    val ok = unixAddress(path) { addr, len -> connect(fd, addr, len) } == 0
    if (!ok) close(fd)
    return if (ok) fd else -1
}

/** A listening Unix stream socket at [path], replacing a stale one. */
fun listenUnix(path: String): Int {
    unlink(path)
    val fd = socket(AF_UNIX, SOCK_STREAM, 0)
    check(fd >= 0) { "socket failed" }
    check(unixAddress(path) { addr, len -> bind(fd, addr, len) } == 0) { "bind $path failed" }
    check(listen(fd, 8) == 0) { "listen failed" }
    return fd
}

fun acceptClient(listener: Int): Int = accept(listener, null, null)

/** Reads what's available into [buf]; 0 at end of stream, -1 on error. */
fun readInto(fd: Int, buf: ByteArray): Int =
    buf.usePinned { read(fd, it.addressOf(0), buf.size.convert()).toInt() }

fun writeAll(fd: Int, bytes: ByteArray): Boolean {
    var off = 0
    bytes.usePinned { pinned ->
        while (off < bytes.size) {
            val n = write(fd, pinned.addressOf(off), (bytes.size - off).convert()).toInt()
            if (n <= 0) return false
            off += n
        }
    }
    return true
}

/** Splits a byte stream into UTF-8 lines. */
class LineReader {
    private val pending = mutableListOf<Byte>()

    fun feed(buf: ByteArray, n: Int): List<String> {
        val lines = mutableListOf<String>()
        for (i in 0 until n) {
            val b = buf[i]
            if (b == '\n'.code.toByte()) {
                lines += pending.toByteArray().decodeToString()
                pending.clear()
            } else pending += b
        }
        return lines
    }
}

/** Runs [args] detached, without a shell (SIGCHLD is ignored, so children reap themselves). */
fun spawn(vararg args: String) {
    if (fork() != 0) return
    memScoped {
        val argv = allocArray<kotlinx.cinterop.CPointerVar<ByteVar>>(args.size + 1)
        args.forEachIndexed { i, a -> argv[i] = a.cstr.ptr }
        argv[args.size] = null
        execvp(args[0], argv)
    }
    _exit(127)
}

fun readText(path: String): String? {
    val fd = open(path, O_RDONLY)
    if (fd < 0) return null
    val out = mutableListOf<Byte>()
    val buf = ByteArray(8192)
    while (true) {
        val n = readInto(fd, buf)
        if (n <= 0) break
        for (i in 0 until n) out += buf[i]
    }
    close(fd)
    return out.toByteArray().decodeToString()
}

/** Writes through a temporary file and a rename, so a crash never leaves half a file. */
fun writeText(path: String, text: String) {
    val tmp = "$path.tmp"
    val fd = open(tmp, O_WRONLY or O_CREAT or O_TRUNC, 0b110_100_100)
    check(fd >= 0) { "can't write $tmp" }
    writeAll(fd, text.encodeToByteArray())
    close(fd)
    rename(tmp, path)
}

fun mkdirs(path: String) {
    var at = ""
    for (part in path.split('/').filter { it.isNotEmpty() }) {
        at += "/$part"
        mkdir(at, 0b111_101_101u)
    }
}
