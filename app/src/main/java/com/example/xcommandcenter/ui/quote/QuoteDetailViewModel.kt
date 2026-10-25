package com.example.xcommandcenter.ui.quote

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.QuoteEntity
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.QuoteRepository
import com.example.xcommandcenter.data.repository.TagRepository
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.common.ItemDetailViewModel
import kotlinx.coroutines.launch

class QuoteDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: QuoteRepository,
    links: LinkRepository,
    tags: TagRepository,
) : ItemDetailViewModel(savedStateHandle, ItemType.QUOTE, links, tags) {

    var quote by mutableStateOf(QuoteEntity(text = ""))
        private set

    init {
        val id = itemId.value
        if (id != 0L) viewModelScope.launch { quote = repository.get(id) ?: return@launch onMissing() }
    }

    fun update(transform: (QuoteEntity) -> QuoteEntity) {
        quote = transform(quote)
    }

    fun save() {
        if (quote.text.isBlank()) {
            message = "The quote text is required"
            return
        }
        val toSave = quote.copy(
            id = itemId.value,
            text = quote.text.trim(),
            source = quote.source.trim(),
            author = quote.author.trim(),
        )
        viewModelScope.launch { onSaved(repository.save(toSave)) }
    }

    fun delete() = deleteThen { repository.delete(it) }
}
