package dev.josemiguel.launcher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.josemiguel.launcher.AppEntry

@Composable
fun AppOptionsDialog(
    app: AppEntry,
    isFavorite: Boolean,
    isConsidered: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleConsidered: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(LauncherTheme.background, RoundedCornerShape(8.dp))
                .padding(vertical = 12.dp, horizontal = 20.dp)
        ) {
            Text(
                app.label.lowercase(),
                style = LauncherTheme.body.copy(color = LauncherTheme.muted),
                modifier = Modifier.padding(vertical = 10.dp),
            )
            Text(
                if (isFavorite) "remove from home" else "add to home",
                style = LauncherTheme.item,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleFavorite(); onDismiss() }
                    .padding(vertical = 12.dp),
            )
            Text(
                if (isConsidered) "unmark as considered" else "mark as considered",
                style = LauncherTheme.item,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleConsidered(); onDismiss() }
                    .padding(vertical = 12.dp),
            )
            Text(
                "app info",
                style = LauncherTheme.item,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAppInfo(); onDismiss() }
                    .padding(vertical = 12.dp),
            )
            Text(
                "uninstall",
                style = LauncherTheme.item.copy(color = LauncherTheme.muted),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUninstall(); onDismiss() }
                    .padding(vertical = 12.dp),
            )
        }
    }
}
