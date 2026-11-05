package dev.josemiguel.launcher

import android.content.Context
import android.content.pm.PackageManager

/**
 * Android TV / Google TV, where Intentional is an app beside the stock
 * launcher, not the launcher: the guard enforces budgets and pauses
 * considered apps however they're opened, and the app itself opens on
 * settings. The remote is the only input there.
 */
object Tv {
    fun isTv(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}
