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
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.xcommandcenter.R
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import kotlinx.coroutines.flow.Flow

/** Secondary line for an entry: a chapter shows its book, books and quotes their author. */
fun ItemIndexEntry.describe(): String? = when (type) {
    ItemType.CHAPTER -> "Chapter in ${subtitle.orEmpty()}"
    else -> subtitle
}

/** "Linked items" card of a detail screen, grouped by tab, plus the link picker. */
@Composable
fun LinkedItemsSection(
    viewModel: ItemDetailViewModel,
    linked: List<ItemIndexEntry>,
    onOpen: (ItemRef) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val saved = viewModel.itemId.collectAsState().value != 0L

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Linked items", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { picking = true }, enabled = saved) { Text("Add link") }
            }
            when {
                !saved -> HintText("Save this item to start linking it.")
                linked.isEmpty() -> HintText("Nothing linked yet.")
                else -> ItemType.tabs.forEach { tab ->
                    val group = linked.filter { it.type.tab == tab }
                    if (group.isNotEmpty()) {
                        Text(
                            tab.tabLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                        )
                        group.forEach { entry ->
                            ListItem(
                                colors = clearListItemColors(),
                                headlineContent = { Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                supportingContent = entry.describe()?.let { { Text(it) } },
                                trailingContent = {
                                    IconButton(onClick = { viewModel.unlink(entry.ref) }) {
                                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove link")
                                    }
                                },
                                modifier = Modifier.clickable { onOpen(entry.ref) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (picking) {
        LinkPickerDialog(
            self = viewModel.ref,
            alreadyLinked = linked.map { it.ref }.toSet(),
            search = viewModel::searchLinkTargets,
            onPick = { viewModel.link(it.ref); picking = false },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                            headlineContent = { Text(option.tabLabel) },
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
                                headlineContent = { Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
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
