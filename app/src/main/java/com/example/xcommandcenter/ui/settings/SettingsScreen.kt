package com.example.xcommandcenter.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcommandcenter.R
import com.example.xcommandcenter.reminder.Notifications
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.common.SectionTitle
import com.example.xcommandcenter.ui.common.clearListItemColors

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val order by viewModel.order.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pendingImport by rememberSaveable { mutableStateOf<Uri?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        uri?.let(viewModel::export)
    }
    // Some file pickers label .xlsx files as generic binary data, so accept that too.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }
    viewModel.message?.let { text ->
        LaunchedEffect(text) {
            snackbar.showSnackbar(text, withDismissAction = true, duration = SnackbarDuration.Long)
            viewModel.messageShown()
        }
    }
    var exactAllowed by remember { mutableStateOf(true) }
    var notificationsAllowed by remember { mutableStateOf(true) }
    // Permissions can change in system settings while we are away; re-check on return.
    LifecycleResumeEffect(Unit) {
        exactAllowed = viewModel.canScheduleExactReminders()
        notificationsAllowed = Notifications.hasPermission(context)
        onPauseOrDispose { }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle("Tab order")
            Text(
                "Move tabs up or down. The order is saved and used on the main screen.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Card(Modifier.fillMaxWidth()) {
                order.forEachIndexed { index, tab ->
                    ListItem(
                        colors = clearListItemColors(),
                        headlineContent = { Text(tab.tabLabel) },
                        leadingContent = { Text("${index + 1}") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { viewModel.move(index, index - 1) }, enabled = index > 0) {
                                    Icon(painterResource(R.drawable.ic_arrow_up), contentDescription = "Move ${tab.tabLabel} up")
                                }
                                IconButton(onClick = { viewModel.move(index, index + 1) }, enabled = index < order.lastIndex) {
                                    Icon(painterResource(R.drawable.ic_arrow_down), contentDescription = "Move ${tab.tabLabel} down")
                                }
                            }
                        },
                    )
                }
            }
            TextButton(onClick = viewModel::reset) { Text("Reset to default order") }

            SectionTitle("Reminders")
            Text(
                if (notificationsAllowed) "Notifications are allowed."
                else "Notifications are off, so task reminders cannot be shown.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!notificationsAllowed) {
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }) { Text("Open notification settings") }
            }
            Text(
                if (exactAllowed) "Reminders fire at the exact time you set."
                else "Exact alarms are not allowed, so reminders may arrive a few minutes late.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!exactAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                OutlinedButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")),
                    )
                }) { Text("Allow exact reminders") }
            }

            SectionTitle("Data")
            Text(
                "Export saves everything to an Excel workbook: one sheet per tab, plus chapters, habit history, links and tags. " +
                    "Importing a workbook replaces all current data.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (viewModel.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch(viewModel.exportFileName()) }, enabled = !viewModel.busy) {
                    Text("Export to Excel")
                }
                OutlinedButton(onClick = { importLauncher.launch(arrayOf(XLSX_MIME, "application/octet-stream")) }, enabled = !viewModel.busy) {
                    Text("Import from Excel")
                }
            }
            Button(
                onClick = { confirmDelete = true },
                enabled = !viewModel.busy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text("Delete everything") }
        }
    }

    pendingImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Replace all data?") },
            text = {
                Text(
                    "Everything in the app (items, links, tags and habit history) will be replaced by the workbook. " +
                        "Export first if you want to keep a copy of the current data.",
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingImport = null; viewModel.import(uri) }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete everything?") },
            text = {
                Text(
                    "This permanently deletes all items, links, tags, habit history, pending reminders and settings. " +
                        "It can't be undone. Export first if you want a backup.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; viewModel.deleteEverything() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete everything") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
