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
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Chapters are created from their book's screen, so this one always edits an existing chapter. */
class ChapterDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: BookRepository,
    links: LinkRepository,
    tags: TagRepository,
    cards: ItemCardSource,
) : ItemDetailViewModel(savedStateHandle, ItemType.CHAPTER, links, tags, cards) {

    var chapter by mutableStateOf<ChapterEntity?>(null)
        private set

    var titleText by mutableStateOf("")

    private val chapterFlow = repository.observeChapter(itemId.value)

    val book: StateFlow<BookEntity?> = chapterFlow.filterNotNull()
        .flatMapLatest { repository.observe(it.bookId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            chapterFlow.collect { current ->
                if (current == null) return@collect onMissing()
                if (chapter == null) {
                    titleText = current.title
                    chapter = current
                    markClean()
                }
                chapter = current
            }
        }
        markClean()
    }

    override fun formState(): Any = titleText

    fun save(close: Boolean) {
        val current = chapter ?: return
        if (titleText.isBlank()) {
            message = "A title is required"
            return
        }
        viewModelScope.launch {
            repository.renameChapter(current, titleText.trim())
            onSaved(current.id, close)
        }
    }

    fun addRead(delta: Int) {
        viewModelScope.launch { repository.addReadCount(itemId.value, delta) }
    }

    fun clearReads() {
        viewModelScope.launch { repository.clearReadCount(itemId.value) }
    }

    fun setInternalized(internalized: Boolean) {
        viewModelScope.launch { repository.setInternalized(itemId.value, internalized) }
    }

    fun delete() = deleteThen { repository.deleteChapter(it) }
}
