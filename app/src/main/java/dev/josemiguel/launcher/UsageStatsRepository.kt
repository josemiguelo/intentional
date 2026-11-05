package dev.josemiguel.launcher

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import dev.josemiguel.intentional.core.AppGroup
import dev.josemiguel.intentional.core.usedMs
import java.time.LocalDate
import java.time.ZoneId

data class AppUsageToday(
    val opensToday: Int,
    val opensLastHour: Int,
    val minutesToday: Long,
)

/**
 * Today's usage for a single app, computed from the raw event stream so an
 * "open" means the app actually came to the foreground from somewhere else —
 * not every internal activity change.
 */
object UsageStatsRepository {

    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun statsFor(context: Context, packageName: String): AppUsageToday {
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val startOfDay = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val events = usageStatsManager.queryEvents(startOfDay, now)
        val event = UsageEvents.Event()
        var foregroundPkg: String? = null
        var opens = 0
        var opensLastHour = 0
        var sessionStart = 0L
        var foregroundMs = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue

            if (event.packageName == packageName && foregroundPkg != packageName) {
                opens++
                if (event.timeStamp >= now - 3_600_000) opensLastHour++
                sessionStart = event.timeStamp
            }
            if (event.packageName != packageName && foregroundPkg == packageName && sessionStart > 0) {
                foregroundMs += event.timeStamp - sessionStart
            }
            foregroundPkg = event.packageName
        }
        if (foregroundPkg == packageName && sessionStart > 0) {
            foregroundMs += now - sessionStart
        }

        return AppUsageToday(opens, opensLastHour, foregroundMs / 60_000)
    }

    /**
     * A group's whole budget use today: its apps' foreground time plus its
     * sites' on-screen time, here and (with sync) on the other devices.
     */
    fun groupUsedMinutes(context: Context, group: AppGroup): Long =
        groupMinutesToday(context, group.packages) + groupSiteMinutes(context, group) +
            SyncRunner.othersToday()?.let(group::usedMs)?.div(60_000).orZero()

    private fun Long?.orZero() = this ?: 0L

    /** On-screen minutes today across a group's sites (subdomains included). */
    fun groupSiteMinutes(context: Context, group: AppGroup): Long {
        val prefs = Prefs(context)
        return group.sites.sumOf(prefs::siteTimeToday) / 60_000
    }

    /**
     * Total foreground minutes today across all [packages] — the shared usage
     * of an app group counted against its budget.
     */
    fun groupMinutesToday(context: Context, packages: Set<String>): Long =
        foregroundMsToday(context, packages).values.sum() / 60_000

    /** Each of [packages]' foreground time today, in ms, in a single pass over the events. */
    fun foregroundMsToday(context: Context, packages: Set<String>): Map<String, Long> {
        if (packages.isEmpty()) return emptyMap()
        val usageStatsManager = context.getSystemService(UsageStatsManager::class.java)
        val now = System.currentTimeMillis()
        val startOfDay = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        val events = usageStatsManager.queryEvents(startOfDay, now)
        val event = UsageEvents.Event()
        var foregroundPkg: String? = null
        var sessionStart = 0L
        val foregroundMs = mutableMapOf<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != UsageEvents.Event.ACTIVITY_RESUMED) continue

            val pkg = foregroundPkg
            if (pkg in packages && sessionStart > 0 && event.packageName != pkg) {
                foregroundMs[pkg!!] = (foregroundMs[pkg] ?: 0L) + event.timeStamp - sessionStart
            }
            if (event.packageName != foregroundPkg) {
                sessionStart = event.timeStamp
            }
            foregroundPkg = event.packageName
        }
        val pkg = foregroundPkg
        if (pkg in packages && sessionStart > 0) {
            foregroundMs[pkg!!] = (foregroundMs[pkg] ?: 0L) + now - sessionStart
        }

        return foregroundMs
    }
}
