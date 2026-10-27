package com.example.xcommandcenter.ui.note

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.NoteEntity
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.NoteRepository
import com.example.xcommandcenter.data.repository.TagRepository
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.common.ItemCardSource
import com.example.xcommandcenter.ui.common.ItemDetailViewModel
import kotlinx.coroutines.launch

class NoteDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: NoteRepository,
    links: LinkRepository,
    tags: TagRepository,
    cards: ItemCardSource,
) : ItemDetailViewModel(savedStateHandle, ItemType.NOTE, links, tags, cards) {

    var note by mutableStateOf(NoteEntity())
        private set

    init {
        val id = itemId.value
        if (id != 0L) viewModelScope.launch { note = repository.get(id) ?: return@launch onMissing() }
    }

    fun update(transform: (NoteEntity) -> NoteEntity) {
        note = transform(note)
    }

    /** The title is optional, but an entirely empty note is not worth keeping. */
    fun save() {
        if (note.title.isBlank() && note.body.isBlank()) {
            message = "Write something first"
            return
        }
        val toSave = note.copy(id = itemId.value, title = note.title.trim())
        viewModelScope.launch { onSaved(repository.save(toSave)) }
    }

    fun delete() = deleteThen { repository.delete(it) }
}
