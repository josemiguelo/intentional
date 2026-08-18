package dev.josemiguel.launcher.guard

/**
 * Stock-ish Android (AOSP and most OEMs). The modern role picker is
 * permissioncontroller's DefaultAppActivity — shared with browser/SMS
 * pickers, so the window text must mention home — and older Android versions
 * used a dedicated Settings HomeSettings activity.
 */
object DefaultGuardPolicy : GuardPolicy {

    override fun isHomePickerScreen(
        packageName: String,
        className: String,
        screenText: String,
    ): Boolean {
        val rolePicker = className.contains("DefaultAppActivity", ignoreCase = true) &&
            screenText.contains("home")
        val legacyHomeSettings = className.contains("HomeSettings", ignoreCase = true)
        return rolePicker || legacyHomeSettings
    }
}
