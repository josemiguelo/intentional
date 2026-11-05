package dev.josemiguel.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.josemiguel.intentional.core.Friction
import dev.josemiguel.launcher.AppEntry

private enum class AppsTab(val label: String) {
    ALL("all"), CONSIDERED("considered"), BATCHED("batched"), GROUPS("groups")
}

/**
 * Every app and its flags, filterable through tabs — see at a glance which apps
 * are considered, batched, or in which group, instead of opening each one.
 * Tapping an app expands a panel of its labeled options.
 */
@Composable
fun SettingsAppsScreen(
    apps: List<AppEntry>,
    favorites: List<String>,
    considered: Set<String>,
    essentials: Set<String>,
    batched: Set<String>,
    frictionVersion: Int,
    frictionFor: (String) -> Friction,
    groupNames: List<String>,
    groupNameOf: (String) -> String?,
    onToggleFavorite: (String) -> Unit,
    onToggleConsidered: (String) -> Unit,
    onToggleEssential: (String) -> Unit,
    onToggleBatched: (String) -> Unit,
    onCycleFriction: (String) -> Unit,
    onAssignGroup: (String, String?) -> Unit,
) {
    var tab by remember { mutableStateOf(AppsTab.ALL) }
    var query by remember { mutableStateOf("") }
    var expandedPkg by remember { mutableStateOf<String?>(null) }

    fun row(app: AppEntry): @Composable () -> Unit = {
        AppRow(
            app = app,
            expanded = expandedPkg == app.packageName,
            onToggleExpand = {
                expandedPkg = if (expandedPkg == app.packageName) null else app.packageName
            },
            favorites = favorites,
            considered = considered,
            essentials = essentials,
            batched = batched,
            frictionVersion = frictionVersion,
            frictionFor = frictionFor,
            groupNames = groupNames,
            groupNameOf = groupNameOf,
            onToggleFavorite = onToggleFavorite,
            onToggleConsidered = onToggleConsidered,
            onToggleEssential = onToggleEssential,
            onToggleBatched = onToggleBatched,
            onCycleFriction = onCycleFriction,
            onAssignGroup = onAssignGroup,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Text("apps", style = LauncherTheme.title)

        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp)) {
            AppsTab.entries.forEach { t ->
                Text(
                    t.label,
                    style = LauncherTheme.body.copy(
                        fontSize = 15.sp,
                        color = if (t == tab) LauncherTheme.ink else LauncherTheme.faint,
                    ),
                    modifier = Modifier
                        .clickable { tab = t; expandedPkg = null }
                        .padding(end = 16.dp, top = 4.dp, bottom = 6.dp),
                )
            }
        }

        if (tab == AppsTab.ALL) {
            Box(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                if (query.isEmpty()) {
                    Text("filter…", style = LauncherTheme.body.copy(color = LauncherTheme.faint))
                }
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = LauncherTheme.body,
                    cursorBrush = SolidColor(LauncherTheme.ink),
                    modifier = Modifier.fillMaxWidth().dpadLeavesField(),
                )
            }
        }

        when (tab) {
            AppsTab.ALL -> {
                val q = query.trim().lowercase()
                val shown = if (q.isEmpty()) apps
                else apps.filter { it.label.lowercase().contains(q) }
                LazyColumn {
                    items(shown, key = { it.packageName + it.className }) { row(it)() }
                }
            }

            AppsTab.CONSIDERED -> FlagList(
                apps.filter { it.packageName in considered },
                empty = "no considered apps",
                row = ::row,
            )

            AppsTab.BATCHED -> FlagList(
                apps.filter { it.packageName in batched },
                empty = "no batched apps",
                row = ::row,
            )

            AppsTab.GROUPS -> GroupedList(
                apps = apps,
                groupNames = groupNames,
                groupNameOf = groupNameOf,
                frictionVersion = frictionVersion,
                row = ::row,
            )
        }
    }
}

@Composable
private fun FlagList(
    apps: List<AppEntry>,
    empty: String,
    row: (AppEntry) -> @Composable () -> Unit,
) {
    if (apps.isEmpty()) {
        Text(empty, style = LauncherTheme.body.copy(color = LauncherTheme.muted))
        return
    }
    LazyColumn {
        items(apps, key = { it.packageName + it.className }) { row(it)() }
    }
}

@Composable
private fun GroupedList(
    apps: List<AppEntry>,
    groupNames: List<String>,
    groupNameOf: (String) -> String?,
    frictionVersion: Int,
    row: (AppEntry) -> @Composable () -> Unit,
) {
    if (groupNames.isEmpty()) {
        Text(
            "no groups yet — create them in groups & time limits.",
            style = LauncherTheme.body.copy(color = LauncherTheme.muted),
        )
        return
    }
    LazyColumn {
        groupNames.forEach { name ->
            val members = frictionVersion.let {
                apps.filter { groupNameOf(it.packageName) == name }
            }
            item(key = "hdr-$name") {
                Text(
                    name.lowercase(),
                    style = LauncherTheme.caption.copy(color = LauncherTheme.faint, fontSize = 15.sp),
                    modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
                )
            }
            if (members.isEmpty()) {
                item(key = "empty-$name") {
                    Text(
                        "no apps",
                        style = LauncherTheme.body.copy(color = LauncherTheme.faint),
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            } else {
                items(members, key = { "$name-${it.packageName}-${it.className}" }) { row(it)() }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    favorites: List<String>,
    considered: Set<String>,
    essentials: Set<String>,
    batched: Set<String>,
    frictionVersion: Int,
    frictionFor: (String) -> Friction,
    groupNames: List<String>,
    groupNameOf: (String) -> String?,
    onToggleFavorite: (String) -> Unit,
    onToggleConsidered: (String) -> Unit,
    onToggleEssential: (String) -> Unit,
    onToggleBatched: (String) -> Unit,
    onCycleFriction: (String) -> Unit,
    onAssignGroup: (String, String?) -> Unit,
) {
    val pkg = app.packageName
    Column(Modifier.fillMaxWidth()) {
        Text(
            app.label.lowercase(),
            style = LauncherTheme.body.copy(
                color = if (expanded) LauncherTheme.ink else LauncherTheme.muted
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(vertical = 10.dp),
        )

        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .background(LauncherTheme.panel, RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                OptionRow("on home screen", if (pkg in favorites) "yes" else "no", pkg in favorites) {
                    onToggleFavorite(pkg)
                }
                if (pkg in favorites) {
                    OptionRow(
                        "essential in wind-down",
                        if (pkg in essentials) "yes" else "no",
                        pkg in essentials,
                    ) { onToggleEssential(pkg) }
                }
                OptionRow("considered", if (pkg in considered) "yes" else "no", pkg in considered) {
                    onToggleConsidered(pkg)
                }
                if (pkg in considered) {
                    OptionRow("friction", frictionVersion.let { frictionFor(pkg).label }, true) {
                        onCycleFriction(pkg)
                    }
                }
                OptionRow("notifications batched", if (pkg in batched) "yes" else "no", pkg in batched) {
                    onToggleBatched(pkg)
                }
                val group = frictionVersion.let { groupNameOf(pkg) }
                OptionRow("group", group ?: "none", group != null) {
                    val idx = groupNames.indexOf(group)
                    val next = if (idx + 1 >= groupNames.size) null else groupNames[idx + 1]
                    onAssignGroup(pkg, next)
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    value: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp)
    ) {
        Text(
            label,
            style = LauncherTheme.body.copy(color = LauncherTheme.muted),
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = LauncherTheme.body.copy(
                color = if (active) LauncherTheme.ink else LauncherTheme.faint
            ),
        )
    }
}
