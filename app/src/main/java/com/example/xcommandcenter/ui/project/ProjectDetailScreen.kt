package com.example.xcommandcenter.ui.project

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
import androidx.compose.material3.ListItem
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
import com.example.xcommandcenter.ui.common.clearListItemColors
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.ProjectTree
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.common.DetailScaffold
import com.example.xcommandcenter.ui.common.LinkedItemsSection
import com.example.xcommandcenter.ui.common.SectionTitle

@Composable
fun ProjectDetailScreen(
    onBack: () -> Unit,
    onOpen: (ItemRef) -> Unit,
    onCreateChild: (parentId: Long) -> Unit,
    viewModel: ProjectDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val id by viewModel.itemId.collectAsStateWithLifecycle()
    val linked by viewModel.linked.collectAsStateWithLifecycle()
    val all by viewModel.allProjects.collectAsStateWithLifecycle()
    val project = viewModel.project
    var choosingParent by rememberSaveable { mutableStateOf(false) }

    val parent = all.firstOrNull { it.id == project.parentId }
    val children = if (id == 0L) emptyList() else all.filter { it.parentId == id }

    DetailScaffold(
        title = if (id == 0L) "New project" else "Project",
        viewModel = viewModel,
        onBack = onBack,
        onSave = viewModel::save,
        onDelete = if (id == 0L) null else viewModel::delete,
        deleteWarning = "Its child projects move up one level. All of its links are removed.",
    ) {
        OutlinedTextField(
            value = project.title,
            onValueChange = { v -> viewModel.update { it.copy(title = v) } },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = project.description,
            onValueChange = { v -> viewModel.update { it.copy(description = v) } },
            label = { Text("Description") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle("Parent project")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                parent?.title ?: "None (top level)",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .then(if (parent != null) Modifier.clickable { onOpen(ItemRef(ItemType.PROJECT, parent.id)) } else Modifier),
                color = if (parent != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            OutlinedButton(onClick = { choosingParent = true }) { Text("Change") }
        }

        if (id != 0L) {
            SectionTitle("Child projects")
            Card(Modifier.fillMaxWidth()) {
                if (children.isEmpty()) {
                    Text("No child projects.", modifier = Modifier.padding(16.dp))
                }
                children.forEach { child ->
                    ListItem(
                        colors = clearListItemColors(),
                        headlineContent = { Text(child.title) },
                        modifier = Modifier.clickable { onOpen(ItemRef(ItemType.PROJECT, child.id)) },
                    )
                }
                TextButton(onClick = { onCreateChild(id) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Add child project")
                }
            }
        }

        LinkedItemsSection(viewModel, linked, onOpen)
    }

    if (choosingParent) {
        // A project cannot sit under itself or anything below it.
        val excluded = if (id == 0L) emptySet() else ProjectTree.subtreeIds(id, all, { it.id }, { it.parentId })
        val options = ProjectTree.flatten(all, { it.id }, { it.parentId }).filter { it.node.id !in excluded }
        AlertDialog(
            onDismissRequest = { choosingParent = false },
            title = { Text("Parent project") },
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
                                headlineContent = { Text(row.node.title) },
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
