package dev.josemiguel.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService

/**
 * Alarms are cleared on reboot. Reschedule the next drop, and deliver any
 * batch that came due while the phone was off. The site-budget VPN restarts
 * too, if it was on and its consent still stands.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        DropNotifier.postSummary(context, HeldNotifications.ready(context).size)
        DropScheduler.scheduleNext(context)
        if (Prefs(context).siteVpnEnabled && VpnService.prepare(context) == null) {
            SiteBlockVpnService.start(context)
        }
    }
}
