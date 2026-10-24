package com.example.xcommandcenter.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.repository.LinkRepository
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
 * Shared behavior of every detail screen: which item is shown, its linked items, linking and
 * unlinking, and one-shot messages. An id of 0 means "new item, not saved yet"; links can be
 * added once it has been saved and has a real id.
 */
abstract class ItemDetailViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val type: ItemType,
    private val links: LinkRepository,
) : ViewModel() {

    private val _itemId = MutableStateFlow(savedStateHandle.get<Long>(Routes.ARG_ID) ?: 0L)
    val itemId: StateFlow<Long> = _itemId.asStateFlow()
    val ref: ItemRef get() = ItemRef(type, itemId.value)

    val linked: StateFlow<List<ItemIndexEntry>> = _itemId
        .flatMapLatest { id -> if (id == 0L) flowOf(emptyList()) else links.observeLinked(ItemRef(type, id)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Shown once in a snackbar, then cleared by the screen. */
    var message by mutableStateOf<String?>(null)
        protected set

    /** Set after a delete (or when the item no longer exists); the screen then navigates back. */
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

    protected fun onSaved(id: Long, note: String = "Saved") {
        _itemId.value = id
        // A new item now exists; if the process dies, the restored screen must reopen it, not a blank form.
        savedStateHandle[Routes.ARG_ID] = id
        message = note
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
