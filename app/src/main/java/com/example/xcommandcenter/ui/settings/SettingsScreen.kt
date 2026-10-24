package com.example.xcommandcenter.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import com.example.xcommandcenter.ui.common.clearListItemColors
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

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val order by viewModel.order.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var exactAllowed by remember { mutableStateOf(true) }
    var notificationsAllowed by remember { mutableStateOf(true) }
    // Permissions can change in system settings while we are away; re-check on return.
    LifecycleResumeEffect(Unit) {
        exactAllowed = viewModel.canScheduleExactReminders()
        notificationsAllowed = Notifications.hasPermission(context)
        onPauseOrDispose { }
    }

    Scaffold(
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
        }
    }
}
