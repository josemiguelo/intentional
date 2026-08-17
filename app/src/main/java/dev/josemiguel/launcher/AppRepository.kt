package dev.josemiguel.launcher

import android.content.Context
import android.content.Intent

data class AppEntry(
    val label: String,
    val packageName: String,
    val className: String,
)

object AppRepository {

    fun loadApps(context: Context): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, 0)
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
