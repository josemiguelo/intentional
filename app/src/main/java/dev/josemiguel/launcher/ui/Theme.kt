package dev.josemiguel.launcher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** Deliberately monochrome: no accent, no icons, nothing that asks for a glance. */
object LauncherTheme {
    val background = Color(0xFF000000)
    val ink = Color(0xFFE8E4DD)
    val muted = Color(0xFF8A857C)
    val faint = Color(0xFF4A463F)

    val clock = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 44.sp, color = ink)
    val item = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp, color = ink)
    val body = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = ink)
    val caption = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = muted)
}
