package com.example.xcommandcenter.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.xcommandcenter.R

/** One item of a list being edited; [key] tells rows apart while their text changes. */
data class ListLine(val key: Long, val text: String, val done: Boolean = false)

/**
 * A bulleted list edited in place: edit or remove items, and add new ones at the end. With
 * [checkable], each item has a checkbox instead of a bullet (a task's checklist); without, items
 * are plain bullets (a note's list).
 */
@Composable
fun ListEditor(
    title: String,
    lines: List<ListLine>,
    checkable: Boolean,
    onAdd: (String) -> Unit,
    onUpdate: (key: Long, transform: (ListLine) -> ListLine) -> Unit,
    onRemove: (key: Long) -> Unit,
) {
    var newItem by rememberSaveable { mutableStateOf("") }
    val add = {
        onAdd(newItem)
        newItem = ""
    }
    SectionTitle(title)
    lines.forEach { line ->
        key(line.key) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (checkable) {
                    Checkbox(checked = line.done, onCheckedChange = { done -> onUpdate(line.key) { it.copy(done = done) } })
                } else {
                    Text("•", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 12.dp))
                }
                val struck = checkable && line.done
                BasicTextField(
                    value = line.text,
                    onValueChange = { text -> onUpdate(line.key) { it.copy(text = text) } },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (struck) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (struck) TextDecoration.LineThrough else null,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemove(line.key) }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove item")
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newItem,
            onValueChange = { newItem = it },
            label = { Text("Add an item") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = add, enabled = newItem.isNotBlank()) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = "Add item")
        }
    }
}

/** Shared by the forms that own a list: adds, edits and removes [ListLine]s with fresh keys. */
class ListLines {
    private var nextKey = 0L

    fun line(text: String, done: Boolean = false) = ListLine(nextKey++, text, done)

    fun add(lines: List<ListLine>, text: String): List<ListLine> = if (text.isBlank()) lines else lines + line(text.trim())

    fun update(lines: List<ListLine>, key: Long, transform: (ListLine) -> ListLine): List<ListLine> =
        lines.map { if (it.key == key) transform(it) else it }

    fun remove(lines: List<ListLine>, key: Long): List<ListLine> = lines.filterNot { it.key == key }
}
