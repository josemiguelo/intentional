package dev.josemiguel.launcher.ui

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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

/**
 * Lets the remote's up and down leave a text field, which otherwise keeps them
 * to move through its text, stranding focus there on a TV.
 */
fun Modifier.dpadLeavesField(): Modifier = composed {
    val focusManager = LocalFocusManager.current
    onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        when (event.key) {
            Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
            Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
            else -> false
        }
    }
}

/**
 * A TV's focus highlight, for every clickable row: the panel color behind it
 * and an accent bar just left of it. Compose's default (a faint ripple layer)
 * is invisible from the couch. Drawn a little outside the row, into the
 * frame's padding, so the text keeps its place.
 */
class FocusIndication(private val fill: Color, private val bar: Color) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        Node(interactionSource, fill, bar)

    override fun equals(other: Any?) = other is FocusIndication && other.fill == fill && other.bar == bar

    override fun hashCode() = 31 * fill.hashCode() + bar.hashCode()

    private class Node(
        private val interactionSource: InteractionSource,
        private val fill: Color,
        private val bar: Color,
    ) : Modifier.Node(), DrawModifierNode {

        private var focused = false

        override fun onAttach() {
            coroutineScope.launch {
                interactionSource.interactions.collect {
                    when (it) {
                        is FocusInteraction.Focus -> focused = true
                        is FocusInteraction.Unfocus -> focused = false
                        else -> return@collect
                    }
                    invalidateDraw()
                }
            }
        }

        override fun ContentDrawScope.draw() {
            if (focused) {
                val out = 12.dp.toPx()
                drawRect(fill, topLeft = Offset(-out, 0f), size = Size(size.width + 2 * out, size.height))
                drawRect(bar, topLeft = Offset(-out, 0f), size = Size(4.dp.toPx(), size.height))
            }
            drawContent()
        }
    }
}
