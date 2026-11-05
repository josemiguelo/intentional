package dev.josemiguel.intentional.desktop

import platform.posix.SIGPIPE
import platform.posix.SIG_IGN
import platform.posix.signal

/**
 * The Brave extension's native-messaging host: Brave starts it with the
 * extension's origin as its argument and talks length-prefixed JSON over
 * stdin/stdout (4 bytes, native byte order, then UTF-8). It relays each
 * message to the daemon's socket as a line, and each line back as a message;
 * it ends when either side does, and the extension reconnects.
 */
object NativeHost {

    private const val STDIN = 0
    private const val STDOUT = 1

    fun run() {
        signal(SIGPIPE, SIG_IGN)
        val daemon = connectUnix(Paths.socket)
        if (daemon < 0) {
            log("native host: daemon not running")
            return
        }
        val fromDaemon = LineReader()
        val pending = mutableListOf<Byte>()
        val buf = ByteArray(8192)
        while (true) {
            for (fd in pollReadable(listOf(STDIN, daemon), timeoutMs = -1)) {
                val n = readInto(fd, buf)
                if (n <= 0) return
                if (fd == STDIN) {
                    for (i in 0 until n) pending += buf[i]
                    while (pending.size >= 4) {
                        val len = (pending[0].toInt() and 0xff) or
                            ((pending[1].toInt() and 0xff) shl 8) or
                            ((pending[2].toInt() and 0xff) shl 16) or
                            ((pending[3].toInt() and 0xff) shl 24)
                        if (pending.size < 4 + len) break
                        val message = pending.subList(4, 4 + len).toByteArray()
                        repeat(4 + len) { pending.removeAt(0) }
                        writeAll(daemon, message + '\n'.code.toByte())
                    }
                } else {
                    fromDaemon.feed(buf, n).forEach { line ->
                        val bytes = line.encodeToByteArray()
                        val len = bytes.size
                        val header = byteArrayOf(
                            len.toByte(), (len shr 8).toByte(), (len shr 16).toByte(), (len shr 24).toByte(),
                        )
                        writeAll(STDOUT, header + bytes)
                    }
                }
            }
        }
    }
}
