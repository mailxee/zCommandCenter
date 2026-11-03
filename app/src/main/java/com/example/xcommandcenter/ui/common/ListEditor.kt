package com.example.xcommandcenter.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import sh.calvin.reorderable.ReorderableColumn

/** One item of a list being edited; [key] tells rows apart while their text changes. */
data class ListLine(val key: Long, val text: String, val done: Boolean = false)

/**
 * A bulleted list edited in place: edit, remove or drag items to a new place, and add new ones at
 * the end. With [checkable], each item has a checkbox instead of a bullet (a task's checklist);
 * without, items are plain bullets (a note's list). [leading], when given, draws the start of each
 * row instead (a person's profiles and items show what each one is). [onMove] gets the item's old
 * and new index; [addLabel] labels the field that adds an item.
 *
 * Rows have no drag handles until the move button beside the title is tapped, so the text gets
 * the room and nothing is dragged by accident. While moving, rows show only their text and a
 * handle; tapping ✓ (or leaving the page) ends it.
 */
@Composable
fun ListEditor(
    title: String,
    lines: List<ListLine>,
    checkable: Boolean,
    onAdd: (String) -> Unit,
    onUpdate: (key: Long, transform: (ListLine) -> ListLine) -> Unit,
    onRemove: (key: Long) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    addLabel: String = "Add an item",
    leading: (@Composable (ListLine) -> Unit)? = null,
) {
    var newItem by rememberSaveable { mutableStateOf("") }
    val add = {
        onAdd(newItem)
        newItem = ""
    }
    var moving by rememberSaveable { mutableStateOf(false) }
    if (lines.size < 2) moving = false
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)) {
        Box(Modifier.weight(1f)) { SectionTitle(if (moving) "$title · moving" else title) }
        if (lines.size > 1) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                IconButton(onClick = { moving = !moving }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painterResource(if (moving) R.drawable.ic_check else R.drawable.ic_reorder),
                        contentDescription = if (moving) "Done moving" else "Move items",
                        tint = if (moving) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    ReorderableColumn(list = lines, onSettle = onMove) { index, line, dragging ->
        key(line.key) {
            ReorderableItem {
                // Each item is its own soft box, so items read apart; the controls sit level with
                // the first line of the text rather than the middle of a long item.
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (dragging) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    shadowElevation = if (dragging) 6.dp else 0.dp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                ) {
                    val struck = checkable && line.done
                    val textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (struck) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (struck) TextDecoration.LineThrough else null,
                    )
                    if (moving) {
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
                            if (leading != null) {
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { leading(line) }
                            }
                            Text(
                                line.text,
                                style = textStyle,
                                modifier = Modifier.weight(1f).padding(start = if (leading != null) 4.dp else 12.dp, top = 8.dp, bottom = 8.dp),
                            )
                            DragHandle(
                                Modifier.draggableHandle(),
                                onMoveUp = if (index > 0) ({ onMove(index, index - 1) }) else null,
                                onMoveDown = if (index < lines.lastIndex) ({ onMove(index, index + 1) }) else null,
                                compact = true,
                            )
                        }
                        return@Surface
                    }
                    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                            if (leading != null) {
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { leading(line) }
                            } else if (checkable) {
                                Checkbox(checked = line.done, onCheckedChange = { done -> onUpdate(line.key) { it.copy(done = done) } })
                            } else {
                                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                    Text("•", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        BasicTextField(
                            value = line.text,
                            onValueChange = { text -> onUpdate(line.key) { it.copy(text = text) } },
                            textStyle = textStyle,
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f).padding(start = 4.dp, top = 8.dp, bottom = 8.dp),
                        )
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 40.dp) {
                            IconButton(onClick = { onRemove(line.key) }, modifier = Modifier.size(40.dp)) {
                                Icon(
                                    painterResource(R.drawable.ic_close),
                                    contentDescription = "Remove item",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (!moving) Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = newItem,
            onValueChange = { newItem = it },
            label = { Text(addLabel) },
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

    fun move(lines: List<ListLine>, from: Int, to: Int): List<ListLine> =
        if (from in lines.indices && to in lines.indices) lines.toMutableList().apply { add(to, removeAt(from)) } else lines
}
