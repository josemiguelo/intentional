package dev.josemiguel.launcher.guard

import android.os.Build

/** The window the guard is deciding about — its package, activity class, and lowercased text. */
data class ScreenInfo(
    val packageName: String,
    val className: String,
    val text: String,
)

/**
 * Per-OEM detection of the system screens the guard bounces you away from while
 * strict mode is on. Each method identifies one such screen so the matching can
 * be tuned per device; [DefaultGuardPolicy] carries generic AOSP matches, and
 * per-OEM policies override only what differs.
 *
 * To support a new OEM (e.g. Pixel quirks), add a policy and select it in
 * [forDevice].
 */
interface GuardPolicy {

    /** The screen for choosing the default HOME app — never the general "Default apps" list. */
    fun isHomePicker(screen: ScreenInfo): Boolean

    /** This app's own page in Accessibility settings (which could disable the guard). */
    fun isOwnAccessibilityScreen(screen: ScreenInfo): Boolean

    /** The device-admin add/deactivate screen (which could uninstall the app). */
    fun isDeviceAdminScreen(screen: ScreenInfo): Boolean

    /** The Private DNS chooser (which could change/disable DNS-based filtering). */
    fun isPrivateDnsScreen(screen: ScreenInfo): Boolean

    companion object {
        fun forDevice(): GuardPolicy =
            if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) OneUiGuardPolicy
            else DefaultGuardPolicy
    }
}
