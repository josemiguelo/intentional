package dev.josemiguel.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private sealed interface Editing {
    data object WindStart : Editing
    data object WindEnd : Editing
    data object PauseSecs : Editing
    data object LongPauseSecs : Editing
    /** index into dropTimes, or -1 for a new drop time */
    data class Drop(val index: Int) : Editing
}

@Composable
fun SettingsScreen(
    windDownEnabled: Boolean,
    windDownStart: LocalTime,
    windDownEnd: LocalTime,
    pauseSeconds: Int,
    longPauseSeconds: Int,
    dropTimes: List<LocalTime>,
    isDefaultLauncher: Boolean,
    usageAccessGranted: Boolean,
    listenerEnabled: Boolean,
    guardEnabled: Boolean,
    uninstallProtected: Boolean,
    onOpenApps: () -> Unit,
    onOpenGroups: () -> Unit,
    onToggleWindDown: () -> Unit,
    onSetWindDownStart: (LocalTime) -> Unit,
    onSetWindDownEnd: (LocalTime) -> Unit,
    onSetPauseSeconds: (Int) -> Unit,
    onSetLongPauseSeconds: (Int) -> Unit,
    onSetDropTimes: (List<LocalTime>) -> Unit,
    onRequestDefault: () -> Unit,
    onGrantUsage: () -> Unit,
    onGrantListener: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onToggleUninstallProtection: () -> Unit,
    onClearHeld: () -> Unit,
    onResetToday: () -> Unit,
) {
    var editing by remember { mutableStateOf<Editing?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Text("settings", style = LauncherTheme.title)

        SectionHeader("apps")
        SettingRow("manage apps →", onClick = onOpenApps)
        SettingRow("groups & time limits →", onClick = onOpenGroups)

        SectionHeader("wind-down")
        SettingRow(
            "wind-down · ${if (windDownEnabled) "on" else "off"}",
            onClick = onToggleWindDown,
        )
        if (windDownEnabled) {
            SettingRow("start · $windDownStart") { editing = Editing.WindStart }
            SettingRow("end · $windDownEnd") { editing = Editing.WindEnd }
        }

        SectionHeader("friction")
        SettingRow("pause · ${pauseSeconds}s") { editing = Editing.PauseSecs }
        SettingRow("long pause · ${longPauseSeconds}s") { editing = Editing.LongPauseSecs }

        SectionHeader("notification drops")
        dropTimes.forEachIndexed { index, time ->
            SettingRow("$time") { editing = Editing.Drop(index) }
        }
        SettingRow("+ add drop time", muted = true) { editing = Editing.Drop(-1) }
        SettingRow(
            if (listenerEnabled) "notification access · granted"
            else "notification access · grant →",
            muted = listenerEnabled,
            onClick = onGrantListener,
        )

        SectionHeader("permissions")
        SettingRow(
            if (isDefaultLauncher) "default launcher · yes"
            else "default launcher · set →",
            muted = isDefaultLauncher,
            onClick = onRequestDefault,
        )
        SettingRow(
            if (usageAccessGranted) "usage access · granted"
            else "usage access · grant →",
            muted = usageAccessGranted,
            onClick = onGrantUsage,
        )

        SectionHeader("strict mode")
        SettingRow("strict mode · on · required", muted = true)
        RowNote(
            "keeps you from quietly switching away from this launcher in a " +
                "weak moment. it's always on so the decision to leave has to be " +
                "deliberate, never a reflex."
        )
        SettingRow(
            if (guardEnabled) "guard service · enabled · required"
            else "guard service · enable →",
            muted = guardEnabled,
            onClick = onOpenAccessibility,
        )
        RowNote(
            "the part that does the blocking. it watches for the system screens " +
                "that switch the default launcher or turn this guard off, and " +
                "sends you back home. enable it in accessibility settings to arm " +
                "strict mode."
        )
        SettingRow(
            "uninstall protection · ${if (uninstallProtected) "on · locked" else "off"}",
            muted = uninstallProtected,
            onClick = onToggleUninstallProtection,
        )
        RowNote(
            "stops the app from being uninstalled. once on it's locked; removing " +
                "it needs adb (see README)."
        )

        SectionHeader("data")
        SettingRow("clear held notifications", onClick = onClearHeld)
        SettingRow("reset today's counters", onClick = onResetToday)

        Spacer(Modifier.padding(bottom = 24.dp))
    }

    when (val e = editing) {
        null -> Unit

        Editing.WindStart -> TextEntryDialog(
            title = "wind-down start (hh:mm)",
            initial = "$windDownStart",
            onSave = { parseTime(it)?.let(onSetWindDownStart) },
            onDismiss = { editing = null },
        )

        Editing.WindEnd -> TextEntryDialog(
            title = "wind-down end (hh:mm)",
            initial = "$windDownEnd",
            onSave = { parseTime(it)?.let(onSetWindDownEnd) },
            onDismiss = { editing = null },
        )

        Editing.PauseSecs -> TextEntryDialog(
            title = "pause seconds",
            initial = "$pauseSeconds",
            onSave = { it.trim().toIntOrNull()?.let(onSetPauseSeconds) },
            onDismiss = { editing = null },
        )

        Editing.LongPauseSecs -> TextEntryDialog(
            title = "long pause seconds",
            initial = "$longPauseSeconds",
            onSave = { it.trim().toIntOrNull()?.let(onSetLongPauseSeconds) },
            onDismiss = { editing = null },
        )

        is Editing.Drop -> TextEntryDialog(
            title = if (e.index == -1) "new drop time (hh:mm)" else "drop time (hh:mm)",
            initial = if (e.index == -1) "" else "${dropTimes[e.index]}",
            onSave = { input ->
                parseTime(input)?.let { time ->
                    val updated =
                        if (e.index == -1) dropTimes + time
                        else dropTimes.toMutableList().also { it[e.index] = time }
                    onSetDropTimes(updated)
                }
            },
            onRemove = if (e.index != -1) {
                { onSetDropTimes(dropTimes.filterIndexed { i, _ -> i != e.index }) }
            } else null,
            onDismiss = { editing = null },
        )
    }
}

