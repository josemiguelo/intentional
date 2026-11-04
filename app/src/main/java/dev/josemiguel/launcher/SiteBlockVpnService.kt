package dev.josemiguel.launcher

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import android.util.Log
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * The network half of site budgets: a DNS-only VPN. It hands Android a fake
 * resolver ([DNS_ADDRESS]) and routes only that address into the tun, so all
 * real traffic keeps flowing over Wi-Fi / mobile data untouched. Queries for
 * the sites of an over-budget group get NXDOMAIN — in every browser and app,
 * not just the ones the guard can read — and the rest are relayed to the
 * underlying network's own DNS server.
 *
 * The guard still does the instant bounce; this is the backstop. Started
 * connections and app-level caches survive a new block for a few minutes.
 *
 * Needs the user's one-time VPN consent ([VpnService.prepare]), and Private
 * DNS on "Automatic" or off: a Private DNS hostname bypasses the fake resolver.
 */
class SiteBlockVpnService : VpnService() {

    private lateinit var worker: HandlerThread
    private lateinit var handler: Handler
    private val upstreamPool = Executors.newFixedThreadPool(4)

    @Volatile private var tun: ParcelFileDescriptor? = null

    /** Domains of over-budget groups; a query matching one, or a subdomain, is refused. */
    @Volatile private var blocked: Set<String> = emptySet()

    /**
     * The real networks underneath (never a VPN) and their DNS servers. The
     * default-network callback can't be used: even with the app excluded from
     * its own VPN, it reports the VPN — whose resolver is this service.
     */
    private val realNetworks = ConcurrentHashMap<Network, Pair<Boolean, InetAddress>>()

