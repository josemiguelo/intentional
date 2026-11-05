package dev.josemiguel.launcher

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Files handed to the app over adb, without run-as (which release builds
 * don't allow): pushed to the app's external folder
 * (/sdcard/Android/data/dev.josemiguel.launcher/files/, writable by adb and,
 * on Android 11+, readable by no other app), then moved here into private
 * storage and deleted there. The sync config is kept readable by the app
 * alone. Run when the guard connects and when the launcher comes up.
 */
object DropIn {

    private const val TAG = "DropIn"

    /** What can be dropped in: the sync config and the per-theme wallpapers. */
    private val NAMES = listOf("sync.json", "wallpaper-dark.webp", "wallpaper-light.webp")

    /** Moves whatever has been dropped in; true if anything was. */
    fun import(context: Context): Boolean {
        val outside = context.getExternalFilesDir(null) ?: return false
        var any = false
        for (name in NAMES) {
            val from = File(outside, name)
            if (!from.isFile) continue
            val to = File(context.filesDir, name)
            runCatching {
                from.copyTo(to, overwrite = true)
                to.setReadable(false, false)
                to.setWritable(false, false)
                to.setReadable(true, true)
                to.setWritable(true, true)
                from.delete()
                any = true
                Log.i(TAG, "imported $name")
            }.onFailure { Log.w(TAG, "couldn't import $name", it) }
        }
        return any
    }
}
