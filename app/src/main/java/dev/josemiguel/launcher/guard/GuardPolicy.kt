package dev.josemiguel.launcher.guard

import android.os.Build

/**
 * Per-OEM detection of the system screen that switches the default launcher.
 * Only the HOME-app picker itself should match — never the general "Default
 * apps" list, so every other default (browser, SMS, …) stays changeable.
 *
 * To support a new OEM (e.g. Pixel-specific quirks), add an implementation
 * and select it in [forDevice].
 */
interface GuardPolicy {

    /**
     * True if this window is the screen for choosing the default HOME app.
     * [screenText] is the lowercased window text from the accessibility event.
     */
    fun isHomePickerScreen(packageName: String, className: String, screenText: String): Boolean

    companion object {
        fun forDevice(): GuardPolicy =
            if (Build.MANUFACTURER.equals("samsung", ignoreCase = true)) OneUiGuardPolicy
            else DefaultGuardPolicy
    }
}
