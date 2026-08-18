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
import dev.josemiguel.launcher.AppEntry
import dev.josemiguel.launcher.Friction

/**
 * Every app with all its flags in one place. Tapping an app expands a panel
 * with its labeled options; tapping again collapses it.
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
    var query by remember { mutableStateOf("") }
    var expandedPkg by remember { mutableStateOf<String?>(null) }
    val q = query.trim().lowercase()
    val shown = if (q.isEmpty()) apps else apps.filter { it.label.lowercase().contains(q) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 28.dp, vertical = 24.dp)
    ) {
        Text("apps", style = LauncherTheme.title)

        Box(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp)) {
            if (query.isEmpty()) {
                Text("filter…", style = LauncherTheme.body.copy(color = LauncherTheme.faint))
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = LauncherTheme.body,
                cursorBrush = SolidColor(LauncherTheme.ink),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LazyColumn {
            items(shown, key = { it.packageName + it.className }) { app ->
                val pkg = app.packageName
                val expanded = expandedPkg == pkg

                Column(Modifier.fillMaxWidth()) {
                    Text(
                        app.label.lowercase(),
                        style = LauncherTheme.body.copy(
                            color = if (expanded) LauncherTheme.ink else LauncherTheme.muted
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedPkg = if (expanded) null else pkg }
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
                            OptionRow(
                                label = "on home screen",
                                value = if (pkg in favorites) "yes" else "no",
                                active = pkg in favorites,
                            ) { onToggleFavorite(pkg) }

                            if (pkg in favorites) {
                                OptionRow(
                                    label = "essential in wind-down",
                                    value = if (pkg in essentials) "yes" else "no",
                                    active = pkg in essentials,
                                ) { onToggleEssential(pkg) }
                            }

                            OptionRow(
                                label = "considered",
                                value = if (pkg in considered) "yes" else "no",
                                active = pkg in considered,
                            ) { onToggleConsidered(pkg) }

                            if (pkg in considered) {
                                OptionRow(
                                    label = "friction",
                                    value = frictionVersion.let { frictionFor(pkg).label },
                                    active = true,
                                ) { onCycleFriction(pkg) }
                            }

                            OptionRow(
                                label = "notifications batched",
                                value = if (pkg in batched) "yes" else "no",
                                active = pkg in batched,
                            ) { onToggleBatched(pkg) }

                            val group = frictionVersion.let { groupNameOf(pkg) }
                            OptionRow(
                                label = "group",
                                value = group ?: "none",
                                active = group != null,
                            ) {
                                val idx = groupNames.indexOf(group)
                                val next =
                                    if (idx + 1 >= groupNames.size) null else groupNames[idx + 1]
                                onAssignGroup(pkg, next)
                            }
                        }
                    }
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
