package com.example.xcommandcenter.ui.note

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.xcommandcenter.R
import com.example.xcommandcenter.domain.IdeaKind
import com.example.xcommandcenter.ui.common.KindIcon
import com.example.xcommandcenter.ui.common.clearListItemColors
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.common.DetailScaffold
import com.example.xcommandcenter.ui.common.LinkedItemsSection
import com.example.xcommandcenter.ui.common.RemindersSection
import com.example.xcommandcenter.ui.common.LinksInText
import com.example.xcommandcenter.ui.common.ListEditor
import com.example.xcommandcenter.ui.common.PriorityField
import com.example.xcommandcenter.ui.common.TagsSection

@Composable
fun NoteDetailScreen(
    onBack: () -> Unit,
    onOpen: (ItemRef) -> Unit,
    onTurnedIntoIdea: (ItemRef) -> Unit = {},
    viewModel: NoteDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val id by viewModel.itemId.collectAsStateWithLifecycle()
    val note = viewModel.note
    var choosingKind by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(viewModel.turnedInto) { viewModel.turnedInto?.let(onTurnedIntoIdea) }

    DetailScaffold(
        title = if (id == 0L) "New note" else "Note",
        viewModel = viewModel,
        onBack = onBack,
        onSave = viewModel::save,
        onDelete = if (id == 0L) null else viewModel::delete,
    ) {
        OutlinedTextField(
            value = note.title,
            onValueChange = { v -> viewModel.update { it.copy(title = v) } },
            label = { Text("Title (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = note.body,
            onValueChange = { v -> viewModel.update { it.copy(body = v) } },
            label = { Text("Note") },
            modifier = Modifier.fillMaxWidth(),
        )
        LinksInText(note.body)
        ListEditor(
            title = "List",
            lines = viewModel.list,
            checkable = false,
            onAdd = viewModel::addListItem,
            onUpdate = viewModel::updateListItem,
            onRemove = viewModel::removeListItem,
            onMove = viewModel::moveListItem,
        )
        PriorityField(note.priority) { p -> viewModel.update { it.copy(priority = p) } }
        RemindersSection(viewModel)
        TagsSection(viewModel)
        LinkedItemsSection(viewModel, onOpen)
        OutlinedButton(onClick = { choosingKind = true }) {
            Icon(painterResource(R.drawable.ic_idea), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Turn into idea")
        }
    }

    if (choosingKind) {
        AlertDialog(
            onDismissRequest = { choosingKind = false },
            title = { Text("Turn into idea") },
            text = {
                Column {
                    Text(
                        "The note becomes an idea on the Ideas tab, taking its links, tags and reminders with it. Its text and list become the explanation.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    IdeaKind.entries.forEach { kind ->
                        ListItem(
                            colors = clearListItemColors(),
                            leadingContent = { KindIcon(kind) },
                            headlineContent = { Text(kind.label) },
                            modifier = Modifier.clickable {
                                choosingKind = false
                                viewModel.turnIntoIdea(kind)
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { choosingKind = false }) { Text("Cancel") } },
        )
    }
}
