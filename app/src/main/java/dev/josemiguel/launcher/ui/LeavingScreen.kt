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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private const val DOOR_SECONDS = 30

/**
 * The door out of strict mode. No walls and no tricks — instead, the user's
 * own written reasons shown back to them in large type, their real progress,
 * and thirty seconds of breathing before the exit unlocks. People are
 * persuaded by their own words; both buttons are honest.
 */
@Composable
fun LeavingScreen(
    reasons: String,
    daysOn: Int,
    pausedClosedSince: Int,
    staysCount: Int,
    onStay: () -> Unit,
    onTurnOff: () -> Unit,
) {
    var remaining by remember { mutableIntStateOf(DOOR_SECONDS) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }

    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000), RepeatMode.Reverse),
        label = "breath",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(36.dp))
        Box(
            Modifier
                .size(160.dp)
                .graphicsLayer(scaleX = breath, scaleY = breath)
                .background(LauncherTheme.faint, CircleShape)
        )
        Spacer(Modifier.height(40.dp))
        Text("breathe", style = LauncherTheme.title.copy(letterSpacing = 4.sp))
        Spacer(Modifier.height(6.dp))
        Text(
            "you're at the door",
            style = LauncherTheme.caption,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(36.dp))
        Text(
            "you wrote this to yourself:",
            style = LauncherTheme.caption,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            if (reasons.isBlank()) "protect my attention. that's why this is here."
            else reasons.trim(),
            style = LauncherTheme.item,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(36.dp))
        if (daysOn > 0) {
            Text(
                "$daysOn ${if (daysOn == 1) "day" else "days"} behind this door",
                style = LauncherTheme.body.copy(color = LauncherTheme.muted),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
        if (pausedClosedSince > 0) {
            Text(
                "$pausedClosedSince times you chose to close instead of scroll",
                style = LauncherTheme.body.copy(color = LauncherTheme.muted),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }
        if (staysCount > 0) {
            Text(
                "$staysCount ${if (staysCount == 1) "time" else "times"} you stood here and stayed",
                style = LauncherTheme.body.copy(color = LauncherTheme.muted),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 3.dp),
            )
        }

        Spacer(Modifier.height(40.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                "keep going",
                style = LauncherTheme.item,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onStay)
                    .padding(vertical = 16.dp),
            )
            Text(
                if (remaining > 0) "turn off in ${remaining}s" else "turn off",
                style = LauncherTheme.item.copy(
                    color = if (remaining > 0) LauncherTheme.faint else LauncherTheme.ink
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = remaining == 0, onClick = onTurnOff)
                    .padding(vertical = 16.dp),
            )
        }
        Text(
            "strict mode can come back anytime. leaving is allowed.",
            style = LauncherTheme.caption.copy(color = LauncherTheme.faint),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
