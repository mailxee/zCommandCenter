package com.example.xcommandcenter.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcommandcenter.R
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.plan.CompletePlanDialog
import kotlinx.coroutines.flow.Flow

/** Secondary line for an entry: a chapter shows its book, books and quotes their author. */
fun ItemIndexEntry.describe(): String? = when (type) {
    ItemType.CHAPTER -> "Chapter in ${subtitle.orEmpty()}"
    else -> subtitle
}

/**
 * "Linked items" of a detail screen, grouped by tab: each linked item is a card as in its own tab,
 * with the same checkbox or "today" chip, plus a button to remove the link. Also the link picker.
 */
@Composable
fun LinkedItemsSection(viewModel: ItemDetailViewModel, onOpen: (ItemRef) -> Unit) {
    val cards by viewModel.linkedCards.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    val saved = viewModel.itemId.collectAsState().value != 0L

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Linked items", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { picking = true }, enabled = saved) { Text("Add link") }
        }
        when {
            !saved -> HintText("Save this item to start linking it.")
            cards.isEmpty() -> HintText("Nothing linked yet.")
            else -> ItemType.tabs.forEach { tab ->
                val group = cards.filter { it.ref.type.tab == tab }
                if (group.isNotEmpty()) {
                    val labelStyle = itemTextStyle(tab, MaterialTheme.typography.labelLarge)
                    Text(tab.tabLabel, style = labelStyle, color = labelStyle.color.takeOrElse { MaterialTheme.colorScheme.primary })
                    group.forEach { card -> LinkedCard(card, viewModel, onOpen) }
                }
            }
        }
    }

    if (picking) {
        LinkPickerDialog(
            self = viewModel.ref,
            alreadyLinked = cards.map { it.ref }.toSet(),
            search = viewModel::searchLinkTargets,
            onPick = { viewModel.link(it.ref); picking = false },
            onDismiss = { picking = false },
        )
    }
    viewModel.linkedBlocked?.let {
        CompletePlanDialog(it, viewModel::completeLinkedWithSubplans, viewModel::dismissLinkedBlocked)
    }
}

@Composable
private fun LinkedCard(card: ItemCardData, viewModel: ItemDetailViewModel, onOpen: (ItemRef) -> Unit) {
    val control = card.control
    ItemCard(
        type = card.ref.type,
        text = card.text,
        links = card.links,
        onClick = { onOpen(card.ref) },
        tags = card.tags,
        reminder = card.reminder,
        leading = when (control) {
            is CardControl.Task -> {
                { Checkbox(checked = control.completed, onCheckedChange = { viewModel.setLinkedTaskCompleted(card.ref.id, it) }) }
            }
            is CardControl.Plan -> {
                { Checkbox(checked = control.plan.completed, onCheckedChange = { viewModel.setLinkedPlanCompleted(control.plan, it) }) }
            }
            else -> null
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (control is CardControl.Habit) {
                    FilterChip(
                        selected = control.markedToday,
                        onClick = { viewModel.toggleLinkedHabitToday(card.ref.id) },
                        label = { Text(if (control.positive) "Done today" else "Missed today") },
                    )
                }
                IconButton(onClick = { viewModel.unlink(card.ref) }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove link")
                }
            }
        },
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Two steps: choose a tab, then search and choose an item from it. */
@Composable
fun LinkPickerDialog(
    self: ItemRef,
    alreadyLinked: Set<ItemRef>,
    search: (ItemType, String) -> Flow<List<ItemIndexEntry>>,
    onPick: (ItemIndexEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf<ItemType?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tab?.let { "Link a ${it.singular.lowercase()}" } ?: "Link to which tab?") },
        text = {
            val selected = tab
            if (selected == null) {
                Column {
                    ItemType.tabs.forEach { option ->
                        ListItem(
                            colors = clearListItemColors(),
                            headlineContent = { Text(option.tabLabel, style = itemTextStyle(option, LocalTextStyle.current)) },
                            modifier = Modifier.clickable { tab = option },
                        )
                    }
                }
            } else {
                val results by remember(selected, query) { search(selected, query) }.collectAsState(initial = emptyList())
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search by title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val candidates = results.filter { it.ref != self }
                    if (candidates.isEmpty()) Text("No matches.")
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(candidates, key = { "${it.type}-${it.id}" }) { entry ->
                            val linked = entry.ref in alreadyLinked
                            ListItem(
                                colors = clearListItemColors(),
                                headlineContent = {
                                    Text(entry.title, style = itemTextStyle(entry.type, LocalTextStyle.current), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    val detail = listOfNotNull(entry.describe(), if (linked) "Already linked" else null)
                                    if (detail.isNotEmpty()) Text(detail.joinToString(" · "))
                                },
                                modifier = Modifier.clickable(enabled = !linked) { onPick(entry) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (tab != null) TextButton(onClick = { tab = null; query = "" }) { Text("Change tab") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
