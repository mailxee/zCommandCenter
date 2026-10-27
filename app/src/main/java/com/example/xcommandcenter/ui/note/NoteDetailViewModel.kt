package com.example.xcommandcenter.ui.note

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.NoteEntity
import com.example.xcommandcenter.data.entity.NoteListItemEntity
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.ReminderRepository
import com.example.xcommandcenter.data.repository.NoteRepository
import com.example.xcommandcenter.data.repository.TagRepository
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.common.ItemCardSource
import com.example.xcommandcenter.ui.common.ItemDetailViewModel
import com.example.xcommandcenter.ui.common.ListLine
import com.example.xcommandcenter.ui.common.ListLines
import kotlinx.coroutines.launch

class NoteDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: NoteRepository,
    links: LinkRepository,
    tags: TagRepository,
    cards: ItemCardSource,
    reminders: ReminderRepository,
) : ItemDetailViewModel(savedStateHandle, ItemType.NOTE, links, tags, cards, reminders) {

    var note by mutableStateOf(NoteEntity())
        private set

    /** The note's list, edited here and saved with the note. */
    var list by mutableStateOf<List<ListLine>>(emptyList())
        private set

    private val lines = ListLines()

    init {
        val id = itemId.value
        if (id != 0L) viewModelScope.launch {
            note = repository.get(id) ?: return@launch onMissing()
            list = repository.listItems(id).map { lines.line(it.text) }
            markClean()
        }
        markClean()
    }

    override fun formState(): Any = note to list

    fun update(transform: (NoteEntity) -> NoteEntity) {
        note = transform(note)
    }

    fun addListItem(text: String) {
        list = lines.add(list, text)
    }

    fun updateListItem(key: Long, transform: (ListLine) -> ListLine) {
        list = lines.update(list, key, transform)
    }

    fun removeListItem(key: Long) {
        list = lines.remove(list, key)
    }

    fun moveListItem(from: Int, to: Int) {
        list = lines.move(list, from, to)
    }

    /** The title is optional, but an entirely empty note is not worth keeping. */
    fun save(close: Boolean) {
        val items = list.filter { it.text.isNotBlank() }
        if (note.title.isBlank() && note.body.isBlank() && items.isEmpty()) {
            message = "Write something first"
            return
        }
        val toSave = note.copy(id = itemId.value, title = note.title.trim())
        val listItems = items.map { NoteListItemEntity(noteId = itemId.value, position = 0, text = it.text.trim()) }
        viewModelScope.launch { onSaved(repository.save(toSave, listItems), close) }
    }

    fun delete() = deleteThen { repository.delete(it) }
}
