package dev.josemiguel.launcher.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * The colors the launcher draws with, from Tokyo Night (folke/tokyonight.nvim,
 * extras/lua): its base shades plus one accent, the blue Omarchy uses for
 * headings and outlines. No icons, nothing else colored.
 */
@Immutable
data class Palette(
    val background: Color,
    val panel: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    /** Headings and the date: Tokyo Night's blue, the one accent (Omarchy's). */
    val accent: Color,
    /** Panel outlines. */
    val border: Color,
) {
    companion object {
        /** Tokyo Night "night": bg, bg_highlight, fg, dark5, terminal_black; blue, terminal_black. */
        val Night = Palette(
            background = Color(0xFF1A1B26),
            panel = Color(0xFF292E42),
            ink = Color(0xFFC0CAF5),
            muted = Color(0xFF737AA2),
            faint = Color(0xFF414868),
            accent = Color(0xFF7AA2F7),
            border = Color(0xFF414868),
        )

        /** Tokyo Night "day", the same roles; blue, fg_gutter. */
        val Day = Palette(
            background = Color(0xFFE1E2E7),
            panel = Color(0xFFC4C8DA),
            ink = Color(0xFF3760BF),
            muted = Color(0xFF68709A),
            faint = Color(0xFFA1A6C5),
            accent = Color(0xFF2E7DE9),
            border = Color(0xFFA8AECB),
        )

        /** The palette for the system's current dark/light setting, outside Compose. */
        fun forSystem(context: Context): Palette {
            val night = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            return if (night) Night else Day
        }
    }
}

private val LocalPalette = staticCompositionLocalOf { Palette.Night }

/**
 * Follows the system's dark/light setting; there is no in-app switch. On a
 * [tv], clickable rows show focus with [FocusIndication].
 */
@Composable
fun LauncherPalette(tv: Boolean = false, content: @Composable () -> Unit) {
    val palette = if (isSystemInDarkTheme()) Palette.Night else Palette.Day
    if (tv) {
        CompositionLocalProvider(
            LocalPalette provides palette,
            LocalIndication provides FocusIndication(palette.panel, palette.accent),
            content = content,
        )
    } else {
        CompositionLocalProvider(LocalPalette provides palette, content = content)
    }
}

/** Omarchy-style: monospace, one accent, framed panels over the wallpaper (see [panel]). */
object LauncherTheme {
    val background: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.background
    val panel: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.panel
    val ink: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
    val muted: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.muted
    val faint: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.faint
    val accent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.accent
    val border: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.border

    /** Home hero only. */
    val clock: TextStyle @Composable @ReadOnlyComposable get() = mono(52.sp, 58.sp, ink)
    /** Screen titles and single anchor words. */
    val title: TextStyle @Composable @ReadOnlyComposable get() = mono(28.sp, 36.sp, accent)
    /** Tappable list rows, buttons, pause stats. */
    val item: TextStyle @Composable @ReadOnlyComposable get() = mono(22.sp, 30.sp, ink)
    /** Prose, questions, settings rows. */
    val body: TextStyle @Composable @ReadOnlyComposable get() = mono(16.sp, 24.sp, ink)
    /** Metadata only — never interactive. */
    val caption: TextStyle @Composable @ReadOnlyComposable get() = mono(13.sp, 19.sp, muted)

    private fun mono(size: TextUnit, lineHeight: TextUnit, color: Color) =
        TextStyle(fontFamily = FontFamily.Monospace, fontSize = size, lineHeight = lineHeight, color = color)
}
