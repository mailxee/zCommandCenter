package com.example.xcommandcenter.ui.idea

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.xcommandcenter.R
import com.example.xcommandcenter.data.backup.IdeaImportPreview
import com.example.xcommandcenter.data.backup.IdeaNetwork
import com.example.xcommandcenter.data.entity.IdeaEntity
import com.example.xcommandcenter.domain.IdeaKind
import com.example.xcommandcenter.ui.common.KindIcon

/** The list key of the row for the new idea above everything, when one is put there. */
private const val NEW_ROOT_ID = -1L

/**
 * Ideas read from a file, shown before anything is added (§2.80): how many and how deep, what
 * the reader noticed, and the whole tree, read only; tap an idea to read its explanation. The
 * ideas can go under a new idea (named after the document) and anywhere among the ideas already
 * here. Nothing changes until Import.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaImportPreviewDialog(
    preview: IdeaImportPreview,
    existing: List<IdeaEntity>,
    onImport: (rootTitle: String?, parentId: Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    var wrap by rememberSaveable(preview) { mutableStateOf(preview.wrap) }
    var rootTitle by rememberSaveable(preview) { mutableStateOf(preview.rootTitle) }
    var placeId by rememberSaveable(preview) { mutableStateOf<Long?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var open by remember(preview) { mutableStateOf(emptySet<Long>()) }
    val rows = remember(preview) { IdeaNetwork.outline(preview.network) }
    val canImport = !wrap || rootTitle.isNotBlank()
    val total = preview.count + if (wrap) 1 else 0

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Import preview") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Cancel") }
                        },
                        actions = {
                            TextButton(
                                onClick = { onImport(rootTitle.trim().takeIf { wrap }, placeId) },
                                enabled = canImport,
                            ) { Text("Import") }
                        },
                    )
                },
            ) { padding ->
                LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "${count(total, "idea")} in ${count(preview.levels + if (wrap) 1 else 0, "level")}" +
                                    (preview.fileName?.let { " from $it" } ?: ""),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            if (preview.notes.isNotEmpty()) {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        preview.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                                    }
                                }
                            }
                        }
                    }
                    item {
                        ListItem(
                            headlineContent = { Text("Put everything under a new idea") },
                            trailingContent = { Switch(checked = wrap, onCheckedChange = null) },
                            modifier = Modifier.clickable(role = Role.Switch) { wrap = !wrap },
                        )
                        if (wrap) {
                            OutlinedTextField(
                                value = rootTitle,
                                onValueChange = { rootTitle = it },
                                label = { Text("Name of the new idea") },
                                singleLine = true,
                                isError = rootTitle.isBlank(),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            )
                        }
                        val place = placeId?.let { id -> existing.firstOrNull { it.id == id } }
                        ListItem(
                            headlineContent = { Text("Goes") },
                            supportingContent = { Text(place?.let { "Under ${it.title}" } ?: "At the top level") },
                            trailingContent = { TextButton(onClick = { picking = true }) { Text("Change") } },
                        )
                        HorizontalDivider()
                    }
                    if (wrap) {
                        item(key = NEW_ROOT_ID) {
                            PreviewRow(rootTitle.ifBlank { "(no name)" }, "", depth = 0, open = false, onToggle = null, root = true)
                        }
                    }
                    items(rows, key = { it.node.id }) { row ->
                        val idea = row.node
                        val isOpen = idea.id in open
                        PreviewRow(
                            idea.title, idea.body, depth = row.depth + if (wrap) 1 else 0, open = isOpen,
                            onToggle = if (idea.body.isBlank()) null else ({ open = if (isOpen) open - idea.id else open + idea.id }),
                            root = false,
                        )
                    }
                    item { Spacer(Modifier.size(24.dp)) }
                }
            }
        }
    }

    if (picking) {
        IdeaPlacePicker(
            title = "Where should the imported ideas go?",
            all = existing,
            excluded = emptySet(),
            topLabel = "At the top level",
            onPick = { placeId = it; picking = false },
            onDismiss = { picking = false },
        )
    }
}

/** One idea of the tree: indented by its depth, its explanation's first line, or all of it once tapped. */
@Composable
private fun PreviewRow(title: String, body: String, depth: Int, open: Boolean, onToggle: (() -> Unit)?, root: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable(onClickLabel = if (open) "Hide explanation" else "Show explanation", onClick = onToggle) else Modifier)
            .padding(start = 16.dp + (depth * 16).dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        KindIcon(IdeaKind.IDEA, Modifier.padding(top = 2.dp), size = 16.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (root || depth == 0) FontWeight.SemiBold else FontWeight.Medium)
            if (body.isNotBlank()) {
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = if (open) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onToggle != null) {
            Icon(
                painterResource(if (open) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun count(n: Int, what: String) = if (n == 1) "1 $what" else "$n ${what}s"
