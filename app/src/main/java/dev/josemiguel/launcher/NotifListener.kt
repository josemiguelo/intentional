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

        // Never intercept media, call, alarm, or navigation notifications.
        // Cancelling a media notification fires its deleteIntent, which
        // players (YouTube included) treat as "user dismissed playback" and
        // stop — even when the notification isn't flagged ongoing yet.
        val notification = sbn.notification
        if (notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return
        if (notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return
        when (notification.category) {
            Notification.CATEGORY_TRANSPORT,
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_ALARM,
            Notification.CATEGORY_NAVIGATION -> return
            else -> Unit
        }

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
