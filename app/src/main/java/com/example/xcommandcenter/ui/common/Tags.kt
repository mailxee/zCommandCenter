package com.example.xcommandcenter.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcommandcenter.R
import com.example.xcommandcenter.data.entity.TagEntity
import com.example.xcommandcenter.data.repository.TagRepository

/** How a tag is written everywhere in the UI. */
fun TagEntity.label(): String = "#$name"

/** "Tags" card of a detail screen: the item's tags, removable, and a dialog to add one. */
@Composable
fun TagsSection(viewModel: ItemDetailViewModel) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val saved = viewModel.itemId.collectAsStateWithLifecycle().value != 0L
    var adding by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tags", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { adding = true }, enabled = saved) { Text("Add tag") }
            }
            when {
                !saved -> Hint("Save this item to start tagging it.")
                tags.isEmpty() -> Hint("No tags yet.")
                else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { viewModel.removeTag(tag.id) },
                            label = { Text(tag.label()) },
                            trailingIcon = {
                                Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove ${tag.label()}", Modifier.size(18.dp))
                            },
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        AddTagDialog(
            suggestions = allTags.filter { tag -> tags.none { it.id == tag.id } },
            onAdd = { viewModel.addTag(it); adding = false },
            onDismiss = { adding = false },
        )
    }
}

/** Type a new tag, or tap an existing one; matching ignores case. */
@Composable
private fun AddTagDialog(suggestions: List<TagEntity>, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val query = text.trim().removePrefix("#")
    val matching = suggestions.filter { it.name.contains(query, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add tag") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(TagRepository.MAX_LENGTH + 1) },
                    label = { Text("Tag") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (matching.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        matching.take(20).forEach { tag ->
                            AssistChip(onClick = { onAdd(tag.name) }, label = { Text(tag.label()) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onAdd(query) }, enabled = query.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
