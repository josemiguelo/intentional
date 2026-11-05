package dev.josemiguel.launcher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

private val SELECT = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/**
 * Holding a remote's select button runs [onLongPress] in place of the click:
 * a TV's stand-in for touch long-press. The repeats and the release are
 * swallowed, so the row underneath never also sees a click. Goes before the
 * row's clickable in the chain, which then handles a plain select as usual.
 */
fun Modifier.onSelectLongPress(onLongPress: () -> Unit): Modifier = composed {
    var held by remember { mutableStateOf(false) }
    onPreviewKeyEvent { event ->
        if (event.key !in SELECT) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> {
                val repeat = event.nativeKeyEvent.repeatCount > 0 || event.nativeKeyEvent.isLongPress
                if (repeat && !held) {
                    held = true
                    onLongPress()
                }
                repeat
            }
            KeyEventType.KeyUp -> held.also { held = false }
            else -> false
        }
    }
}
