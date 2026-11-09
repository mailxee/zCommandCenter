package com.example.xcommandcenter.ui.idea

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcommandcenter.R
import com.example.xcommandcenter.data.entity.IdeaEntity
import com.example.xcommandcenter.domain.Glossary
import com.example.xcommandcenter.domain.IdeaKind
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.PlanTree
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.common.DetailScaffold
import com.example.xcommandcenter.ui.common.KindIcon
import com.example.xcommandcenter.ui.common.LinkedItemsSection
import com.example.xcommandcenter.ui.common.LinksInText
import com.example.xcommandcenter.ui.common.PriorityField
import com.example.xcommandcenter.ui.common.RemindersSection
import com.example.xcommandcenter.ui.common.SectionTitle
import com.example.xcommandcenter.ui.common.TagsSection
import com.example.xcommandcenter.ui.common.clearListItemColors
import com.example.xcommandcenter.ui.common.kindColor

@Composable
fun IdeaDetailScreen(
    onBack: () -> Unit,
    onOpen: (ItemRef) -> Unit,
    onCreateChild: (parentId: Long) -> Unit,
    viewModel: IdeaDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val id by viewModel.itemId.collectAsStateWithLifecycle()
    val all by viewModel.allIdeas.collectAsStateWithLifecycle()
    val idea = viewModel.idea
    var choosingParent by rememberSaveable { mutableStateOf(false) }
    val parent = all.firstOrNull { it.id == idea.parentId }
    val children = if (id == 0L) emptyList() else all.filter { it.parentId == id }

    DetailScaffold(
        title = if (id == 0L) "New ${idea.kind.label.lowercase()}" else idea.kind.label,
        viewModel = viewModel,
        onBack = onBack,
        onSave = viewModel::save,
        onDelete = if (id == 0L) null else viewModel::delete,
        deleteWarning = "The ideas under it move up one level. All of its links are removed.",
    ) {
        SectionTitle("Kind")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IdeaKind.entries.forEach { kind ->
                FilterChip(
                    selected = idea.kind == kind,
                    onClick = { viewModel.update { it.copy(kind = kind) } },
                    label = { Text(kind.label) },
                    leadingIcon = { KindIcon(kind, size = 18.dp) },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = kindColor(kind).copy(alpha = 0.18f)),
                )
            }
        }
        KindDefinition(idea.kind)
        OutlinedTextField(
            value = idea.title,
            onValueChange = { v -> viewModel.update { it.copy(title = v) } },
            label = { Text(idea.kind.label) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = idea.body,
            onValueChange = { v -> viewModel.update { it.copy(body = v) } },
            label = { Text("Explanation (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        LinksInText(idea.body)

        SectionTitle("Under")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .then(if (parent != null) Modifier.clickable { onOpen(ItemRef(ItemType.IDEA, parent.id)) } else Modifier),
            ) {
                if (parent != null) {
                    KindIcon(parent.kind)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    parent?.title ?: "Nothing (top level)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (parent != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
            }
            OutlinedButton(onClick = { choosingParent = true }) { Text("Change") }
        }

        if (id != 0L) {
            SectionTitle("Under this")
            Card(Modifier.fillMaxWidth()) {
                if (children.isEmpty()) Text("Nothing yet.", modifier = Modifier.padding(16.dp))
                children.forEach { child ->
                    ListItem(
                        colors = clearListItemColors(),
                        leadingContent = { KindIcon(child.kind) },
                        headlineContent = { Text(child.title) },
                        modifier = Modifier.clickable { onOpen(ItemRef(ItemType.IDEA, child.id)) },
                    )
                }
                TextButton(onClick = { onCreateChild(id) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Add an idea under this")
                }
            }
        }

        PriorityField(idea.priority) { p -> viewModel.update { it.copy(priority = p) } }
        RemindersSection(viewModel)
        TagsSection(viewModel)
        LinkedItemsSection(viewModel, onOpen)
    }

    if (choosingParent) {
        ParentPicker(
            all = all,
            // An idea can't go under itself or anything below it.
            excluded = if (id == 0L) emptySet() else PlanTree.subtreeIds(id, all, { it.id }, { it.parentId }),
            onPick = { parentId ->
                viewModel.update { it.copy(parentId = parentId) }
                choosingParent = false
            },
            onDismiss = { choosingParent = false },
        )
    }
}

/** The kind's definition under the picker, so the choice is easy to make. */
@Composable
fun KindDefinition(kind: IdeaKind, definition: String = Glossary.default(kind)) {
    val color = kindColor(kind)
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.10f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(kind.label + ": ") }
                    append(definition)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ParentPicker(all: List<IdeaEntity>, excluded: Set<Long>, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    val options = PlanTree.flatten(all, { it.id }, { it.parentId }).filter { it.node.id !in excluded }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Under") },
        text = {
            Column {
                ListItem(
                    colors = clearListItemColors(),
                    headlineContent = { Text("Nothing (top level)") },
                    modifier = Modifier.clickable { onPick(null) },
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(options, key = { it.node.id }) { row ->
                        ListItem(
                            colors = clearListItemColors(),
                            leadingContent = { KindIcon(row.node.kind) },
                            headlineContent = { Text(row.node.title) },
                            modifier = Modifier.padding(start = (row.depth * 16).dp).clickable { onPick(row.node.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
