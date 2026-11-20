package com.example.xcommandcenter.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcommandcenter.ui.common.SectionTitle

/**
 * A one-time step while goals remain: move them into Ideas as visions (see SimplifyRepository).
 * Shown only while there are goals; it asks first, suggesting an export.
 */
@Composable
internal fun SimplifySettings(viewModel: SettingsViewModel) {
    val goals by viewModel.goalCount.collectAsStateWithLifecycle()
    if (goals == 0) return
    var asking by remember { mutableStateOf(false) }
    SectionTitle("Simplify")
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Move your ${if (goals == 1) "goal" else "$goals goals"} into Ideas as visions. Each keeps its title, its strategy " +
                    "(as the explanation), priority, links, tags and reminders, so tasks and plans linked to a goal stay linked " +
                    "to the vision. The Goals tab is then hidden.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { asking = true }) { Text("Move goals to Ideas") }
        }
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("Move goals to Ideas?") },
            text = {
                Text(
                    "This can't be undone here. Export to Excel first if you want a copy to go back to; importing it restores the goals.",
                )
            },
            confirmButton = { TextButton(onClick = { asking = false; viewModel.goalsToVisions() }) { Text("Move") } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } },
        )
    }
}
