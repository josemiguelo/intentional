package dev.josemiguel.launcher

import android.content.Context
import dev.josemiguel.intentional.core.AppGroup
import dev.josemiguel.intentional.core.Budget
import dev.josemiguel.intentional.core.Friction
import dev.josemiguel.intentional.core.canChangeLimit
import dev.josemiguel.intentional.core.daysUntilChangeable
import dev.josemiguel.intentional.core.groupOf
import dev.josemiguel.intentional.core.groupOfSite
import dev.josemiguel.intentional.core.parseGroups
import dev.josemiguel.intentional.core.siteCovers
import dev.josemiguel.intentional.core.siteDomain
import dev.josemiguel.intentional.core.sync.SyncedConsidered
import dev.josemiguel.intentional.core.sync.SyncedGroups
import dev.josemiguel.intentional.core.sync.parseConsideredJson
import dev.josemiguel.intentional.core.sync.toConsideredJson
import dev.josemiguel.intentional.core.toGroupsJson
import dev.josemiguel.intentional.core.withApp
import dev.josemiguel.intentional.core.withGroup
import dev.josemiguel.intentional.core.withLimit
import dev.josemiguel.intentional.core.withSite
import dev.josemiguel.intentional.core.withoutGroup
import dev.josemiguel.intentional.core.withoutSite
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
        const val MAX_LIMIT_MINUTES = Budget.MAX_LIMIT_MINUTES
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
     * App groups sharing a daily time budget; the rules live in core's
     * Groups.kt. Budgets are hard-enforced by GuardService and changeable
     * only once a week.
     */
    var groups: List<AppGroup>
        get() = parseGroups(sp.getString("groups", "[]")!!)
        set(value) = sp.edit().putString("groups", value.toGroupsJson()).apply()

    fun groupOf(packageName: String): AppGroup? = groups.groupOf(packageName)

    fun addGroup(name: String) {
        groups = groups.withGroup(name)
    }

    fun deleteGroup(name: String) {
        groups = groups.withoutGroup(name)
    }

    /** Moves an app into [groupName], or out of every group if null. */
    fun assignToGroup(packageName: String, groupName: String?) {
        groups = groups.withApp(packageName, groupName)
    }

    /** The group whose sites cover [host] (a site or any subdomain of it). */
    fun groupOfSite(host: String): AppGroup? = groups.groupOfSite(host)

    /**
     * Moves a site (typed as a domain or pasted as a URL) into [groupName], or
     * out of every group if null. Returns false if it isn't a valid domain.
     */
    fun assignSiteToGroup(site: String, groupName: String?): Boolean {
        val host = SiteTracker.hostOf(site) ?: return false
        groups = groups.withSite(siteDomain(host), groupName)
        return true
    }

    fun removeSite(groupName: String, domain: String) {
        groups = groups.withoutSite(groupName, domain)
    }

    fun canChangeGroupLimit(group: AppGroup): Boolean =
        group.canChangeLimit(System.currentTimeMillis())

    fun daysUntilChangeable(group: AppGroup): Int =
        group.daysUntilChangeable(System.currentTimeMillis())

    /** Sets a group's budget, honoring the once-a-week cooldown. */
    fun setGroupLimit(name: String, minutes: Int) {
        groups = groups.withLimit(name, minutes, System.currentTimeMillis())
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

    /** Today's on-screen time per host, in ms. */
    fun sitesToday(): Map<String, Long> {
        val prefix = "site-${todaySuffix()}-"
        return sp.all.entries
            .filter { it.key.startsWith(prefix) && it.value is Long }
            .associate { it.key.removePrefix(prefix) to it.value as Long }
    }

    /**
     * The groups as last agreed with the server (sync): what tells an edit
     * made here from one made elsewhere. Null before the first sync.
     */
    var groupsSynced: SyncedGroups?
        get() {
            val raw = sp.getString("groups_synced", null) ?: return null
            return SyncedGroups(parseGroups(raw), sp.getLong("groups_synced_at", 0L))
        }
        set(value) {
            sp.edit().apply {
                if (value == null) remove("groups_synced").remove("groups_synced_at")
                else putString("groups_synced", value.groups.toGroupsJson()).putLong("groups_synced_at", value.updatedAt)
            }.apply()
        }

    /**
     * The considered apps with their friction, as sync shares them (app id to
     * Friction ordinal; desktop apps are "hypr:<class>"). Setting it replaces
     * both the considered set and each app's friction.
     */
    var consideredWithFriction: Map<String, Int>
        get() = considered.associateWith { frictionFor(it).ordinal }
        set(value) {
            considered = value.keys
            sp.edit().apply { value.forEach { (app, f) -> putInt("friction-$app", f) } }.apply()
        }

    /** The considered apps as last agreed with the server (sync); null before the first. */
    var consideredSynced: SyncedConsidered?
        get() {
            val raw = sp.getString("considered_synced", null) ?: return null
            return SyncedConsidered(parseConsideredJson(raw), sp.getLong("considered_synced_at", 0L))
        }
        set(value) {
            sp.edit().apply {
                if (value == null) remove("considered_synced").remove("considered_synced_at")
                else putString("considered_synced", value.apps.toConsideredJson()).putLong("considered_synced_at", value.updatedAt)
            }.apply()
        }

    /** Today's on-screen time for [domain] and its subdomains, in ms. */
    fun siteTimeToday(domain: String): Long {
        val prefix = "site-${todaySuffix()}-"
        return sp.all.entries.sumOf { (key, value) ->
            val host = key.removePrefix(prefix)
            val matches = key.startsWith(prefix) && siteCovers(domain, host)
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
