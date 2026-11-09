package com.example.xcommandcenter.ui.idea

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.IdeaEntity
import com.example.xcommandcenter.data.entity.IdeaListItemEntity
import com.example.xcommandcenter.data.repository.IdeaRepository
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.ReminderRepository
import com.example.xcommandcenter.data.repository.TagRepository
import com.example.xcommandcenter.domain.IdeaKind
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.common.ItemCardSource
import com.example.xcommandcenter.ui.common.ItemDetailViewModel
import com.example.xcommandcenter.ui.common.ListLine
import com.example.xcommandcenter.ui.common.ListLines
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

    /** The idea's list, edited here and saved with the idea. */
    var list by mutableStateOf<List<ListLine>>(emptyList())
        private set

    private val lines = ListLines()

    /** All ideas, for the "Under" picker and the list of ideas under this one. */
    val allIdeas: StateFlow<List<IdeaEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        val id = itemId.value
        if (id != 0L) {
            viewModelScope.launch {
                idea = repository.get(id) ?: return@launch onMissing()
                list = repository.listItems(id).map { lines.line(it.text) }
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

    override fun formState(): Any = idea to list

    override val selfName: String get() = "This ${idea.kind.label.lowercase()}"

    fun update(transform: (IdeaEntity) -> IdeaEntity) {
        idea = transform(idea)
    }

    fun addListItem(text: String) {
        list = lines.add(list, text)
    }

    fun updateListItem(key: Long, transform: (ListLine) -> ListLine) {
        list = lines.update(list, key, transform)
    }

    fun removeListItem(key: Long) {
        list = lines.remove(list, key)
    }

    fun moveListItem(from: Int, to: Int) {
        list = lines.move(list, from, to)
    }

    /**
     * Puts existing ideas under this one straight away, like linking: they keep everything else.
     * Only for a saved idea; the picker leaves out this idea and the ones above it.
     */
    fun addUnder(ids: List<Long>) {
        val parentId = itemId.value
        if (parentId == 0L || ids.isEmpty()) return
        viewModelScope.launch {
            try {
                ids.forEach { repository.moveUnder(it, parentId) }
            } catch (e: IllegalArgumentException) {
                message = e.message
            }
        }
    }

    fun save(close: Boolean) {
        if (idea.title.isBlank()) {
            message = "Write the idea first"
            return
        }
        val toSave = idea.copy(id = itemId.value, title = idea.title.trim(), body = idea.body.trim())
        val listItems = list.filter { it.text.isNotBlank() }.map { IdeaListItemEntity(ideaId = itemId.value, position = 0, text = it.text.trim()) }
        viewModelScope.launch {
            try {
                onSaved(repository.save(toSave, listItems), close)
            } catch (e: IllegalArgumentException) {
                message = e.message
            }
        }
    }

    fun delete() = deleteThen { repository.delete(it) }
}
