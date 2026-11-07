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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.josemiguel.intentional.core.AppGroup
import dev.josemiguel.launcher.Prefs
import kotlinx.coroutines.delay

/**
 * Create groups of apps and websites and set each one's shared daily budget.
 * Budgets change only once a week; a locked group shows the days remaining.
 * While a group is over budget its sites can't be removed, nor the group
 * deleted — either would just undo today's block.
 */
@Composable
fun GroupsScreen(
    groups: List<AppGroup>,
    canChangeLimit: (AppGroup) -> Boolean,
    daysUntilChangeable: (AppGroup) -> Int,
    isOverBudget: (AppGroup) -> Boolean,
    /** Today's (app minutes, site minutes) for a group. */
    usageToday: (AppGroup) -> Pair<Long, Long>,
    /** The other devices' minutes today for a group, or null without sync. */
    othersToday: (AppGroup) -> Long?,
    /** Packages installed here: a group's other members live on other devices. */
    installedHere: Set<String>,
    onRemoveApp: (group: String, app: String) -> Unit,
    onAddGroup: (String) -> Unit,
    onDeleteGroup: (String) -> Unit,
    onSetLimit: (String, Int) -> Unit,
    /** Adds a typed domain or pasted URL; false if it isn't one. */
    onAddSite: (String, String) -> Boolean,
    onRemoveSite: (String, String) -> Unit,
    /** Changes with each sync, which brings the other devices' time. */
    syncs: Int = 0,
) {
    // The numbers move while the screen is open: re-read them every half
    // minute and after each sync, or a screen opened before the first sync
    // (a TV just switched on) keeps showing zeros.
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            tick++
        }
    }
    val usage = remember(groups, tick, syncs) { groups.associateWith { usageToday(it) to othersToday(it) } }
    var adding by remember { mutableStateOf(false) }
    var editingLimit by remember { mutableStateOf<AppGroup?>(null) }
    var addingSiteTo by remember { mutableStateOf<AppGroup?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text("groups", style = LauncherTheme.title)
        Spacer(Modifier.height(8.dp))
        Text(
            "apps and sites in a group share one daily budget. a budget can be " +
                "changed only once a week; while a group is over budget, its sites " +
                "can't be removed until tomorrow.",
            style = LauncherTheme.caption.copy(color = LauncherTheme.faint),
        )
        Spacer(Modifier.height(16.dp))

        if (groups.isEmpty()) {
            Text("no groups yet.", style = LauncherTheme.body.copy(color = LauncherTheme.muted))
        }

        groups.forEach { group ->
            val changeable = canChangeLimit(group)
            val over = isOverBudget(group)
            val (minutes, others) = usage.getValue(group)
            val (appMinutes, siteMinutes) = minutes
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = changeable) { editingLimit = group }
                    .padding(vertical = 10.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(group.name.lowercase(), style = LauncherTheme.body)
                    Text(
                        "${group.limitMinutes} min · ${count(group.packages.size, "app")} · " +
                            count(group.sites.size, "site") +
                            if (!changeable) " · locked ${daysUntilChangeable(group)}d" else "",
                        style = LauncherTheme.caption,
                    )
                    Text(
                        "today ${appMinutes + siteMinutes + (others ?: 0)} min · app $appMinutes · sites $siteMinutes" +
                            if (others != null) " · other devices $others" else "",
                        style = LauncherTheme.caption.copy(color = LauncherTheme.faint),
                    )
                }
                RemoveLink("delete", locked = over) { onDeleteGroup(group.name) }
            }

            // Members that aren't apps on this device (desktop windows, the
            // TV's apps): listed so they can be seen and removed from here.
            group.packages.filter { it !in installedHere }.sorted().forEach { app ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 2.dp, bottom = 2.dp)) {
                    Text(
                        if (app.startsWith("hypr:")) "desktop · ${app.removePrefix("hypr:")}" else "other device · $app",
                        style = LauncherTheme.body.copy(color = LauncherTheme.muted),
                        modifier = Modifier.weight(1f),
                    )
                    RemoveLink("remove", locked = over) { onRemoveApp(group.name, app) }
                }
            }

            group.sites.sorted().forEach { site ->
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 2.dp, bottom = 2.dp)) {
                    Text(
                        site,
                        style = LauncherTheme.body.copy(color = LauncherTheme.muted),
                        modifier = Modifier.weight(1f),
                    )
                    RemoveLink("remove", locked = over) { onRemoveSite(group.name, site) }
                }
            }
            Text(
                "+ add site",
                style = LauncherTheme.caption.copy(color = LauncherTheme.muted),
                modifier = Modifier
                    .clickable { addingSiteTo = group }
                    .padding(start = 16.dp, top = 4.dp, bottom = 10.dp),
            )
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
            onSave = { if (it.isNotBlank()) onAddGroup(it.trim()); true },
            onDismiss = { adding = false },
        )
    }

    addingSiteTo?.let { group ->
        GroupTextDialog(
            title = "site for ${group.name.lowercase()} · a domain or a link",
            initial = "",
            digitsOnly = false,
            onSave = { onAddSite(group.name, it) },
            invalidMessage = "that isn't a site — try facebook.com",
            onDismiss = { addingSiteTo = null },
        )
    }

    editingLimit?.let { group ->
        GroupTextDialog(
            title = "budget in minutes (max ${Prefs.MAX_LIMIT_MINUTES}) · changeable once a week",
            initial = group.limitMinutes.toString(),
            digitsOnly = true,
            onSave = { it.toIntOrNull()?.let { m -> onSetLimit(group.name, m) }; true },
            onDismiss = { editingLimit = null },
        )
    }
}

private fun count(n: Int, noun: String) = if (n == 1) "1 $noun" else "$n ${noun}s"

/** "delete" / "remove", or a faint "locked until tomorrow" while the group is over budget. */
@Composable
private fun RemoveLink(label: String, locked: Boolean, onClick: () -> Unit) {
    Text(
        if (locked) "locked until tomorrow" else label,
        style = LauncherTheme.caption.copy(color = LauncherTheme.faint),
        modifier = Modifier
            .clickable(enabled = !locked, onClick = onClick)
            .padding(start = 12.dp, top = 4.dp),
    )
}

/** A one-field dialog; [onSave] returning false keeps it open showing [invalidMessage]. */
@Composable
private fun GroupTextDialog(
    title: String,
    initial: String,
    digitsOnly: Boolean,
    onSave: (String) -> Boolean,
    onDismiss: () -> Unit,
    invalidMessage: String? = null,
) {
    var value by remember { mutableStateOf(initial) }
    var invalid by remember { mutableStateOf(false) }
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
                    invalid = false
                },
                singleLine = true,
                textStyle = LauncherTheme.item,
                cursorBrush = SolidColor(LauncherTheme.ink),
                modifier = Modifier.fillMaxWidth().dpadLeavesField().padding(vertical = 12.dp),
            )
            if (invalid && invalidMessage != null) {
                Text(invalidMessage, style = LauncherTheme.caption.copy(color = LauncherTheme.faint))
            }
            Text(
                "save",
                style = LauncherTheme.item,
                modifier = Modifier
                    .clickable { if (onSave(value)) onDismiss() else invalid = true }
                    .padding(vertical = 8.dp),
            )
        }
    }
}
