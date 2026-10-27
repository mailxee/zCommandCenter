package com.example.xcommandcenter.ui.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcommandcenter.data.entity.ChapterEntity
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.common.DetailScaffold
import com.example.xcommandcenter.ui.common.DragHandle
import com.example.xcommandcenter.ui.common.LinkedItemsSection
import com.example.xcommandcenter.ui.common.LinksInText
import com.example.xcommandcenter.ui.common.PriorityField
import com.example.xcommandcenter.ui.common.SectionTitle
import com.example.xcommandcenter.ui.common.TagsSection
import com.example.xcommandcenter.ui.common.times
import sh.calvin.reorderable.ReorderableColumn

@Composable
fun BookDetailScreen(
    onBack: () -> Unit,
    onOpen: (ItemRef) -> Unit,
    viewModel: BookDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val id by viewModel.itemId.collectAsStateWithLifecycle()
    val chapters by viewModel.chapters.collectAsStateWithLifecycle()
    val stored by viewModel.stored.collectAsStateWithLifecycle()
    val book = viewModel.book
    var addingChapter by rememberSaveable { mutableStateOf(false) }

    DetailScaffold(
        title = if (id == 0L) "New book" else "Book",
        viewModel = viewModel,
        onBack = onBack,
        onSave = viewModel::save,
        onDelete = if (id == 0L) null else viewModel::delete,
        deleteWarning = "This also deletes its chapters and removes all links to the book and its chapters.",
    ) {
        OutlinedTextField(
            value = book.title,
            onValueChange = { v -> viewModel.update { it.copy(title = v) } },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = book.author,
            onValueChange = { v -> viewModel.update { it.copy(author = v) } },
            label = { Text("Author") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = book.description,
            onValueChange = { v -> viewModel.update { it.copy(description = v) } },
            label = { Text("Description") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        LinksInText(book.description)
        PriorityField(book.priority) { p -> viewModel.update { it.copy(priority = p) } }
        TagsSection(viewModel)
        LinkedItemsSection(viewModel, onOpen)

        // Like a chapter's progress, applied at once rather than on save.
        stored?.let { saved ->
            SectionTitle("Progress")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Read ${times(saved.readCount)}", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { viewModel.addBookRead(-1) }, enabled = saved.readCount > 0) { Text("−1") }
                OutlinedButton(onClick = { viewModel.addBookRead(1) }) { Text("+1") }
                TextButton(onClick = viewModel::clearBookReads, enabled = saved.readCount > 0) { Text("Clear") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Internalized", modifier = Modifier.weight(1f))
                Switch(checked = saved.internalized, onCheckedChange = viewModel::setBookInternalized)
            }
        }

        SectionTitle("Table of contents")
        if (id == 0L) {
            Text("Save the book to add chapters.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Card(Modifier.fillMaxWidth()) {
                if (chapters.isEmpty()) Text("No chapters yet.", modifier = Modifier.padding(16.dp))
                // A local copy follows the drag; it is replaced when the stored order changes.
                var ordered by remember(chapters) { mutableStateOf(chapters) }
                ReorderableColumn(
                    list = ordered,
                    onSettle = { from, to ->
                        val moved = ordered[from]
                        ordered = ordered.toMutableList().apply { add(to, removeAt(from)) }
                        viewModel.moveChapter(moved.id, to - from)
                    },
                ) { index, chapter, _ ->
                    key(chapter.id) {
                        ReorderableItem {
                            Column {
                                if (index > 0) HorizontalDivider()
                                ChapterRow(
                                    chapter = chapter,
                                    number = index + 1,
                                    onOpen = { onOpen(ItemRef(ItemType.CHAPTER, chapter.id)) },
                                    onRead = { viewModel.addRead(chapter.id) },
                                    onClearReads = { viewModel.clearReads(chapter.id) },
                                    onInternalized = { viewModel.setInternalized(chapter.id, it) },
                                    dragHandle = {
                                        DragHandle(
                                            Modifier.draggableHandle(),
                                            onMoveUp = if (index > 0) ({ viewModel.moveChapter(chapter.id, -1) }) else null,
                                            onMoveDown = if (index < ordered.lastIndex) ({ viewModel.moveChapter(chapter.id, 1) }) else null,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = { addingChapter = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text("Add chapter")
                }
            }
        }

    }

    if (addingChapter) {
        var title by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingChapter = false },
            title = { Text("New chapter") },
            text = {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.addChapter(title); addingChapter = false },
                    enabled = title.isNotBlank(),
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { addingChapter = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChapterRow(
    chapter: ChapterEntity,
    number: Int,
    onOpen: () -> Unit,
    onRead: () -> Unit,
    onClearReads: () -> Unit,
    onInternalized: (Boolean) -> Unit,
    dragHandle: @Composable () -> Unit,
) {
    Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp)) {
        Text(
            "$number. ${chapter.title}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Read ${chapter.readCount}×", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onRead) { Text("+1") }
            if (chapter.readCount > 0) TextButton(onClick = onClearReads) { Text("Clear") }
            Checkbox(checked = chapter.internalized, onCheckedChange = onInternalized)
            Text("Internalized", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            dragHandle()
        }
    }
}
