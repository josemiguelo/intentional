package dev.josemiguel.intentional.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One day's on-screen time on a device, in ms: [apps] by app id (an Android
 * package, or "hypr:<window class>" on a desktop) and [sites] by host. A group's budget is
 * spent by both (see [usedMs]).
 */
@Serializable
data class DayUsage(
    val apps: Map<String, Long> = emptyMap(),
    val sites: Map<String, Long> = emptyMap(),
    /** Apps opened through their pause (or, on the phone, intentionally from home). */
    val opened: Int = 0,
    /** Pauses that ended in close: the moments the app is built for. */
    val closed: Int = 0,
) {
    fun plusApp(app: String, ms: Long) = copy(apps = apps + (app to (apps[app] ?: 0L) + ms))

    fun plusSite(host: String, ms: Long) = copy(sites = sites + (host to (sites[host] ?: 0L) + ms))

    /** Two devices' days, added: what a group spends across them, and the day's decisions. */
    operator fun plus(other: DayUsage) = DayUsage(
        apps = (apps.keys + other.apps.keys).associateWith { (apps[it] ?: 0L) + (other.apps[it] ?: 0L) },
        sites = (sites.keys + other.sites.keys).associateWith { (sites[it] ?: 0L) + (other.sites[it] ?: 0L) },
        opened = opened + other.opened,
        closed = closed + other.closed,
    )

    /** Only what counts toward some group, and the counts: all that leaves the device. */
    fun onlyFor(groups: List<AppGroup>) = copy(
        apps = apps.filterKeys { app -> groups.any { app in it.packages } },
        sites = sites.filterKeys { host -> groups.any { g -> g.sites.any { siteCovers(it, host) } } },
    )

    fun toJson(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(raw: String): DayUsage = json.decodeFromString(serializer(), raw)
    }
}

/** The group's budget spent in [day]: its apps' time plus its sites' (subdomains included), each host once. */
fun AppGroup.usedMs(day: DayUsage): Long =
    packages.sumOf { day.apps[it] ?: 0L } +
        day.sites.entries.sumOf { (host, ms) -> if (sites.any { siteCovers(it, host) }) ms else 0L }

fun AppGroup.isOverBudget(day: DayUsage): Boolean = usedMs(day) >= limitMinutes * 60_000L
