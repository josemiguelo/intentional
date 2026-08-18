package dev.josemiguel.launcher.guard

/**
 * Samsung One UI. Verified live on One UI (Android 16, 2026-08-17): the
 * home picker is com.android.permissioncontroller.role.ui.DefaultAppActivity
 * with window text "Default home app". The same DefaultAppActivity class also
 * serves the browser/SMS pickers, so the window text — not the class — is
 * what narrows the match to home. English-locale text; add the localized
 * string here if the device language changes.
 */
object OneUiGuardPolicy : GuardPolicy {

    override fun isHomePickerScreen(
        packageName: String,
        className: String,
        screenText: String,
    ): Boolean =
        className.contains("DefaultAppActivity", ignoreCase = true) &&
            screenText.contains("default home app")
}
