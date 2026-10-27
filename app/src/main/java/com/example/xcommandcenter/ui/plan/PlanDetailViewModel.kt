package com.example.xcommandcenter.ui.plan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.PlanEntity
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.PlanRepository
import com.example.xcommandcenter.data.repository.TagRepository
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.common.ItemCardSource
import com.example.xcommandcenter.ui.common.ItemDetailViewModel
import com.example.xcommandcenter.ui.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlanDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: PlanRepository,
    links: LinkRepository,
    tags: TagRepository,
    cards: ItemCardSource,
) : ItemDetailViewModel(savedStateHandle, ItemType.PLAN, links, tags, cards) {

    var plan by mutableStateOf(
        PlanEntity(title = "", parentId = savedStateHandle.get<Long>(Routes.ARG_PARENT)?.takeIf { it != 0L }),
    )
        private set

    /** All plans, for the parent picker and the children list. */
    val allPlans: StateFlow<List<PlanEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        val id = itemId.value
        if (id != 0L) viewModelScope.launch { plan = repository.get(id) ?: return@launch onMissing() }
    }

    fun update(transform: (PlanEntity) -> PlanEntity) {
        plan = transform(plan)
    }

    fun save(close: Boolean) {
        if (plan.title.isBlank()) {
            message = "A title is required"
            return
        }
        val toSave = plan.copy(id = itemId.value, title = plan.title.trim())
        viewModelScope.launch {
            try {
                onSaved(repository.save(toSave), close)
            } catch (e: IllegalArgumentException) {
                message = e.message
            }
        }
    }

    /** Set when ticking "Completed" was refused because of open subplans. */
    var blockedCompletion by mutableStateOf<BlockedCompletion?>(null)
        private set

    /** Applied at once, not on save; the form's copy of `completed` is never written. */
    fun setCompleted(completed: Boolean) {
        val id = itemId.value
        viewModelScope.launch {
            if (completed) {
                val open = repository.complete(id)
                if (open > 0) blockedCompletion = BlockedCompletion(id, plan.title, open)
            } else {
                repository.reopen(id)
            }
        }
    }

    fun completeWithSubplans() {
        val id = blockedCompletion?.planId ?: return
        blockedCompletion = null
        viewModelScope.launch { repository.completeWithSubplans(id) }
    }

    fun dismissBlockedCompletion() {
        blockedCompletion = null
    }

    fun delete() = deleteThen { repository.delete(it) }
}
