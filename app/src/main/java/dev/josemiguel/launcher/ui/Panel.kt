package dev.josemiguel.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Omarchy's window look, after Omalauncher's defaults: a square frame with a
 * 2dp outline, glass-filled with the background at 88% so the wallpaper only
 * just shows through. Panels sit [PanelGap] apart and from the screen's edges,
 * like Hyprland's gaps.
 */
@Composable
fun Modifier.panel(): Modifier = this
    .background(LauncherTheme.background.copy(alpha = 0.88f))
    .border(2.dp, LauncherTheme.border)
    .padding(horizontal = 16.dp, vertical = 12.dp)

val PanelGap = 6.dp

/**
 * The same frame around a whole screen (the drawer, settings): [PanelGap] from
 * the edges, no inner padding — the screen keeps its own.
 */
@Composable
fun Modifier.screenPanel(): Modifier = this
    .padding(PanelGap)
    .fillMaxSize()
    .background(LauncherTheme.background.copy(alpha = 0.88f))
    .border(2.dp, LauncherTheme.border)
