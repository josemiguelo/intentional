package dev.josemiguel.intentional.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One day's on-screen time on a device, in ms: [apps] by app id (an Android
 * package, a Hyprland window class) and [sites] by host. A group's budget is
 * spent by both (see [usedMs]).
 */
@Serializable
data class DayUsage(
    val apps: Map<String, Long> = emptyMap(),
    val sites: Map<String, Long> = emptyMap(),
) {
    fun plusApp(app: String, ms: Long) = copy(apps = apps + (app to (apps[app] ?: 0L) + ms))

    fun plusSite(host: String, ms: Long) = copy(sites = sites + (host to (sites[host] ?: 0L) + ms))

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
