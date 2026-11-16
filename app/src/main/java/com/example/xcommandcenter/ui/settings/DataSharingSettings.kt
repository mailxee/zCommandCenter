package com.example.xcommandcenter.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcommandcenter.domain.DataSharing
import com.example.xcommandcenter.domain.ShareApp
import com.example.xcommandcenter.ui.common.SectionTitle
import com.example.xcommandcenter.ui.common.clearListItemColors

/**
 * Which other apps may read the shared data (see `ShareProvider`): requests waiting for an answer,
 * with Allow and Deny, and the allowed apps, with Revoke.
 */
@Composable
fun DataSharingSettings(viewModel: SettingsViewModel) {
    val pending by viewModel.sharePending.collectAsStateWithLifecycle()
    val allowed by viewModel.shareAllowed.collectAsStateWithLifecycle()
    SectionTitle("Data sharing")
    Text(
        "Apps you allow here can read your tasks, habits and their history, goals, plans, schedules, tags, quotes and books, " +
            "but not change them. Notes, people, ideas, links and reminders are never shared. " +
            "They can also ask to add a habit or mark one, which happens only once you confirm it on the screen that opens. " +
            "An app that tries either before you allow it is listed here.",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (pending.isEmpty() && allowed.isEmpty()) {
        Text("No app has asked yet, so nothing is shared.", style = MaterialTheme.typography.bodyMedium)
    }
    if (pending.isNotEmpty()) {
        Text("Asking for access", style = MaterialTheme.typography.titleSmall)
        Card(Modifier.fillMaxWidth()) {
            pending.forEach { app ->
                key(app.packageName, app.certSha256) {
                    ShareAppRow(app) {
                        TextButton(
                            onClick = { viewModel.denyShare(app) },
                            modifier = Modifier.semantics { contentDescription = "Deny ${app.label}" },
                        ) { Text("Deny") }
                        TextButton(
                            onClick = { viewModel.allowShare(app) },
                            modifier = Modifier.semantics { contentDescription = "Allow ${app.label}" },
                        ) { Text("Allow") }
                    }
                }
            }
        }
    }
    if (allowed.isNotEmpty()) {
        Text("Allowed", style = MaterialTheme.typography.titleSmall)
        Card(Modifier.fillMaxWidth()) {
            allowed.forEach { app ->
                key(app.packageName, app.certSha256) {
                    ShareAppRow(app) {
                        TextButton(
                            onClick = { viewModel.revokeShare(app) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.semantics { contentDescription = "Revoke ${app.label}" },
                        ) { Text("Revoke") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareAppRow(app: ShareApp, actions: @Composable () -> Unit) {
    ListItem(
        colors = clearListItemColors(),
        headlineContent = { Text(app.label) },
        supportingContent = {
            Text("${app.packageName}\nKey ${DataSharing.shortFingerprint(app.certSha256)}", style = MaterialTheme.typography.bodySmall)
        },
        trailingContent = { Row { actions() } },
    )
}
