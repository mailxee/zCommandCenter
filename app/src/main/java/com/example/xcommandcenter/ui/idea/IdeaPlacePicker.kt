package com.example.xcommandcenter.ui.idea

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.xcommandcenter.data.entity.IdeaEntity
import com.example.xcommandcenter.domain.PlanTree
import com.example.xcommandcenter.ui.common.KindIcon
import com.example.xcommandcenter.ui.common.clearListItemColors

/**
 * Picks a place among the ideas: the top level, or under one of them, shown as the Ideas tab's
 * outline. A search narrows it to the ideas whose title matches, each saying what it is under.
 * [excluded] ideas are left out (an idea can't go under itself or anything below it).
 */
@Composable
fun IdeaPlacePicker(
    title: String,
    all: List<IdeaEntity>,
    excluded: Set<Long>,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
    topLabel: String = "Nothing (top level)",
) {
    var query by rememberSaveable { mutableStateOf("") }
    val byId = all.associateBy { it.id }
    val words = query.trim()
    val options = PlanTree.flatten(all, { it.id }, { it.parentId })
        .filter { it.node.id !in excluded }
        .filter { words.isEmpty() || it.node.title.contains(words, ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (all.size > 8) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search ideas") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (words.isEmpty()) {
                    ListItem(
                        colors = clearListItemColors(),
                        headlineContent = { Text(topLabel) },
                        modifier = Modifier.clickable { onPick(null) },
                    )
                }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(options, key = { it.node.id }) { row ->
                        val parent = row.node.parentId?.let(byId::get)
                        ListItem(
                            colors = clearListItemColors(),
                            leadingContent = { KindIcon(row.node.kind) },
                            headlineContent = { Text(row.node.title) },
                            supportingContent = if (words.isNotEmpty() && parent != null) ({ Text("Under ${parent.title}") }) else null,
                            modifier = Modifier
                                .padding(start = if (words.isEmpty()) (row.depth * 16).dp else 0.dp)
                                .clickable { onPick(row.node.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
