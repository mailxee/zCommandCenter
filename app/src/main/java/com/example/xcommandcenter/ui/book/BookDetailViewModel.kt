package com.example.xcommandcenter.ui.book

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.BookEntity
import com.example.xcommandcenter.data.entity.ChapterEntity
import com.example.xcommandcenter.data.repository.BookRepository
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.TagRepository
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.common.ItemCardSource
import com.example.xcommandcenter.ui.common.ItemDetailViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BookDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: BookRepository,
    links: LinkRepository,
    tags: TagRepository,
    cards: ItemCardSource,
) : ItemDetailViewModel(savedStateHandle, ItemType.BOOK, links, tags, cards) {

    var book by mutableStateOf(BookEntity(title = ""))
        private set

    /** Table of contents, in order. Chapter changes are saved immediately. */
    val chapters: StateFlow<List<ChapterEntity>> = itemId
        .flatMapLatest { id -> if (id == 0L) flowOf(emptyList()) else repository.observeChapters(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        val id = itemId.value
        if (id != 0L) viewModelScope.launch { book = repository.get(id) ?: return@launch onMissing() }
    }

    fun update(transform: (BookEntity) -> BookEntity) {
        book = transform(book)
    }

    fun save(close: Boolean) {
        if (book.title.isBlank()) {
            message = "A title is required"
            return
        }
        val toSave = book.copy(id = itemId.value, title = book.title.trim(), author = book.author.trim())
        viewModelScope.launch { onSaved(repository.save(toSave), close) }
    }

    fun delete() = deleteThen { repository.delete(it) }

    fun addChapter(title: String) {
        val id = itemId.value
        if (id != 0L && title.isNotBlank()) viewModelScope.launch { repository.addChapter(id, title.trim()) }
    }

    fun addRead(chapterId: Long) {
        viewModelScope.launch { repository.addReadCount(chapterId, 1) }
    }

    fun setInternalized(chapterId: Long, internalized: Boolean) {
        viewModelScope.launch { repository.setInternalized(chapterId, internalized) }
    }

    fun moveChapter(chapterId: Long, offset: Int) {
        viewModelScope.launch { repository.moveChapter(chapterId, offset) }
    }
}
