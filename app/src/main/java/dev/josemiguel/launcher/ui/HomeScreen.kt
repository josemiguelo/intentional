package dev.josemiguel.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.josemiguel.launcher.AppEntry
import dev.josemiguel.launcher.WindDown
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.ENGLISH)

/** Below this much total finger movement, a long-press release opens the options dialog. */
private const val TAP_SLOP_PX = 14f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    favorites: List<AppEntry>,
    essentials: Set<String>,
    windDownEnabled: Boolean,
    windDownStart: LocalTime,
    windDownEnd: LocalTime,
    countsToday: Pair<Int, Int>,
    heldReadyCount: Int,
    needsListenerGrant: Boolean,
    isDefaultLauncher: Boolean,
    onRequestDefault: () -> Unit,
    onOpenTray: () -> Unit,
    onEnableFiltering: () -> Unit,
    onOpenApp: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onReorder: (List<String>) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenReflection: () -> Unit,
    onOpenSettings: () -> Unit,
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
        val windDown = windDownEnabled &&
            WindDown.isActive(windDownStart, windDownEnd, now.toLocalTime())

        Text(
            now.format(TIME),
            style = LauncherTheme.clock,
            modifier = Modifier.combinedClickable(
                onClick = {},
                onLongClick = onOpenSettings,
            ),
        )
        Text(now.format(DATE), style = LauncherTheme.body)
        if (windDown) {
            Text(
                "wind-down · until $windDownEnd",
                style = LauncherTheme.body.copy(color = LauncherTheme.faint),
            )
        }

        if (!isDefaultLauncher) {
            Text(
                "→ set as default launcher",
                style = LauncherTheme.body,
                modifier = Modifier
                    .clickable(onClick = onRequestDefault)
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

        val shownFavorites =
            if (windDown && favorites.any { it.packageName in essentials }) {
                favorites.filter { it.packageName in essentials }
            } else favorites

        FavoritesList(
            favorites = shownFavorites,
            onOpenApp = onOpenApp,
            onLongPressApp = onLongPressApp,
            onReorder = onReorder,
        )

        Spacer(Modifier.weight(1f))

        if (heldReadyCount > 0) {
            Text(
                "→ $heldReadyCount notifications waiting",
                style = LauncherTheme.body,
                modifier = Modifier
                    .clickable(onClick = onOpenTray)
                    .padding(bottom = 10.dp),
            )
        }
        if (needsListenerGrant) {
            Text(
                "→ enable notification filtering",
                style = LauncherTheme.caption,
                modifier = Modifier
                    .clickable(onClick = onEnableFiltering)
                    .padding(bottom = 10.dp),
            )
        }

        val (intentional, paused) = countsToday
        Text(
            "$intentional intentional · $paused paused-and-closed",
            style = LauncherTheme.body,
            modifier = Modifier.clickable(onClick = onOpenReflection),
        )
        Text(
            "↑ swipe to search",
            style = LauncherTheme.body.copy(color = LauncherTheme.faint),
            modifier = Modifier
                .clickable(onClick = onOpenSearch)
                .padding(top = 6.dp),
        )
    }
}

/**
 * Long-press and drag to reorder; a long-press released without movement
 * opens the options dialog instead. The dragged row follows the finger and
 * neighbors swap out of its way as it crosses their midpoint.
 */
@Composable
private fun FavoritesList(
    favorites: List<AppEntry>,
    onOpenApp: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    val order = remember(favorites) { favorites.map { it.packageName }.toMutableStateList() }
    var draggingPkg by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var totalDrag by remember { mutableFloatStateOf(0f) }
    var itemHeight by remember { mutableIntStateOf(0) }

    order.forEach { pkg ->
        val app = favorites.find { it.packageName == pkg } ?: return@forEach
        key(pkg) {
            val isDragging = draggingPkg == pkg
            Text(
                app.label.lowercase(),
                style = LauncherTheme.item.copy(
                    color = if (isDragging) LauncherTheme.muted else LauncherTheme.ink
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .offset { IntOffset(0, if (isDragging) dragOffset.roundToInt() else 0) }
                    .onSizeChanged { itemHeight = it.height }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingPkg = pkg
                                dragOffset = 0f
                                totalDrag = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                totalDrag += abs(amount.x) + abs(amount.y)
                                val i = order.indexOf(pkg)
                                if (itemHeight > 0 && i != -1) {
                                    if (dragOffset > itemHeight / 2f && i < order.lastIndex) {
                                        order[i] = order[i + 1].also { order[i + 1] = order[i] }
                                        dragOffset -= itemHeight
                                    } else if (dragOffset < -itemHeight / 2f && i > 0) {
                                        order[i] = order[i - 1].also { order[i - 1] = order[i] }
                                        dragOffset += itemHeight
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingPkg = null
                                if (totalDrag < TAP_SLOP_PX) {
                                    onLongPressApp(app)
                                } else {
                                    onReorder(order.toList())
                                }
                            },
                            onDragCancel = {
                                draggingPkg = null
                                order.clear()
                                order.addAll(favorites.map { it.packageName })
                            },
                        )
                    }
                    .clickable { onOpenApp(app) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}
