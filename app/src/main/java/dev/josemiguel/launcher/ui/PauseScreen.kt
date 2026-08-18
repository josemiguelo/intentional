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
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.SolidColor
import dev.josemiguel.launcher.AppEntry
import dev.josemiguel.launcher.AppGroup
import dev.josemiguel.launcher.AppUsageToday
import dev.josemiguel.launcher.Friction
import dev.josemiguel.launcher.UsageStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private fun usageLines(stats: AppUsageToday): List<String> {
    if (stats.opensToday == 0) return listOf("first open today")
    return buildList {
        add("opened ${stats.opensToday}× today")
        add("${stats.minutesToday} min so far")
        if (stats.opensLastHour > 0) add("${stats.opensLastHour} in the last hour")
    }
}

/**
 * The one-sec mechanic: a breathing pause before a considered app opens.
 * "Close" is available immediately; "Open" only after the countdown, and the
 * two read with equal visual weight — friction with an honest exit ramp.
 */
@Composable
fun PauseScreen(
    app: AppEntry,
    group: AppGroup?,
    friction: Friction,
    windDown: Boolean,
    windDownEndLabel: String,
    baseSeconds: Int,
    longSeconds: Int,
    onOpen: () -> Unit,
    onClose: () -> Unit,
) {
    val blocked = friction == Friction.NIGHT_LOCK && windDown
    val frictionSeconds = when (friction) {
        Friction.PAUSE, Friction.TYPE_NAME -> baseSeconds
        Friction.LONG_PAUSE, Friction.NIGHT_LOCK -> longSeconds
    }
    val pauseSeconds = frictionSeconds * (if (windDown && !blocked) 2 else 1)
    var typed by remember { mutableStateOf("") }
    val typeSatisfied = friction != Friction.TYPE_NAME ||
        typed.trim().equals(app.label.trim(), ignoreCase = true)

    var remaining by remember { mutableIntStateOf(pauseSeconds) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000)
            remaining--
        }
    }

    val context = LocalContext.current
    val hasUsageAccess = remember { UsageStatsRepository.hasPermission(context) }
    // When the app is in a group, the meaningful number is the group's shared
    // budget remaining — not this app's isolated minutes.
    val lines by produceState<List<String>>(initialValue = emptyList()) {
        if (hasUsageAccess) {
            value = withContext(Dispatchers.IO) {
                if (group != null) {
                    val used = UsageStatsRepository.groupMinutesToday(context, group.packages)
                    val left = (group.limitMinutes - used).coerceAtLeast(0)
                    if (left <= 0) listOf("no ${group.name.lowercase()} time left today")
                    else listOf("$left min left", "in ${group.name.lowercase()} today")
                } else {
                    usageLines(UsageStatsRepository.statsFor(context, app.packageName))
                }
            }
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
        Text(
            "breathe",
            style = LauncherTheme.title.copy(letterSpacing = 4.sp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            if (blocked) "${app.label.lowercase()} is locked for the night"
            else "what do you want from ${app.label.lowercase()}?",
            style = LauncherTheme.body,
            textAlign = TextAlign.Center,
        )

        if (friction == Friction.TYPE_NAME && !blocked) {
            Spacer(Modifier.height(20.dp))
            BasicTextField(
                value = typed,
                onValueChange = { typed = it },
                singleLine = true,
                textStyle = LauncherTheme.body.copy(textAlign = TextAlign.Center),
                cursorBrush = SolidColor(LauncherTheme.ink),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "type “${app.label.lowercase()}” to open",
                style = LauncherTheme.caption,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        if (hasUsageAccess) {
            lines.forEach { line ->
                Text(
                    line,
                    style = LauncherTheme.item,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        } else {
            Text(
                "→ grant usage access to see your day",
                style = LauncherTheme.caption,
                modifier = Modifier
                    .clickable {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                    .padding(4.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        if (blocked) {
            Text(
                "close · until $windDownEndLabel",
                style = LauncherTheme.item,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClose)
                    .padding(vertical = 16.dp),
            )
        } else {
            val openEnabled = remaining == 0 && typeSatisfied
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
                        color = if (openEnabled) LauncherTheme.ink else LauncherTheme.faint
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = openEnabled, onClick = onOpen)
                        .padding(vertical = 16.dp),
                )
            }
        }
    }
}
