package com.example.xcommandcenter.ui.settings

import androidx.compose.material3.OutlinedTextField
import com.example.xcommandcenter.domain.LinkLabels
import com.example.xcommandcenter.data.entity.LinkLabelEntity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
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
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.example.xcommandcenter.data.prefs.ReminderPrefsRepository
import androidx.compose.runtime.key
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.example.xcommandcenter.ui.common.tabIcon
import com.example.xcommandcenter.ui.common.tabAccent
import com.example.xcommandcenter.domain.LinkRoute
import com.example.xcommandcenter.data.entity.TagUsage
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.TabOrder
import com.example.xcommandcenter.reminder.Notifications
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.common.DragHandle
import com.example.xcommandcenter.ui.common.LocalItemStyles
import com.example.xcommandcenter.ui.common.SectionTitle
import com.example.xcommandcenter.ui.common.clearListItemColors
import com.example.xcommandcenter.ui.common.itemTextStyle
import sh.calvin.reorderable.ReorderableColumn

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onTidy: () -> Unit = {},
    onGlossary: () -> Unit = {},
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val order by viewModel.order.collectAsStateWithLifecycle()
    val tagUsage by viewModel.tagUsage.collectAsStateWithLifecycle()
    var deletingTag by remember { mutableStateOf<TagUsage?>(null) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var pendingImport by rememberSaveable { mutableStateOf<Uri?>(null) }
    var editingStyle by rememberSaveable { mutableStateOf<String?>(null) }
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
            SectionTitle("Tabs")
            Text(
                "Drag tabs by their handle to change their order on the main screen. Tap a tab to give its items their own look; " +
                    "it is used wherever an item is shown, including where other tabs list it as linked.",
                style = MaterialTheme.typography.bodyMedium,
            )
            val styles = LocalItemStyles.current
            Card(Modifier.fillMaxWidth()) {
                // A local copy follows the drag; it is replaced when the stored order changes.
                var tabs by remember(order) { mutableStateOf(order) }
                ReorderableColumn(
                    list = tabs,
                    onSettle = { from, to ->
                        tabs = TabOrder.move(tabs, from, to)
                        viewModel.move(from, to)
                    },
                ) { index, tab, _ ->
                    key(tab) {
                        ReorderableItem {
                            ListItem(
                                colors = clearListItemColors(),
                                headlineContent = { Text(tab.tabLabel, style = itemTextStyle(tab, LocalTextStyle.current)) },
                                supportingContent = { Text(styles[tab].summary) },
                                leadingContent = { Text("${index + 1}") },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(onClick = { editingStyle = tab.name }) { Text("Style") }
                                        DragHandle(
                                            Modifier.draggableHandle(),
                                            onMoveUp = if (index > 0) ({ viewModel.move(index, index - 1) }) else null,
                                            onMoveDown = if (index < tabs.lastIndex) ({ viewModel.move(index, index + 1) }) else null,
                                        )
                                    }
                                },
                                modifier = Modifier.clickable { editingStyle = tab.name },
                            )
                        }
                    }
                }
            }
            TextButton(onClick = viewModel::reset) { Text("Reset to default order") }

            SectionTitle("Tags")
            if (tagUsage.isEmpty()) {
                Text("No tags yet. Add them on an item's page.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "Deleting a tag takes it off every item that has it. The items themselves are kept.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Card(Modifier.fillMaxWidth()) {
                    tagUsage.forEach { tag ->
                        key(tag.id) {
                            ListItem(
                                colors = clearListItemColors(),
                                headlineContent = { Text("#${tag.name}") },
                                supportingContent = { Text(itemCount(tag.uses)) },
                                trailingContent = {
                                    IconButton(onClick = { deletingTag = tag }) {
                                        Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete tag ${tag.name}")
                                    }
                                },
                            )
                        }
                    }
                }
            }

            SectionTitle("Reminders")
            Text(
                if (notificationsAllowed) "Notifications are allowed."
                else "Notifications are off, so reminders cannot be shown.",
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
            CarryOverSetting(viewModel)

            LinksSettings(viewModel, onTidy)
            LinkLabelsSettings(viewModel)
            SectionTitle("Glossary")
            Card(Modifier.fillMaxWidth().clickable(onClick = onGlossary)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                    Icon(painterResource(R.drawable.ic_glossary), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Glossary", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "What visions, beliefs, goals, labels and the rest mean to you",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("Open", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }

            SectionTitle("Data")
            Text(
                "Export saves everything to an Excel workbook: one sheet per tab, plus chapters, habit history, links, tags, reminders " +
                    "and these settings (tab order and item styles). " +
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
                    "Everything in the app (items, links, tags, habit history, reminders, and the tab order and item styles) " +
                        "will be replaced by the workbook. " +
                        "Export first if you want to keep a copy of the current data.",
                )
            },
            confirmButton = {
                TextButton(onClick = { pendingImport = null; viewModel.import(uri) }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Cancel") } },
        )
    }

    ItemType.tabs.firstOrNull { it.name == editingStyle }?.let { tab ->
        ItemStyleDialog(
            tab = tab,
            style = LocalItemStyles.current[tab],
            onChange = { viewModel.setStyle(tab, it) },
            onReset = { viewModel.resetStyle(tab) },
            onDismiss = { editingStyle = null },
        )
    }

    deletingTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { deletingTag = null },
            title = { Text("Delete #${tag.name}?") },
            text = { Text("The tag is taken off ${itemCount(tag.uses)}. The items themselves are kept.") },
            confirmButton = {
                TextButton(
                    onClick = { deletingTag = null; viewModel.deleteTag(tag) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deletingTag = null }) { Text("Cancel") } },
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

private fun itemCount(n: Int) = if (n == 1) "1 item" else "$n items"

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/**
 * How long after a task's or plan's reminder goes off, with the item still open and no later
 * reminder, the reminder repeats (see `ReminderCarry`).
 */
@Composable
private fun CarryOverSetting(viewModel: SettingsViewModel) {
    val days by viewModel.carryDays.collectAsStateWithLifecycle()
    val choices = ReminderPrefsRepository.choices
    Text("Carry over a reminder when its task or plan is still open", style = MaterialTheme.typography.bodyLarge)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        choices.forEachIndexed { i, n ->
            SegmentedButton(
                selected = n == days,
                onClick = { viewModel.setCarryDays(n) },
                shape = SegmentedButtonDefaults.itemShape(i, choices.size),
            ) { Text(if (n == 0) "Off" else if (n == 1) "1 day" else "$n days", maxLines = 1) }
        }
    }
    Text(
        if (days == 0) "Reminders go off once."
        else "After this long, if the item isn't completed and has no later reminder, the reminder is repeated at the same time of day.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * The suggested route of links (`LinkRoute`): a layer for each linking type, used by the map's rows
 * and the Tidy links suggestions, and the way to Tidy links.
 */
@Composable
private fun LinksSettings(viewModel: SettingsViewModel, onTidy: () -> Unit) {
    val route by viewModel.route.collectAsStateWithLifecycle()
    val suggestions by viewModel.tidySuggestions.collectAsStateWithLifecycle()
    SectionTitle("Links")
    Text(
        "Suggested route: how links usually run, from layer 1 at the top down. Items in the same layer are linked sideways; " +
            "notes, quotes, books, people and blocks always end the route. Only used for the map and Tidy links: linking is never blocked.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LinkRoute.types.forEach { type ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(tabIcon(type)), contentDescription = null, tint = tabAccent(type), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(type.tabLabel, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    val current = route.layers[type] ?: LinkRoute.DEFAULT.getValue(type)
                    val choices = LinkRoute.choices.toList()
                    SingleChoiceSegmentedButtonRow(Modifier.width(196.dp)) {
                        choices.forEachIndexed { i, n ->
                            SegmentedButton(
                                selected = n == current,
                                onClick = { viewModel.setLayer(type, n) },
                                shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                                icon = {},
                                modifier = Modifier.semantics { contentDescription = "${type.tabLabel} in layer $n" },
                            ) { Text("$n") }
                        }
                    }
                }
            }
        }
    }
    TextButton(onClick = viewModel::resetRoute) { Text("Reset to default route") }
    Card(Modifier.fillMaxWidth().clickable(onClick = onTidy)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Icon(painterResource(R.drawable.ic_map), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Tidy links", style = MaterialTheme.typography.bodyLarge)
                Text(
                    when (suggestions) {
                        0 -> "No suggestions across all networks"
                        1 -> "1 suggestion across all networks"
                        else -> "$suggestions suggestions across all networks"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Open", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** The labels links can carry, each with its two wordings and how many links use it. */
@Composable
private fun LinkLabelsSettings(viewModel: SettingsViewModel) {
    val labels by viewModel.labels.collectAsStateWithLifecycle()
    val uses by viewModel.labelUses.collectAsStateWithLifecycle()
    // The label being edited; a blank one (id 0) is a new label.
    var editing by remember { mutableStateOf<LinkLabelEntity?>(null) }
    SectionTitle("Link labels")
    Text(
        "Words a link can carry, like \"supports\" or \"leads to\", with a wording for each end. Pick one when linking, " +
            "or press and hold a linked item to change it. Leave the second wording blank for a label that reads the same both ways.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            labels.forEach { label ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Edit") { editing = label }.padding(vertical = 6.dp),
                ) {
                    val warn = label.key == LinkLabels.CONTRADICTS
                    Icon(
                        painterResource(R.drawable.ic_label), contentDescription = null,
                        tint = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(listOfNotNull(label.forward, label.backward).joinToString(" · "), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    val n = uses[label.id] ?: 0
                    Text(if (n == 1) "1 link" else "$n links", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(
                        painterResource(R.drawable.ic_edit), contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp).size(18.dp),
                    )
                }
            }
            TextButton(onClick = { editing = LinkLabelEntity(forward = "") }) { Text("Add label") }
        }
    }
    editing?.let { label ->
        var forward by remember(label) { mutableStateOf(label.forward) }
        var backward by remember(label) { mutableStateOf(label.backward.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (label.id == 0L) "New label" else "Edit label") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(forward, { forward = it }, label = { Text("One way (\"supports\")") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(backward, { backward = it }, label = { Text("The other way (\"supported by\")") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text(
                        "Blank if it reads the same both ways, like \"contradicts\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = forward.isNotBlank(),
                    onClick = {
                        viewModel.saveLabel(label.id.takeIf { it != 0L }, forward, backward)
                        editing = null
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (label.id != 0L) {
                        TextButton(onClick = { viewModel.deleteLabel(label); editing = null }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
}
