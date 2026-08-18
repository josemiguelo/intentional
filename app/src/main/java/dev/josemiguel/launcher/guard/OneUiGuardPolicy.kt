package dev.josemiguel.launcher.guard

/**
 * Samsung One UI, verified live (Android 16, 2026-08).
 *
 * Reuses [DefaultGuardPolicy] by delegation and overrides only what differs on
 * this phone: the home picker. The device-admin add (SecDeviceAdminAdd), the
 * accessibility page, and the Private DNS chooser (a generic AlertDialog) all
 * match the default detection here, so they're inherited — override them if a
 * future One UI build changes them.
 */
object OneUiGuardPolicy : GuardPolicy by DefaultGuardPolicy {

    // The home picker is permissioncontroller's DefaultAppActivity, but that
    // same class also serves the browser/SMS pickers — so the window text
    // "default home app" (not the class) is what narrows the match to home.
    // English-locale text; add the localized string if the language changes.
    override fun isHomePicker(screen: ScreenInfo): Boolean =
        screen.className.contains("DefaultAppActivity", ignoreCase = true) &&
            screen.text.contains("default home app")
}
