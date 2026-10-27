package com.example.xcommandcenter.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.TagEntity
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.TagRepository
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.navigation.Routes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shared behavior of every detail screen: which item is shown, its linked items and tags, linking
 * and tagging, and one-shot messages. An id of 0 means "new item, not saved yet"; links can be
 * added once it has been saved and has a real id. Saving closes the screen, back to where it was
 * opened from.
 */
abstract class ItemDetailViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val type: ItemType,
    private val links: LinkRepository,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _itemId = MutableStateFlow(savedStateHandle.get<Long>(Routes.ARG_ID) ?: 0L)
    val itemId: StateFlow<Long> = _itemId.asStateFlow()
    val ref: ItemRef get() = ItemRef(type, itemId.value)

    val linked: StateFlow<List<ItemIndexEntry>> = _itemId
        .flatMapLatest { id -> if (id == 0L) flowOf(emptyList()) else links.observeLinked(ItemRef(type, id)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** This item's tags; like links, they can be added once the item has been saved. */
    val tags: StateFlow<List<TagEntity>> = _itemId
        .flatMapLatest { id -> if (id == 0L) flowOf(emptyList()) else tagRepository.observeTags(ItemRef(type, id)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Every tag in use, offered as suggestions when adding one. */
    val allTags: StateFlow<List<TagEntity>> = tagRepository.observeInUse()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addTag(name: String) {
        if (itemId.value == 0L) return
        viewModelScope.launch { tagRepository.add(ref, name) }
    }

    fun removeTag(tagId: Long) {
        viewModelScope.launch { tagRepository.remove(ref, tagId) }
    }

    /** Shown once in a snackbar, then cleared by the screen. */
    var message by mutableStateOf<String?>(null)
        protected set

    /** Set after a save or a delete (or when the item no longer exists); the screen then navigates back. */
    var closed by mutableStateOf(false)
        protected set

    fun messageShown() {
        message = null
    }

    fun searchLinkTargets(tab: ItemType, query: String): Flow<List<ItemIndexEntry>> = links.search(tab, query)

    fun link(target: ItemRef) {
        if (itemId.value == 0L) return
        viewModelScope.launch { links.link(ref, target) }
    }

    fun unlink(target: ItemRef) {
        viewModelScope.launch { links.unlink(ref, target) }
    }

    protected fun onSaved(id: Long) {
        _itemId.value = id
        // A new item now exists; if the process dies before the screen closes, the restored screen must reopen it, not a blank form.
        savedStateHandle[Routes.ARG_ID] = id
        closed = true
    }

    protected fun onMissing() {
        closed = true
    }

    /** Runs [block] against the saved item and closes the screen afterwards. */
    protected fun deleteThen(block: suspend (Long) -> Unit) {
        val id = itemId.value
        viewModelScope.launch {
            if (id != 0L) block(id)
            closed = true
        }
    }
}
