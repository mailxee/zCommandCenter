package com.example.xcommandcenter.ui.book

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.common.DetailScaffold
import com.example.xcommandcenter.ui.common.LinkedItemsSection
import com.example.xcommandcenter.ui.common.RemindersSection
import com.example.xcommandcenter.ui.common.SectionTitle
import com.example.xcommandcenter.ui.common.TagsSection

@Composable
fun ChapterDetailScreen(
    onBack: () -> Unit,
    onOpen: (ItemRef) -> Unit,
    viewModel: ChapterDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val book by viewModel.book.collectAsStateWithLifecycle()
    val chapter = viewModel.chapter

    DetailScaffold(
        title = "Chapter",
        viewModel = viewModel,
        onBack = onBack,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
    ) {
        book?.let { parent ->
            Text(
                "In “${parent.title}”",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onOpen(ItemRef(ItemType.BOOK, parent.id)) },
            )
        }
        OutlinedTextField(
            value = viewModel.titleText,
            onValueChange = { viewModel.titleText = it },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        TagsSection(viewModel)
        LinkedItemsSection(viewModel, onOpen)
        RemindersSection(viewModel)
        if (chapter != null) {
            SectionTitle("Progress")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Read ${chapter.readCount} times", modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { viewModel.addRead(-1) }, enabled = chapter.readCount > 0) { Text("−1") }
                OutlinedButton(onClick = { viewModel.addRead(1) }) { Text("+1") }
                TextButton(onClick = viewModel::clearReads, enabled = chapter.readCount > 0) { Text("Clear") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Done", modifier = Modifier.weight(1f))
                Switch(checked = chapter.internalized, onCheckedChange = viewModel::setInternalized)
            }
        }
    }
}
