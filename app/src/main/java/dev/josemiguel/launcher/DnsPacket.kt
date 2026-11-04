package dev.josemiguel.launcher

/**
 * Just enough IPv4/UDP/DNS to run a DNS-only VPN: pull the query out of a
 * packet read from the tun, and wrap an answer back into one. The VPN only
 * routes its own resolver address, so everything it sees is a DNS query —
 * anything else (TCP, IPv6, malformed) is dropped by returning null.
 */
class DnsPacket private constructor(
    private val srcAddr: ByteArray,
    private val dstAddr: ByteArray,
    private val srcPort: Int,
    /** The DNS message itself, as sent to port 53. */
    val payload: ByteArray,
    /** The first question's name, lowercased, without the trailing dot. */
    val name: String,
) {

    /** The IPv4/UDP packet carrying [dns] back to whoever asked. */
    fun reply(dns: ByteArray): ByteArray {
        val udpLength = UDP_HEADER + dns.size
        val total = IP_HEADER + udpLength
        val out = ByteArray(total)
        out[0] = 0x45 // IPv4, 20-byte header
        putShort(out, 2, total)
        putShort(out, 6, 0x4000) // don't fragment
        out[8] = 64 // TTL
        out[9] = UDP
        dstAddr.copyInto(out, 12) // swapped: we answer as the resolver
        srcAddr.copyInto(out, 16)
        putShort(out, 10, checksum(out, 0, IP_HEADER))
        putShort(out, IP_HEADER, DNS_PORT)
        putShort(out, IP_HEADER + 2, srcPort)
        putShort(out, IP_HEADER + 4, udpLength)
        // UDP checksum 0 = none, which IPv4 allows.
        dns.copyInto(out, IP_HEADER + UDP_HEADER)
        return out
    }

    /**
     * An NXDOMAIN answer to this query: the header and question echoed back
     * with no records, so the name simply doesn't exist for the asker.
     */
    fun nxdomain(): ByteArray {
        val end = questionEnd(payload) ?: payload.size
        val dns = payload.copyOf(end)
        dns[2] = (0x80 or (payload[2].toInt() and 0x79)).toByte() // QR, keep opcode + RD
        dns[3] = (0x80 or 0x03).toByte() // RA, RCODE 3
        putShort(dns, 4, 1)
        for (i in 6 until 12) dns[i] = 0
        return dns
    }

    companion object {
        private const val IP_HEADER = 20
        private const val UDP_HEADER = 8
        private const val UDP: Byte = 17
        private const val DNS_PORT = 53

        /** Parses an IPv4 UDP packet to port 53 holding a DNS query, or null. */
        fun parse(packet: ByteArray, length: Int): DnsPacket? {
            if (length < IP_HEADER + UDP_HEADER + 12) return null
            if (packet[0].toInt() ushr 4 != 4 || packet[9] != UDP) return null
            val ihl = (packet[0].toInt() and 0x0F) * 4
            val udp = ihl
            if (getShort(packet, udp + 2) != DNS_PORT) return null
            val dnsStart = udp + UDP_HEADER
            val dnsEnd = minOf(length, udp + getShort(packet, udp + 4))
            if (dnsEnd - dnsStart < 12) return null
            val payload = packet.copyOfRange(dnsStart, dnsEnd)
            val name = readName(payload) ?: return null
            return DnsPacket(
                srcAddr = packet.copyOfRange(12, 16),
                dstAddr = packet.copyOfRange(16, 20),
                srcPort = getShort(packet, udp),
                payload = payload,
                name = name,
            )
        }

        /** The first question's name, read label by label from offset 12. */
        private fun readName(dns: ByteArray): String? {
            if (getShort(dns, 4) < 1) return null
            val labels = mutableListOf<String>()
            var i = 12
            while (i < dns.size) {
                val len = dns[i].toInt() and 0xFF
                if (len == 0) return labels.joinToString(".").lowercase()
                if (len and 0xC0 != 0 || i + 1 + len > dns.size) return null // no compression in a question
                labels += String(dns, i + 1, len, Charsets.US_ASCII)
                i += 1 + len
            }
            return null
        }

        /** Offset just past the first question (name + type + class). */
        private fun questionEnd(dns: ByteArray): Int? {
            var i = 12
            while (i < dns.size) {
                val len = dns[i].toInt() and 0xFF
                if (len == 0) return (i + 1 + 4).takeIf { it <= dns.size }
                i += 1 + len
            }
            return null
        }

        private fun getShort(b: ByteArray, at: Int): Int =
            ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

        private fun putShort(b: ByteArray, at: Int, value: Int) {
            b[at] = (value ushr 8).toByte()
            b[at + 1] = value.toByte()
        }

        private fun checksum(b: ByteArray, from: Int, length: Int): Int {
            var sum = 0
            var i = from
            while (i < from + length) {
                sum += getShort(b, i)
                i += 2
            }
            while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
            return sum.inv() and 0xFFFF
        }
    }
}
