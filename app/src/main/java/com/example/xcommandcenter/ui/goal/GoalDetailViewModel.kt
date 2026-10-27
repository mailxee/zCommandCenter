package com.example.xcommandcenter.ui.goal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.GoalEntity
import com.example.xcommandcenter.data.repository.GoalRepository
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.ReminderRepository
import com.example.xcommandcenter.data.repository.TagRepository
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.common.ItemCardSource
import com.example.xcommandcenter.ui.common.ItemDetailViewModel
import kotlinx.coroutines.launch

class GoalDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: GoalRepository,
    links: LinkRepository,
    tags: TagRepository,
    cards: ItemCardSource,
    reminders: ReminderRepository,
) : ItemDetailViewModel(savedStateHandle, ItemType.GOAL, links, tags, cards, reminders) {

    var goal by mutableStateOf(GoalEntity(title = ""))
        private set

    init {
        val id = itemId.value
        if (id != 0L) viewModelScope.launch {
            goal = repository.get(id) ?: return@launch onMissing()
            markClean()
        }
        markClean()
    }

    override fun formState(): Any = goal

    fun update(transform: (GoalEntity) -> GoalEntity) {
        goal = transform(goal)
    }

    fun save(close: Boolean) {
        if (goal.title.isBlank()) {
            message = "A title is required"
            return
        }
        val toSave = goal.copy(id = itemId.value, title = goal.title.trim())
        viewModelScope.launch { onSaved(repository.save(toSave), close) }
    }

    fun delete() = deleteThen { repository.delete(it) }
}
