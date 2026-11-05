package dev.josemiguel.launcher

import android.content.Context
import android.content.pm.PackageManager

/**
 * Android TV / Google TV. There the remote is the only input (no swipes, no
 * touch long-press), and Home always opens the stock launcher whatever the
 * default home app is, so the guard sends it back here (see GuardService).
 */
object Tv {

    /** The stock TV launchers the Home button opens: Google TV's, Android TV's, the old leanback one. */
    val STOCK_LAUNCHERS = setOf(
        "com.google.android.apps.tv.launcherx",
        "com.google.android.tvlauncher",
        "com.google.android.leanbacklauncher",
    )

    fun isTv(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}
