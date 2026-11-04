package dev.josemiguel.launcher.ui

import android.content.Context
import android.content.res.Configuration
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
 * The five shades the launcher draws with. Taken from Tokyo Night's base
 * colors only (folke/tokyonight.nvim, extras/lua) — none of its accents, so
 * nothing on screen asks for a glance.
 */
@Immutable
data class Palette(
    val background: Color,
    val panel: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    /** Opacity of the background laid over the wallpaper on home, so text stays readable. */
    val homeVeil: Float,
    /** The same over the app drawer: nearly opaque, the wallpaper only hinted at. */
    val drawerVeil: Float,
) {
    companion object {
        /** Tokyo Night "night": bg, bg_highlight, fg, dark5, terminal_black. */
        val Night = Palette(
            background = Color(0xFF1A1B26),
            panel = Color(0xFF292E42),
            ink = Color(0xFFC0CAF5),
            muted = Color(0xFF737AA2),
            faint = Color(0xFF414868),
            homeVeil = 0.72f,
            drawerVeil = 0.88f,
        )

        /** Tokyo Night "day", the same roles. */
        val Day = Palette(
            background = Color(0xFFE1E2E7),
            panel = Color(0xFFC4C8DA),
            ink = Color(0xFF3760BF),
            muted = Color(0xFF68709A),
            faint = Color(0xFFA1A6C5),
            homeVeil = 0.35f,
            drawerVeil = 0.85f,
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

/** Follows the system's dark/light setting; there is no in-app switch. */
@Composable
fun LauncherPalette(content: @Composable () -> Unit) {
    val palette = if (isSystemInDarkTheme()) Palette.Night else Palette.Day
    CompositionLocalProvider(LocalPalette provides palette, content = content)
}

/** Deliberately monochrome: no accent, no icons, nothing that asks for a glance. */
object LauncherTheme {
    val background: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.background
    val panel: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.panel
    val ink: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
    val muted: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.muted
    val faint: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.faint
    val homeVeil: Float @Composable @ReadOnlyComposable get() = LocalPalette.current.homeVeil
    val drawerVeil: Float @Composable @ReadOnlyComposable get() = LocalPalette.current.drawerVeil

    /** Home hero only. */
    val clock: TextStyle @Composable @ReadOnlyComposable get() = mono(52.sp, 58.sp, ink)
    /** Screen titles and single anchor words. */
    val title: TextStyle @Composable @ReadOnlyComposable get() = mono(28.sp, 36.sp, ink)
    /** Tappable list rows, buttons, pause stats. */
    val item: TextStyle @Composable @ReadOnlyComposable get() = mono(22.sp, 30.sp, ink)
    /** Prose, questions, settings rows. */
    val body: TextStyle @Composable @ReadOnlyComposable get() = mono(16.sp, 24.sp, ink)
    /** Metadata only — never interactive. */
    val caption: TextStyle @Composable @ReadOnlyComposable get() = mono(13.sp, 19.sp, muted)

    private fun mono(size: TextUnit, lineHeight: TextUnit, color: Color) =
        TextStyle(fontFamily = FontFamily.Monospace, fontSize = size, lineHeight = lineHeight, color = color)
}
