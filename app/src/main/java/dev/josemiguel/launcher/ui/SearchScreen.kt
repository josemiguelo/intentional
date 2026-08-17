package dev.josemiguel.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.josemiguel.launcher.AppEntry

/**
 * Type-to-open. Regular apps match any substring; "considered" apps stay
 * invisible until at least three characters of their name are typed — the
 * impulse has to become a named intention before the app even appears.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    apps: List<AppEntry>,
    considered: Set<String>,
    onOpenApp: (AppEntry) -> Unit,
    onLongPressApp: (AppEntry) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val q = query.trim().lowercase()
    val results = apps.filter { app ->
        val label = app.label.lowercase()
        if (app.packageName in considered) {
            q.length >= 3 && label.startsWith(q)
        } else {
            q.isEmpty() || label.contains(q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Box(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            if (query.isEmpty()) {
                Text("type an app name…", style = LauncherTheme.item.copy(color = LauncherTheme.faint))
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = LauncherTheme.item,
                cursorBrush = SolidColor(LauncherTheme.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    results.firstOrNull()?.let(onOpenApp)
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        }

        LazyColumn {
            items(results, key = { it.packageName + it.className }) { app ->
                Text(
                    app.label.lowercase(),
                    style = LauncherTheme.item.copy(
                        color = if (app.packageName in considered) LauncherTheme.muted
                        else LauncherTheme.ink
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onOpenApp(app) },
                            onLongClick = { onLongPressApp(app) },
                        )
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
}
