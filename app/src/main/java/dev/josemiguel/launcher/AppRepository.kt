package dev.josemiguel.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo

data class AppEntry(
    val label: String,
    val packageName: String,
    val className: String,
)

object AppRepository {

    /**
     * Every app's launcher activities. On a TV the leanback ones come first and
     * win: TV apps (YouTube's among them) often declare only that category, and
     * one that has both opens its TV interface from it.
     */
    fun launchable(context: Context): List<ResolveInfo> {
        val categories =
            if (Tv.isTv(context)) listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
            else listOf(Intent.CATEGORY_LAUNCHER)
        val seen = mutableSetOf<String>()
        return categories.flatMap { category ->
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            context.packageManager.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName !in seen }
                .also { found -> seen += found.map { it.activityInfo.packageName } }
        }
    }

    fun loadApps(context: Context): List<AppEntry> {
        return launchable(context)
            .asSequence()
            .map {
                AppEntry(
                    label = it.loadLabel(context.packageManager).toString(),
                    packageName = it.activityInfo.packageName,
                    className = it.activityInfo.name,
                )
            }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName to it.className }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun launch(context: Context, app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(app.packageName, app.className)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
    }
}
