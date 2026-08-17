package dev.josemiguel.launcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Intercepts notifications from considered apps: cancels them on arrival and
 * queues them for batched delivery. Everything else rings through untouched —
 * batching beats blackout (total silence backfires into FOMO).
 */
class NotifListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.isOngoing) return
        if (sbn.packageName == packageName) return
        if (sbn.packageName !in Prefs(this).batched) return

        val isGroupSummary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        if (!isGroupSummary) {
            val extras = sbn.notification.extras
            HeldNotifications.add(
                this,
                HeldNotifications.Held(
                    packageName = sbn.packageName,
                    title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: "",
                    text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "",
                    time = System.currentTimeMillis(),
                ),
            )
        }
        cancelNotification(sbn.key)
    }
}
