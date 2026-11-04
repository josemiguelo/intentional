package dev.josemiguel.launcher.guard

/**
 * Stock-ish Android (AOSP and most OEMs). Generic matches that other policies
 * reuse and selectively override.
 */
object DefaultGuardPolicy : GuardPolicy {

    // The modern role picker is permissioncontroller's DefaultAppActivity —
    // shared with the browser/SMS pickers, so the text must mention home — and
    // older Android used a dedicated HomeSettings activity.
    override fun isHomePicker(screen: ScreenInfo): Boolean {
        val rolePicker = screen.className.contains("DefaultAppActivity", ignoreCase = true) &&
            screen.text.contains("home")
        val legacyHomeSettings = screen.className.contains("HomeSettings", ignoreCase = true)
        return rolePicker || legacyHomeSettings
    }

    override fun isOwnAccessibilityScreen(screen: ScreenInfo): Boolean =
        screen.className.contains("Accessibility", ignoreCase = true) &&
            screen.text.contains("intentional")

    // The activity's own state event can lag its dialog by ~500ms (One UI), and
    // the dialog's event arrives as a generic AlertDialog — so the title text is
    // what catches it the moment it shows.
    override fun isDeviceAdminScreen(screen: ScreenInfo): Boolean =
        screen.className.contains("DeviceAdmin", ignoreCase = true) ||
            screen.text.contains("device admin app")

    override fun isPrivateDnsScreen(screen: ScreenInfo): Boolean =
        screen.text.contains("private dns")
}