    /** Where allowed queries go: Wi-Fi's DNS server if there is Wi-Fi, else mobile data's. */
    private val upstream: Pair<Network, InetAddress>?
        get() = realNetworks.entries
            .sortedByDescending { it.value.first }
            .firstOrNull()
            ?.let { it.key to it.value.second }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
            val server = lp.dnsServers.firstOrNull { it is Inet4Address } ?: lp.dnsServers.firstOrNull()
            val wifi = getSystemService(ConnectivityManager::class.java)
                .getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            if (server == null) realNetworks.remove(network) else realNetworks[network] = wifi to server
        }

        override fun onLost(network: Network) {
            realNetworks.remove(network)
        }
    }

    private val refreshTick = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, REFRESH_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        worker = HandlerThread("site-block-vpn").apply { start() }
        handler = Handler(worker.looper)
        val realInternet = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        getSystemService(ConnectivityManager::class.java)
            .registerNetworkCallback(realInternet, networkCallback, handler)
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs(this).siteVpnEnabled = false
            shutDown()
            return START_NOT_STICKY
        }
        Prefs(this).siteVpnEnabled = true
        handler.post {
            if (tun == null) establish()
            handler.removeCallbacks(refreshTick)
            refreshTick.run()
        }
        return START_STICKY
    }

    /** The user turned the VPN off in Settings, or another VPN took over. */
    override fun onRevoke() {
        Log.i(TAG, "revoked")
        Prefs(this).siteVpnEnabled = false
        shutDown()
    }

    override fun onDestroy() {
        instance = null
        tun?.close()
        tun = null
        getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        worker.quitSafely()
        upstreamPool.shutdownNow()
        super.onDestroy()
    }

    private fun shutDown() {
        handler.removeCallbacks(refreshTick)
        tun?.close()
        tun = null
        stopSelf()
    }

    /**
     * Recomputes the blocked set. Names resolved before a block stay in
     * Android's resolver cache until their TTL runs out (minutes for most
     * sites) — rebuilding the tun to flush it left the VPN routing nowhere on
     * One UI, so a block is allowed that lag; the guard covers the browser
     * instantly in the meantime.
     */
    private fun refresh() {
        val now = Prefs(this).groups
            .filter { it.sites.isNotEmpty() }
            .filter { UsageStatsRepository.groupUsedMinutes(this, it) >= it.limitMinutes }
            .flatMap { it.sites }
            .toSet()
        val newlyBlocked = now - blocked
        blocked = now
        if (newlyBlocked.isNotEmpty()) Log.i(TAG, "now blocking $newlyBlocked")
    }

    /** Creates the tun and starts reading queries from it. */
    private fun establish() {
        val pfd = Builder()
            .setSession("Intentional site budgets")
            .addAddress(TUN_ADDRESS, 32)
            .addDnsServer(DNS_ADDRESS)
            .addRoute(DNS_ADDRESS, 32)
            // Unrouted traffic of both families falls through to the real network.
            .allowFamily(OsConstants.AF_INET)
            .allowFamily(OsConstants.AF_INET6)
            .addDisallowedApplication(packageName)
            .setMetered(false)
            .setMtu(MTU)
            .setBlocking(false)
            .setConfigureIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .establish()
        if (pfd == null) {
            Log.w(TAG, "establish failed: no VPN consent")
            stopSelf()
            return
        }
        tun = pfd
        thread(name = "site-block-tun") { readLoop(pfd) }
    }

    /** Reads queries off [pfd] until it's replaced or closed. */
    private fun readLoop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val poll = arrayOf(StructPollfd().apply {
            fd = pfd.fileDescriptor
            events = OsConstants.POLLIN.toShort()
        })
        val buffer = ByteArray(MTU)
        while (tun === pfd) {
            try {
                if (Os.poll(poll, POLL_TIMEOUT_MS) == 0) continue
            } catch (e: ErrnoException) {
                break
            }
            val length = try {
                input.read(buffer)
            } catch (e: IOException) {
                break
            }
            if (length <= 0) continue
            val query = DnsPacket.parse(buffer, length) ?: continue
            if (isBlocked(query.name)) {
                Log.i(TAG, "refused: ${query.name}")
                write(output, query.reply(query.nxdomain()))
            } else {
                upstreamPool.execute { forward(query, output) }
            }
        }
    }

    private fun isBlocked(name: String): Boolean =
        blocked.any { name == it || name.endsWith(".$it") }

    /** Relays an allowed query to the real network's resolver; on failure the asker just retries. */
    private fun forward(query: DnsPacket, output: FileOutputStream) {
        val (network, server) = upstream ?: return
        try {
            DatagramSocket().use { socket ->
                network.bindSocket(socket)
                socket.soTimeout = UPSTREAM_TIMEOUT_MS
                socket.send(DatagramPacket(query.payload, query.payload.size, server, 53))
                val buffer = ByteArray(MTU - 28) // room for the IPv4 + UDP headers
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                write(output, query.reply(buffer.copyOf(response.length)))
            }
        } catch (e: IOException) {
            // timeout or network gone
        }
    }

    private fun write(output: FileOutputStream, packet: ByteArray) = synchronized(output) {
        try {
            output.write(packet)
        } catch (e: IOException) {
            // tun closed mid-reply
        }
    }

    companion object {
        private const val TAG = "SiteBlockVpn"
        const val ACTION_STOP = "dev.josemiguel.launcher.STOP_SITE_VPN"
        private const val TUN_ADDRESS = "10.111.0.1"
        private const val DNS_ADDRESS = "10.111.0.53"
        private const val MTU = 4096
        private const val POLL_TIMEOUT_MS = 1_000
        private const val UPSTREAM_TIMEOUT_MS = 5_000
        private const val REFRESH_MS = 30_000L

        @Volatile private var instance: SiteBlockVpnService? = null

        /** Whether the tun is up in this process. */
        val isRunning: Boolean get() = instance?.tun != null

        fun start(context: Context) {
            context.startService(Intent(context, SiteBlockVpnService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, SiteBlockVpnService::class.java).setAction(ACTION_STOP))
        }

        /** Re-evaluates budgets now — the guard calls this the moment it blocks something. */
        fun refreshSoon() {
            instance?.let { vpn -> vpn.handler.post { vpn.refresh() } }
        }
    }
}
