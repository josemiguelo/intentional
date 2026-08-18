package dev.josemiguel.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Shown when an app is launched after its group's daily budget is used up. */
@Composable
fun BlockedScreen(
    groupName: String,
    limitMinutes: Int,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            "that's your ${limitMinutes} minutes\nof ${groupName.lowercase()} today",
            style = LauncherTheme.title,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "come back tomorrow.",
            style = LauncherTheme.body.copy(color = LauncherTheme.muted),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "close",
            style = LauncherTheme.item,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClose)
                .padding(vertical = 16.dp),
        )
    }
}
