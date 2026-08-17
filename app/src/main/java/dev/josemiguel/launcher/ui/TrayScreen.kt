package dev.josemiguel.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.josemiguel.launcher.HeldNotifications
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val WHEN = DateTimeFormatter.ofPattern("HH:mm")

/** The batched-notification tray: everything held since the last drop. */
@Composable
fun TrayScreen(
    items: List<HeldNotifications.Held>,
    appLabel: (String) -> String,
    onOpenApp: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Text("${items.size} held notifications", style = LauncherTheme.title)
        Spacer(Modifier.height(20.dp))

        LazyColumn(Modifier.weight(1f)) {
            items(items.sortedByDescending { it.time }) { held ->
                val time = Instant.ofEpochMilli(held.time)
                    .atZone(ZoneId.systemDefault())
                    .format(WHEN)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenApp(held.packageName) }
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        "${appLabel(held.packageName).lowercase()} · $time",
                        style = LauncherTheme.caption,
                    )
                    if (held.title.isNotBlank() || held.text.isNotBlank()) {
                        Text(
                            listOf(held.title, held.text)
                                .filter { it.isNotBlank() }
                                .joinToString(" — "),
                            style = LauncherTheme.body,
                        )
                    }
                }
            }
        }

        Text(
            "clear all",
            style = LauncherTheme.item.copy(color = LauncherTheme.muted),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClearAll)
                .padding(vertical = 16.dp),
        )
    }
}
