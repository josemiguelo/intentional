package dev.josemiguel.launcher.guard

/**
 * Android shows device-admin activation and deactivation in the same Settings
 * activity, so the guard can't tell them apart by window. When the app itself
 * launches the activation flow it opens a short bypass window here; outside
 * it, any device-admin screen naming this app is treated as a deactivation
 * attempt and blocked while strict mode is on.
 */
object GuardBypass {
    @Volatile
    var adminScreenAllowedUntil: Long = 0L

    fun allowAdminScreen(durationMs: Long = 60_000) {
        adminScreenAllowedUntil = System.currentTimeMillis() + durationMs
    }

    fun adminScreenAllowed(): Boolean =
        System.currentTimeMillis() < adminScreenAllowedUntil
}
