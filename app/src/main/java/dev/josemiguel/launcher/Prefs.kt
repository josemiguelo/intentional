package dev.josemiguel.launcher

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime

/**
 * Persistence for the launcher's three pieces of state: the favorites list
 * (ordered), the "considered" set (apps that only open through the pause
 * screen), and per-day open counters. The counters are the launcher's whole
 * metric system: intentional opens vs. paused-and-closed — never minutes.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    companion object {
        const val MAX_LIMIT_MINUTES = 60
        const val MIN_LIMIT_MINUTES = 5
        const val LIMIT_CHANGE_COOLDOWN_MS = 7L * 86_400_000L
    }

    var favorites: List<String>
        get() = sp.getString("favorites", "")!!.split('\n').filter { it.isNotBlank() }
        set(value) = sp.edit().putString("favorites", value.joinToString("\n")).apply()

    var considered: Set<String>
        get() = sp.getStringSet("considered", emptySet())!!
        set(value) = sp.edit().putStringSet("considered", value).apply()

    fun toggleFavorite(packageName: String) {
        favorites = if (packageName in favorites) favorites - packageName
        else favorites + packageName
    }

    fun toggleConsidered(packageName: String) {
        considered = if (packageName in considered) considered - packageName
        else considered + packageName
    }

    /** Favorites that stay visible during wind-down. Empty set = show all. */
    var essentials: Set<String>
        get() = sp.getStringSet("essentials", emptySet())!!
        set(value) = sp.edit().putStringSet("essentials", value).apply()

    fun toggleEssential(packageName: String) {
        essentials = if (packageName in essentials) essentials - packageName
        else essentials + packageName
    }

    /** Apps whose notifications are batched. Independent of considered. */
    var batched: Set<String>
        get() = sp.getStringSet("batched", emptySet())!!
        set(value) = sp.edit().putStringSet("batched", value).apply()

    fun toggleBatched(packageName: String) {
        batched = if (packageName in batched) batched - packageName
        else batched + packageName
    }

    /**
     * Mandatory: strict mode is always on and cannot be disabled in-app.
     * The guard service and its launcher-switch / device-admin blocking are
     * therefore permanently armed once the accessibility service is enabled.
     * (This is a deliberate override of the addiction-intervention evidence,
     * which favors escapable friction over hard self-imposed locks.)
     */
    val strictMode: Boolean get() = true

    /** Whether the site-budget VPN should be running; restored on boot. */
    var siteVpnEnabled: Boolean
        get() = sp.getBoolean("site_vpn", false)
        set(value) = sp.edit().putBoolean("site_vpn", value).apply()

    var windDownEnabled: Boolean
        get() = sp.getBoolean("winddown_enabled", true)
        set(value) = sp.edit().putBoolean("winddown_enabled", value).apply()

    var windDownStart: LocalTime
        get() = minutesToTime(sp.getInt("winddown_start", 22 * 60))
        set(value) = sp.edit().putInt("winddown_start", timeToMinutes(value)).apply()

    var windDownEnd: LocalTime
        get() = minutesToTime(sp.getInt("winddown_end", 6 * 60))
        set(value) = sp.edit().putInt("winddown_end", timeToMinutes(value)).apply()

    var pauseSeconds: Int
        get() = sp.getInt("pause_seconds", 8)
        set(value) = sp.edit().putInt("pause_seconds", value.coerceIn(1, 300)).apply()

    var longPauseSeconds: Int
        get() = sp.getInt("long_pause_seconds", 25)
        set(value) = sp.edit().putInt("long_pause_seconds", value.coerceIn(1, 600)).apply()

    /** Batch delivery times for held notifications, sorted. */
    var dropTimes: List<LocalTime>
        get() = sp.getString("drop_times", "540,780,1140")!!
            .split(',')
            .filter { it.isNotBlank() }
            .map { minutesToTime(it.trim().toInt()) }
            .sorted()
        set(value) = sp.edit()
            .putString("drop_times", value.map(::timeToMinutes).distinct().sorted().joinToString(","))
            .apply()

    fun resetToday() {
        sp.edit()
            .remove("intentional-${todaySuffix()}")
            .remove("paused-${todaySuffix()}")
            .apply()
    }

    private fun minutesToTime(minutes: Int) = LocalTime.of(minutes / 60, minutes % 60)
    private fun timeToMinutes(time: LocalTime) = time.hour * 60 + time.minute

    fun frictionFor(packageName: String): Friction =
        Friction.of(sp.getInt("friction-$packageName", 0))

    fun cycleFriction(packageName: String) {
        sp.edit().putInt("friction-$packageName", frictionFor(packageName).next().ordinal).apply()
    }

    /**
     * App groups sharing a daily time budget. An app belongs to at most one
     * group; apps in no group are unlimited. Budgets are hard-enforced by
     * GuardService and changeable only once a week.
     */
    var groups: List<AppGroup>
        get() = parseGroups(sp.getString("groups", "[]")!!)
        set(value) = sp.edit().putString("groups", value.toGroupsJson()).apply()

    fun groupOf(packageName: String): AppGroup? =
        groups.firstOrNull { packageName in it.packages }

    fun addGroup(name: String) {
        if (name.isBlank() || groups.any { it.name.equals(name, ignoreCase = true) }) return
        groups = groups + AppGroup(name.trim(), emptySet(), MAX_LIMIT_MINUTES, 0L)
    }

    fun deleteGroup(name: String) {
        groups = groups.filterNot { it.name == name }
    }

    /** Moves an app into [groupName], or out of every group if null. */
    fun assignToGroup(packageName: String, groupName: String?) {
        groups = groups.map { g ->
            val without = g.packages - packageName
            when {
                g.name == groupName -> g.copy(packages = without + packageName)
                else -> g.copy(packages = without)
            }
        }
    }

    /** The group whose sites cover [host] (a site or any subdomain of it). */
    fun groupOfSite(host: String): AppGroup? =
        groups.firstOrNull { g -> g.sites.any { host == it || host.endsWith(".$it") } }

    /**
     * Moves a site (typed as a domain or pasted as a URL) into [groupName], or
     * out of every group if null. Returns false if it isn't a valid domain.
     */
    fun assignSiteToGroup(site: String, groupName: String?): Boolean {
        val host = SiteTracker.hostOf(site) ?: return false
        // "m.facebook.com" means the site, not just its mobile host.
        val domain = host.removePrefix("m.").takeIf { '.' in it } ?: host
        groups = groups.map { g ->
            val without = g.sites - domain
            if (g.name == groupName) g.copy(sites = without + domain) else g.copy(sites = without)
        }
        return true
    }

    fun removeSite(groupName: String, domain: String) {
        groups = groups.map { g -> if (g.name == groupName) g.copy(sites = g.sites - domain) else g }
    }

    fun canChangeGroupLimit(group: AppGroup): Boolean =
        System.currentTimeMillis() - group.limitChangedAt >= LIMIT_CHANGE_COOLDOWN_MS

    fun daysUntilChangeable(group: AppGroup): Int {
        val remaining = LIMIT_CHANGE_COOLDOWN_MS - (System.currentTimeMillis() - group.limitChangedAt)
        return ((remaining + 86_400_000L - 1) / 86_400_000L).toInt().coerceAtLeast(0)
    }

    /** Sets a group's budget, honoring the once-a-week cooldown. */
    fun setGroupLimit(name: String, minutes: Int) {
        groups = groups.map { g ->
            if (g.name == name && canChangeGroupLimit(g)) {
                g.copy(
                    limitMinutes = minutes.coerceIn(MIN_LIMIT_MINUTES, MAX_LIMIT_MINUTES),
                    limitChangedAt = System.currentTimeMillis(),
                )
            } else g
        }
    }

    private fun todaySuffix() = LocalDate.now().toString()

    fun incrementIntentional() = increment("intentional-${todaySuffix()}")

    fun incrementPausedClosed() = increment("paused-${todaySuffix()}")

    private fun increment(key: String) {
        sp.edit().putInt(key, sp.getInt(key, 0) + 1).apply()
    }

    /** Adds [ms] of on-screen time to [host] for today; returns today's total. */
    fun addSiteTime(host: String, ms: Long): Long {
        val key = "site-${todaySuffix()}-$host"
        val total = sp.getLong(key, 0L) + ms
        sp.edit().putLong(key, total).apply()
        return total
    }

    /** Today's on-screen time for [domain] and its subdomains, in ms. */
    fun siteTimeToday(domain: String): Long {
        val prefix = "site-${todaySuffix()}-"
        return sp.all.entries.sumOf { (key, value) ->
            val host = key.removePrefix(prefix)
            val matches = key.startsWith(prefix) && (host == domain || host.endsWith(".$domain"))
            if (matches) value as Long else 0L
        }
    }

    /** (intentional opens, paused-and-closed) for today. */
    fun countsToday(): Pair<Int, Int> =
        sp.getInt("intentional-${todaySuffix()}", 0) to sp.getInt("paused-${todaySuffix()}", 0)

    /** Daily counters for the last [days] days, most recent first. */
    fun history(days: Int): List<DayCounts> {
        val today = LocalDate.now()
        return (0 until days).map { back ->
            val date = today.minusDays(back.toLong())
            DayCounts(
                date = date,
                intentional = sp.getInt("intentional-$date", 0),
                pausedClosed = sp.getInt("paused-$date", 0),
            )
        }
    }
}

data class DayCounts(
    val date: LocalDate,
    val intentional: Int,
    val pausedClosed: Int,
)
