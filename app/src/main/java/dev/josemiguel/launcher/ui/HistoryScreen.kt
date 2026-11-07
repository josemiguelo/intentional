package dev.josemiguel.launcher.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.josemiguel.intentional.core.GroupMinutes
import dev.josemiguel.intentional.core.History
import dev.josemiguel.intentional.core.formatMinutes
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.datetime.toJavaLocalDate

private val DAY = DateTimeFormatter.ofPattern("EEE d", Locale.ENGLISH)
private val WEEK = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/**
 * Each group's minutes a day, across every device: the recent days one by
 * one, the weeks before in sums. Unlike the reflection screen this is
 * minutes, so it sits in settings, beside the groups it's about. Rows are
 * focusable so a TV remote can scroll through them.
 */
@Composable
fun HistoryScreen(history: History, synced: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text("history", style = LauncherTheme.title)
        Spacer(Modifier.height(8.dp))
        Text(
            if (synced) "each group's time a day, across every device; kept for a year. " +
                "\"over\" is against today's limits."
            else "each group's time a day. turn on sync to see the days before today.",
            style = LauncherTheme.caption.copy(color = LauncherTheme.faint),
        )
        Spacer(Modifier.height(16.dp))

        if (history.days.isEmpty() && history.weeks.isEmpty()) {
            Text("nothing yet.", style = LauncherTheme.body.copy(color = LauncherTheme.muted))
        }

        history.days.forEach { day ->
            Entry(
                label = day.date().format(DAY).lowercase(),
                groups = day.groups,
                footer = "${day.opened} open · ${day.closed} closed at the pause",
            )
        }

        if (history.weeks.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("weeks", style = LauncherTheme.body.copy(color = LauncherTheme.accent))
            Spacer(Modifier.height(8.dp))
            history.weeks.forEach { week ->
                Entry(
                    label = week.start.toJavaLocalDate().format(WEEK).lowercase(),
                    groups = week.groups,
                    footer = if (week.days < 7) "${week.days} of 7 days recorded" else null,
                )
            }
        }
    }
}

private fun dev.josemiguel.intentional.core.HistoryDay.date() = day.toJavaLocalDate()

@Composable
private fun Entry(label: String, groups: List<GroupMinutes>, footer: String?) {
    Row(
        Modifier
            .fillMaxWidth()
            .focusable()
            .padding(vertical = 6.dp)
    ) {
        Text(label, style = LauncherTheme.caption, modifier = Modifier.width(72.dp))
        Column {
            if (groups.isEmpty()) Text("—", style = LauncherTheme.caption.copy(color = LauncherTheme.faint))
            groups.forEach { g ->
                Text(
                    "${g.group.lowercase()} ${formatMinutes(g.minutes)}" + if (g.over) " · over" else "",
                    style = LauncherTheme.caption.copy(color = if (g.over) LauncherTheme.accent else LauncherTheme.ink),
                )
            }
            if (footer != null) Text(footer, style = LauncherTheme.caption.copy(color = LauncherTheme.faint))
        }
    }
}
