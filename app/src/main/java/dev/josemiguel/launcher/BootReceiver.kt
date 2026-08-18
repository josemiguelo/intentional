package dev.josemiguel.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Alarms are cleared on reboot. Reschedule the next drop, and deliver any
 * batch that came due while the phone was off.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        DropNotifier.postSummary(context, HeldNotifications.ready(context).size)
        DropScheduler.scheduleNext(context)
    }
}
