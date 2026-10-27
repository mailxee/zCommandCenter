package com.example.xcommandcenter.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.PlanTree
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.common.DetailScaffold
import com.example.xcommandcenter.ui.common.LinkedItemsSection
import com.example.xcommandcenter.ui.common.SectionTitle
import com.example.xcommandcenter.ui.common.TagsSection
import com.example.xcommandcenter.ui.common.clearListItemColors
import com.example.xcommandcenter.ui.common.itemSpanStyle
import com.example.xcommandcenter.ui.common.itemTextStyle

@Composable
fun PlanDetailScreen(
    onBack: () -> Unit,
    onOpen: (ItemRef) -> Unit,
    onCreateChild: (parentId: Long) -> Unit,
    viewModel: PlanDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val id by viewModel.itemId.collectAsStateWithLifecycle()
    val all by viewModel.allPlans.collectAsStateWithLifecycle()
    val plan = viewModel.plan
    var choosingParent by rememberSaveable { mutableStateOf(false) }

    val parent = all.firstOrNull { it.id == plan.parentId }
    val children = if (id == 0L) emptyList() else all.filter { it.parentId == id }
    // Read from the database, not the form: completion is saved immediately, outside the form.
    val completed = all.firstOrNull { it.id == id }?.completed ?: false

    DetailScaffold(
        title = if (id == 0L) "New plan" else "Plan",
        viewModel = viewModel,
        onBack = onBack,
        onSave = viewModel::save,
        onDelete = if (id == 0L) null else viewModel::delete,
        deleteWarning = "Its child plans move up one level. All of its links are removed.",
    ) {
        OutlinedTextField(
            value = plan.title,
            onValueChange = { v -> viewModel.update { it.copy(title = v) } },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = plan.description,
            onValueChange = { v -> viewModel.update { it.copy(description = v) } },
            label = { Text("Description") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        LinkedItemsSection(viewModel, onOpen)
        if (id != 0L) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = completed, onCheckedChange = viewModel::setCompleted)
                Text("Completed")
            }
        }

        SectionTitle("Parent plan")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                parent?.title ?: "None (top level)",
                style = if (parent != null) itemTextStyle(ItemType.PLAN, MaterialTheme.typography.bodyLarge) else MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .then(if (parent != null) Modifier.clickable { onOpen(ItemRef(ItemType.PLAN, parent.id)) } else Modifier),
                // A plain parent title shows as a link; a styled one keeps its plan color.
                color = if (parent != null) itemSpanStyle(ItemType.PLAN).color.takeOrElse { MaterialTheme.colorScheme.primary } else MaterialTheme.colorScheme.onSurface,
            )
            OutlinedButton(onClick = { choosingParent = true }) { Text("Change") }
        }

        if (id != 0L) {
            SectionTitle("Child plans")
            Card(Modifier.fillMaxWidth()) {
                if (children.isEmpty()) {
                    Text("No child plans.", modifier = Modifier.padding(16.dp))
                }
                children.forEach { child ->
                    ListItem(
                        colors = clearListItemColors(),
                        headlineContent = {
                            val style = itemTextStyle(ItemType.PLAN, LocalTextStyle.current)
                            Text(
                                child.title,
                                style = style,
                                textDecoration = TextDecoration.combine(
                                    listOfNotNull(style.textDecoration, TextDecoration.LineThrough.takeIf { child.completed }),
                                ),
                            )
                        },
                        modifier = Modifier.clickable { onOpen(ItemRef(ItemType.PLAN, child.id)) },
                    )
                }
                TextButton(onClick = { onCreateChild(id) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Add child plan")
                }
            }
        }

        TagsSection(viewModel)
    }

    viewModel.blockedCompletion?.let {
        CompletePlanDialog(it, viewModel::completeWithSubplans, viewModel::dismissBlockedCompletion)
    }

    if (choosingParent) {
        // A plan cannot sit under itself or anything below it.
        val excluded = if (id == 0L) emptySet() else PlanTree.subtreeIds(id, all, { it.id }, { it.parentId })
        val options = PlanTree.flatten(all, { it.id }, { it.parentId }).filter { it.node.id !in excluded }
        AlertDialog(
            onDismissRequest = { choosingParent = false },
            title = { Text("Parent plan") },
            text = {
                Column {
                    ListItem(
                        colors = clearListItemColors(),
                        headlineContent = { Text("None (top level)") },
                        modifier = Modifier.clickable {
                            viewModel.update { it.copy(parentId = null) }
                            choosingParent = false
                        },
                    )
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(options, key = { it.node.id }) { row ->
                            ListItem(
                                colors = clearListItemColors(),
                                headlineContent = { Text(row.node.title, style = itemTextStyle(ItemType.PLAN, LocalTextStyle.current)) },
                                modifier = Modifier
                                    .padding(start = (row.depth * 16).dp)
                                    .clickable {
                                        viewModel.update { it.copy(parentId = row.node.id) }
                                        choosingParent = false
                                    },
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingParent = false }) { Text("Cancel") } },
        )
    }
}
