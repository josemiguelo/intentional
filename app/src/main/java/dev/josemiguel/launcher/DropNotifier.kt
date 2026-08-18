package dev.josemiguel.launcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Posts the batched-notification digest at a drop time — a single summary
 * ("5 notifications waiting") with sound, whose tap opens the launcher's tray.
 * This is the active half of batching: predictable scheduled delivery, which
 * the evidence favors over both constant pings and total silence.
 */
object DropNotifier {

    private const val CHANNEL = "batched_delivery"
    private const val NOTIF_ID = 1001

    private fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Batched notifications",
                    NotificationManager.IMPORTANCE_HIGH,
                )
            )
        }
    }

    fun postSummary(context: Context, count: Int) {
        if (count <= 0) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                if (count == 1) "1 notification waiting" else "$count notifications waiting"
            )
            .setContentText("tap to read your batch")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification)
    }
}
