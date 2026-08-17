package dev.josemiguel.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Store for notifications intercepted from considered apps. A held
 * notification becomes visible only at the first drop time after it arrived —
 * predictable batched delivery, which the evidence favors over both constant
 * pings and total silence. Drop times live in [Prefs.dropTimes].
 */
object HeldNotifications {

    data class Held(
        val packageName: String,
        val title: String,
        val text: String,
        val time: Long,
    )

    private fun sp(context: Context) =
        context.getSharedPreferences("held_notifications", Context.MODE_PRIVATE)

    @Synchronized
    fun add(context: Context, held: Held) {
        save(context, all(context) + held)
    }

    fun all(context: Context): List<Held> {
        val raw = sp(context).getString("held", "[]")!!
        val array = JSONArray(raw)
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            Held(
                packageName = o.getString("pkg"),
                title = o.getString("title"),
                text = o.getString("text"),
                time = o.getLong("time"),
            )
        }
    }

    private fun save(context: Context, list: List<Held>) {
        val array = JSONArray()
        list.takeLast(200).forEach {
            array.put(
                JSONObject()
                    .put("pkg", it.packageName)
                    .put("title", it.title)
                    .put("text", it.text)
                    .put("time", it.time)
            )
        }
        sp(context).edit().putString("held", array.toString()).apply()
    }

    /** The first drop moment strictly after [timeMillis]. */
    fun nextDropAfter(context: Context, timeMillis: Long): Long {
        val drops = Prefs(context).dropTimes.ifEmpty { listOf(LocalTime.of(9, 0)) }
        val zone = ZoneId.systemDefault()
        val arrived = LocalDateTime.ofInstant(Instant.ofEpochMilli(timeMillis), zone)
        val sameDay = drops.firstOrNull { it.isAfter(arrived.toLocalTime()) }
        val drop =
            if (sameDay != null) LocalDateTime.of(arrived.toLocalDate(), sameDay)
            else LocalDateTime.of(arrived.toLocalDate().plusDays(1), drops.first())
        return drop.atZone(zone).toInstant().toEpochMilli()
    }

    fun ready(context: Context, now: Long = System.currentTimeMillis()): List<Held> =
        all(context).filter { nextDropAfter(context, it.time) <= now }

    @Synchronized
    fun clearReady(context: Context, now: Long = System.currentTimeMillis()) {
        save(context, all(context).filterNot { nextDropAfter(context, it.time) <= now })
    }

    @Synchronized
    fun clearAll(context: Context) {
        save(context, emptyList())
    }
}