private fun parseTime(input: String): LocalTime? = try {
    LocalTime.parse(input.trim(), DateTimeFormatter.ofPattern("H:mm"))
} catch (e: Exception) {
    null
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text,
        style = LauncherTheme.caption.copy(color = LauncherTheme.faint),
        modifier = Modifier.padding(top = 26.dp, bottom = 2.dp),
    )
}

/** An explanatory caption tied to the row above it. */
@Composable
private fun RowNote(text: String) {
    Text(
        text,
        style = LauncherTheme.caption.copy(color = LauncherTheme.faint),
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
fun SettingRow(text: String, muted: Boolean = false, onClick: () -> Unit = {}) {
    Text(
        text,
        style = LauncherTheme.body.copy(
            color = if (muted) LauncherTheme.muted else LauncherTheme.ink
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    )
}

/** Monospace one-field editor; invalid input saves nothing. */
@Composable
private fun TextEntryDialog(
    title: String,
    initial: String,
    singleLine: Boolean = true,
    saveLabel: String = "save",
    onSave: (String) -> Unit,
    onRemove: (() -> Unit)? = null,
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
                onValueChange = { value = it },
                singleLine = singleLine,
                maxLines = if (singleLine) 1 else 6,
                textStyle = if (singleLine) LauncherTheme.item else LauncherTheme.body,
                cursorBrush = SolidColor(LauncherTheme.ink),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            )
            Row {
                Text(
                    saveLabel,
                    style = LauncherTheme.item,
                    modifier = Modifier
                        .clickable { onSave(value); onDismiss() }
                        .padding(vertical = 8.dp)
                        .padding(end = 28.dp),
                )
                if (onRemove != null) {
                    Text(
                        "remove",
                        style = LauncherTheme.item.copy(color = LauncherTheme.muted),
                        modifier = Modifier
                            .clickable { onRemove(); onDismiss() }
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}
