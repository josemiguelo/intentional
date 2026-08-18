package dev.josemiguel.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires at each drop time: posts the digest if anything is ready, then chains the next drop. */
class DropAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ready = HeldNotifications.ready(context)
        DropNotifier.postSummary(context, ready.size)
        DropScheduler.scheduleNext(context)
    }
}
