package dev.josemiguel.launcher.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.josemiguel.launcher.AppEntry
import kotlinx.coroutines.delay

private const val PAUSE_SECONDS = 8

/**
 * The one-sec mechanic: a breathing pause before a considered app opens.
 * "Close" is available immediately; "Open" only after the countdown, and the
 * two read with equal visual weight — friction with an honest exit ramp.
 */
@Composable
fun PauseScreen(
    app: AppEntry,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    var remaining by remember { mutableIntStateOf(PAUSE_SECONDS) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }

    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
        label = "breath",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Box(
            Modifier
                .size(160.dp)
                .graphicsLayer(scaleX = breath, scaleY = breath)
                .background(LauncherTheme.faint, CircleShape)
        )

        Spacer(Modifier.height(48.dp))
        Text("breathe", style = LauncherTheme.body.copy(color = LauncherTheme.muted))
        Spacer(Modifier.height(8.dp))
        Text(
            "what do you want from ${app.label.lowercase()}?",
            style = LauncherTheme.body,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "close",
                style = LauncherTheme.item,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClose)
                    .padding(vertical = 16.dp),
            )
            Text(
                if (remaining > 0) "open in ${remaining}s" else "open",
                style = LauncherTheme.item.copy(
                    color = if (remaining > 0) LauncherTheme.faint else LauncherTheme.ink
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = remaining == 0, onClick = onOpen)
                    .padding(vertical = 16.dp),
            )
        }
    }
}
