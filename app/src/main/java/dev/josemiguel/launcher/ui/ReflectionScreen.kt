package dev.josemiguel.launcher.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.josemiguel.launcher.DayCounts
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

private val DAY = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)

/**
 * Two weeks of the launcher's own metric — intentional opens and
 * paused-and-closed moments. Reflection, not a dashboard: no minutes, no
 * goals, no streaks to break.
 */
@Composable
fun ReflectionScreen(history: List<DayCounts>, strictStays: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Text("last two weeks", style = LauncherTheme.title)
        Spacer(Modifier.height(24.dp))

        history.forEach { day ->
            Row(Modifier.padding(vertical = 5.dp)) {
                Text(
                    day.date.format(DAY).lowercase(),
                    style = LauncherTheme.caption,
                    modifier = Modifier.width(64.dp),
                )
                Column {
                    Text(
                        "█".repeat(min(day.intentional, 24)) +
                            (if (day.intentional > 0) " " else "") +
                            "${day.intentional} open",
                        style = LauncherTheme.caption.copy(color = LauncherTheme.ink),
                    )
                    if (day.pausedClosed > 0) {
                        Text(
                            "░".repeat(min(day.pausedClosed, 24)) +
                                " ${day.pausedClosed} closed at the pause",
                            style = LauncherTheme.caption,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        val thisWeek = history.take(7)
        val lastWeek = history.drop(7).take(7)
        Text(
            "this week ${thisWeek.sumOf { it.intentional }} open · " +
                "${thisWeek.sumOf { it.pausedClosed }} closed",
            style = LauncherTheme.caption,
        )
        Text(
            "last week ${lastWeek.sumOf { it.intentional }} open · " +
                "${lastWeek.sumOf { it.pausedClosed }} closed",
            style = LauncherTheme.caption.copy(color = LauncherTheme.faint),
        )
        if (strictStays > 0) {
            Text(
                "stood at the door and stayed · $strictStays",
                style = LauncherTheme.caption,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
