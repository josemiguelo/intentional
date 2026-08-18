package dev.josemiguel.launcher.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/** Deliberately monochrome: no accent, no icons, nothing that asks for a glance. */
object LauncherTheme {
    val background = Color(0xFF000000)
    val panel = Color(0xFF161514)
    val ink = Color(0xFFE8E4DD)
    val muted = Color(0xFF8A857C)
    val faint = Color(0xFF4A463F)

    /** Home hero only. */
    val clock = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 52.sp, lineHeight = 58.sp, color = ink)
    /** Screen titles and single anchor words. */
    val title = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 28.sp, lineHeight = 36.sp, color = ink)
    /** Tappable list rows, buttons, pause stats. */
    val item = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 22.sp, lineHeight = 30.sp, color = ink)
    /** Prose, questions, settings rows. */
    val body = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp, lineHeight = 24.sp, color = ink)
    /** Metadata only — never interactive. */
    val caption = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, color = muted)
}
