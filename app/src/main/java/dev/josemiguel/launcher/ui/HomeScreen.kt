package dev.josemiguel.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.josemiguel.launcher.AppEntry
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    favorites: List<AppEntry>,
    countsToday: Pair<Int, Int>,
    isDefaultLauncher: Boolean,
    onRequestDefault: () -> Unit,
    onOpenApp: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onOpenSearch: () -> Unit,
) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragStart = { dragged = 0f },
                    onVerticalDrag = { _, dragAmount -> dragged += dragAmount },
                    onDragEnd = { if (dragged < -120f) onOpenSearch() },
                )
            }
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Text(now.format(TIME), style = LauncherTheme.clock)
        Text(now.format(DATE), style = LauncherTheme.caption)

        if (!isDefaultLauncher) {
            Text(
                "→ set as default launcher",
                style = LauncherTheme.body,
                modifier = Modifier
                    .combinedClickable(onClick = onRequestDefault)
                    .padding(top = 16.dp),
            )
        }

        Spacer(Modifier.height(48.dp))

        if (favorites.isEmpty()) {
            Text(
                "swipe up, then long-press an app to add it here",
                style = LauncherTheme.caption,
            )
        }
        favorites.forEach { app ->
            Text(
                app.label.lowercase(),
                style = LauncherTheme.item,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onOpenApp(app) },
                        onLongClick = { onLongPressApp(app) },
                    )
                    .padding(vertical = 10.dp),
            )
        }

        Spacer(Modifier.weight(1f))

        val (intentional, paused) = countsToday
        Text(
            "$intentional intentional · $paused paused-and-closed",
            style = LauncherTheme.caption,
        )
        Text(
            "↑ swipe to search",
            style = LauncherTheme.caption.copy(color = LauncherTheme.faint),
            modifier = Modifier
                .combinedClickable(onClick = onOpenSearch)
                .padding(top = 6.dp),
        )
    }
}
