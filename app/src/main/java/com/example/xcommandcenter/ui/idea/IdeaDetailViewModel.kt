package com.example.xcommandcenter.ui.idea

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.IdeaEntity
import com.example.xcommandcenter.data.repository.IdeaRepository
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.ReminderRepository
import com.example.xcommandcenter.data.repository.TagRepository
import com.example.xcommandcenter.domain.IdeaKind
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.common.ItemCardSource
import com.example.xcommandcenter.ui.common.ItemDetailViewModel
import com.example.xcommandcenter.ui.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class IdeaDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: IdeaRepository,
    links: LinkRepository,
    tags: TagRepository,
    cards: ItemCardSource,
    reminders: ReminderRepository,
) : ItemDetailViewModel(savedStateHandle, ItemType.IDEA, links, tags, cards, reminders) {

    var idea by mutableStateOf(
        IdeaEntity(title = "", parentId = savedStateHandle.get<Long>(Routes.ARG_PARENT)?.takeIf { it != 0L }),
    )
        private set

    /** All ideas, for the "Under" picker and the list of ideas under this one. */
    val allIdeas: StateFlow<List<IdeaEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        val id = itemId.value
        if (id != 0L) {
            viewModelScope.launch {
                idea = repository.get(id) ?: return@launch onMissing()
                markClean()
            }
        } else {
            // A new idea under another starts one kind narrower than its parent: a belief under a vision.
            val parentId = idea.parentId
            if (parentId != null) viewModelScope.launch {
                val parent = repository.get(parentId) ?: return@launch
                idea = idea.copy(kind = IdeaKind.entries.getOrElse(parent.kind.ordinal + 1) { IdeaKind.IDEA })
                markClean()
            }
        }
        markClean()
    }

    override fun formState(): Any = idea

    override val selfName: String get() = "This ${idea.kind.label.lowercase()}"

    fun update(transform: (IdeaEntity) -> IdeaEntity) {
        idea = transform(idea)
    }

    fun save(close: Boolean) {
        if (idea.title.isBlank()) {
            message = "Write the idea first"
            return
        }
        val toSave = idea.copy(id = itemId.value, title = idea.title.trim(), body = idea.body.trim())
        viewModelScope.launch {
            try {
                onSaved(repository.save(toSave), close)
            } catch (e: IllegalArgumentException) {
                message = e.message
            }
        }
    }

    fun delete() = deleteThen { repository.delete(it) }
}
