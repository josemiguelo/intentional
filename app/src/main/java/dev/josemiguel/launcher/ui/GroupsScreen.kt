package dev.josemiguel.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.josemiguel.launcher.AppGroup
import dev.josemiguel.launcher.Prefs

/**
 * Create app groups and set each one's shared daily budget. Budgets change
 * only once a week; a locked group shows the days remaining.
 */
@Composable
fun GroupsScreen(
    groups: List<AppGroup>,
    canChangeLimit: (AppGroup) -> Boolean,
    daysUntilChangeable: (AppGroup) -> Int,
    onAddGroup: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onSetLimit: (String, Int) -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var editingLimit by remember { mutableStateOf<AppGroup?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Text("groups", style = LauncherTheme.title)
        Spacer(Modifier.height(8.dp))
        Text(
            "apps in a group share one daily budget. a budget can be changed " +
                "only once a week.",
            style = LauncherTheme.caption.copy(color = LauncherTheme.faint),
        )
        Spacer(Modifier.height(16.dp))

        if (groups.isEmpty()) {
            Text("no groups yet.", style = LauncherTheme.body.copy(color = LauncherTheme.muted))
        }

        groups.forEach { group ->
            val changeable = canChangeLimit(group)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = changeable) { editingLimit = group }
                    .padding(vertical = 10.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(group.name.lowercase(), style = LauncherTheme.body)
                    Text(
                        "${group.limitMinutes} min · ${group.packages.size} apps" +
                            if (!changeable) " · locked ${daysUntilChangeable(group)}d" else "",
                        style = LauncherTheme.caption,
                    )
                }
                Text(
                    "delete",
                    style = LauncherTheme.caption.copy(color = LauncherTheme.faint),
                    modifier = Modifier
                        .clickable { onDeleteGroup(group.name) }
                        .padding(start = 12.dp, top = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "+ add group",
            style = LauncherTheme.body.copy(color = LauncherTheme.muted),
            modifier = Modifier
                .clickable { adding = true }
                .padding(vertical = 10.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "assign apps to a group from settings › manage apps.",
            style = LauncherTheme.caption.copy(color = LauncherTheme.faint),
        )
    }

    if (adding) {
        GroupTextDialog(
            title = "group name",
            initial = "",
            digitsOnly = false,
            onSave = { if (it.isNotBlank()) onAddGroup(it.trim()) },
            onDismiss = { adding = false },
        )
    }

    editingLimit?.let { group ->
        GroupTextDialog(
            title = "budget in minutes (max ${Prefs.MAX_LIMIT_MINUTES}) · changeable once a week",
            initial = group.limitMinutes.toString(),
            digitsOnly = true,
            onSave = { it.toIntOrNull()?.let { m -> onSetLimit(group.name, m) } },
            onDismiss = { editingLimit = null },
        )
    }
}

@Composable
private fun GroupTextDialog(
    title: String,
    initial: String,
    digitsOnly: Boolean,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(LauncherTheme.background, RoundedCornerShape(8.dp))
                .padding(vertical = 16.dp, horizontal = 20.dp)
        ) {
            Text(title, style = LauncherTheme.caption)
            BasicTextField(
                value = value,
                onValueChange = {
                    value = if (digitsOnly) it.filter(Char::isDigit).take(3) else it
                },
                singleLine = true,
                textStyle = LauncherTheme.item,
                cursorBrush = SolidColor(LauncherTheme.ink),
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )
            Text(
                "save",
                style = LauncherTheme.item,
                modifier = Modifier
                    .clickable { onSave(value); onDismiss() }
                    .padding(vertical = 8.dp),
            )
        }
    }
}
