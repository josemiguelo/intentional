package dev.josemiguel.launcher

import android.app.admin.DeviceAdminReceiver

/**
 * Deliberately empty device admin: it requests no policies. Its only purpose
 * is that Android refuses to uninstall an app whose admin is active — the
 * commitment-device half of strict mode. The app can remove its own admin via
 * DevicePolicyManager.removeActiveAdmin(), which settings gates behind strict
 * mode being off (i.e., behind the leaving flow).
 *
 * Emergency escape:
 * `adb shell dpm remove-active-admin dev.josemiguel.launcher/.UninstallGuardAdmin`
 */
class UninstallGuardAdmin : DeviceAdminReceiver()
