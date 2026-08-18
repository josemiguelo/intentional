package dev.josemiguel.launcher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Schedules the next batched-delivery drop as a single exact alarm and chains
 * the following one when it fires (repeating alarms are inexact on modern
 * Android). Reschedule on app start, after editing drop times, and on boot.
 */
object DropScheduler {

    fun scheduleNext(context: Context) {
        val drops = Prefs(context).dropTimes
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pending = pendingIntent(context)
        if (drops.isEmpty()) {
            alarmManager.cancel(pending)
            return
        }

        val next = nextDropMillis(drops)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Falls back to inexact (may drift under Doze) until the user grants
            // the exact-alarm permission.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pending)
        }
    }

    fun exactAlarmsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun pendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, DropAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun nextDropMillis(drops: List<java.time.LocalTime>): Long {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        val sorted = drops.sorted()
        val todayNext = sorted
            .map { now.toLocalDate().atTime(it).atZone(zone) }
            .firstOrNull { it.isAfter(now) }
        val target = todayNext
            ?: now.toLocalDate().plusDays(1).atTime(sorted.first()).atZone(zone)
        return target.toInstant().toEpochMilli()
    }
}
