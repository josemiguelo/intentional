package dev.josemiguel.launcher.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The wallpaper behind home, the drawer and settings, one per theme, shown at
 * full strength: the panels on top carry their own glass. The images aren't
 * bundled — they're third-party art (Omarchy's theme
 * backgrounds), pushed into the app's private files per phone (see
 * docs/SETUP.md). Without a file the screen stays plain.
 */
@Composable
fun Wallpaper() {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val image by produceState<ImageBitmap?>(null, dark) {
        value = withContext(Dispatchers.IO) { load(context, dark) }
    }
    val bitmap = image ?: return
    Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
}

/** `files/wallpaper-dark.webp` or `files/wallpaper-light.webp`, or null when absent. */
private fun load(context: Context, dark: Boolean): ImageBitmap? {
    val file = File(context.filesDir, if (dark) "wallpaper-dark.webp" else "wallpaper-light.webp")
    if (!file.exists()) return null
    return BitmapFactory.decodeFile(file.path)?.asImageBitmap()
}
