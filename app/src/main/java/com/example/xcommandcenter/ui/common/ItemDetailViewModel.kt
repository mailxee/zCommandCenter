package com.example.xcommandcenter.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.PlanEntity
import com.example.xcommandcenter.data.entity.TagEntity
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.TagRepository
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.navigation.Routes
import com.example.xcommandcenter.ui.plan.BlockedCompletion
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
 * added once it has been saved and has a real id. The check mark saves and closes the screen, back
 * to where it was opened from; the save button saves and stays.
 */
abstract class ItemDetailViewModel(
    private val savedStateHandle: SavedStateHandle,
    val type: ItemType,
    private val links: LinkRepository,
    private val tagRepository: TagRepository,
    private val cards: ItemCardSource,
) : ViewModel() {

    private val _itemId = MutableStateFlow(savedStateHandle.get<Long>(Routes.ARG_ID) ?: 0L)
    val itemId: StateFlow<Long> = _itemId.asStateFlow()
    val ref: ItemRef get() = ItemRef(type, itemId.value)

    /** The linked items as cards, the way their own tabs show them. */
    val linkedCards: StateFlow<List<ItemCardData>> = _itemId
        .flatMapLatest { id ->
            if (id == 0L) flowOf(emptyList()) else ItemRef(type, id).let { self -> cards.cardsFor(self, links.observeLinked(self)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Set when ticking a linked plan was refused because of open subplans. */
    var linkedBlocked by mutableStateOf<BlockedCompletion?>(null)
        private set

    fun setLinkedTaskCompleted(id: Long, completed: Boolean) {
        viewModelScope.launch { cards.setTaskCompleted(id, completed) }
    }

    fun setLinkedPlanCompleted(plan: PlanEntity, completed: Boolean) {
        viewModelScope.launch {
            if (completed) {
                val open = cards.completePlan(plan.id)
                if (open > 0) linkedBlocked = BlockedCompletion(plan.id, plan.title, open)
            } else {
                cards.reopenPlan(plan.id)
            }
        }
    }

    fun completeLinkedWithSubplans() {
        val id = linkedBlocked?.planId ?: return
        linkedBlocked = null
        viewModelScope.launch { cards.completePlanWithSubplans(id) }
    }

    fun dismissLinkedBlocked() {
        linkedBlocked = null
    }

    fun toggleLinkedHabitToday(id: Long) {
        viewModelScope.launch { cards.toggleHabitToday(id) }
    }

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

    /** After a save: [close] for the check mark, which also leaves the screen; the save button stays. */
    protected fun onSaved(id: Long, close: Boolean) {
        _itemId.value = id
        // A new item now exists; a restored screen must reopen it, not a blank form.
        savedStateHandle[Routes.ARG_ID] = id
        markClean()
        if (close) closed = true else message = "Saved"
    }

    /**
     * The screen's editable fields as one comparable value (data classes and strings), so a change
     * can be told from what was loaded or saved. What is saved at once (tags, links, read counts) is
     * not part of it.
     */
    protected abstract fun formState(): Any

    /** The form as last loaded or saved; null until the screen has called [markClean]. */
    private var cleanForm by mutableStateOf<Any?>(null)

    /** Records the current form as saved: at the end of each screen's init, after loading, and after a save. */
    protected fun markClean() {
        cleanForm = formState()
    }

    /** True when the form differs from what was last loaded or saved; leaving then asks first. */
    val hasUnsavedChanges: Boolean
        get() = cleanForm.let { it != null && it != formState() }

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
