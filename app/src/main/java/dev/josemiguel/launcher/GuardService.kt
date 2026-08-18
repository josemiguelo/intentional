package dev.josemiguel.launcher

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import dev.josemiguel.launcher.guard.GuardPolicy

/**
 * Strict mode's watchdog. While [Prefs.strictMode] is on, landing on the
 * screen that switches the default launcher — or the accessibility page that
 * could disable this very service — bounces straight back to the home screen.
 * Which screen counts as "the launcher switcher" is decided per-OEM by
 * [GuardPolicy]; everything else in Settings (including the general "Default
 * apps" list) stays reachable. Inert whenever strict mode is off, so the
 * sanctioned exit (the leaving flow) always works, and adb remains the
 * ultimate escape hatch.
 *
 * Hard-won platform notes (Samsung, Android 13+):
 * - Sideloaded apps need the ACCESS_RESTRICTED_SETTINGS appop allowed or the
 *   system silently drops event delivery even though the service binds:
 *   `adb shell appops set dev.josemiguel.launcher ACCESS_RESTRICTED_SETTINGS allow`
 * - Registering only typeWindowStateChanged without isAccessibilityTool got
 *   zero events on One UI; the current guard_service.xml config is the one
 *   that works. Don't narrow it without retesting.
 * - After every reinstall the service must be rebind-cycled (see README).
 */
class GuardService : AccessibilityService() {

    private val policy = GuardPolicy.forDevice()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "connected, policy=${policy.javaClass.simpleName}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val watched = pkg.contains("settings", ignoreCase = true) ||
            pkg.contains("permissioncontroller", ignoreCase = true)
        if (!watched) return
        if (!Prefs(this).strictMode) return

        val cls = event.className?.toString() ?: ""
        val screenText = event.text.joinToString(" ").lowercase()
        Log.i(TAG, "window: pkg=$pkg cls=$cls text=$screenText")

        val isHomePicker = policy.isHomePickerScreen(pkg, cls, screenText)
        val isOwnAccessibilityScreen = cls.contains("Accessibility", ignoreCase = true) &&
            screenText.contains("intentional")

        if (isHomePicker || isOwnAccessibilityScreen) {
            Log.i(TAG, "blocked: $cls")
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() = Unit

    private companion object {
        const val TAG = "GuardService"
    }
}
