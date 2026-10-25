package com.example.xcommandcenter.ui.project

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.ProjectEntity
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.ProjectRepository
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.common.ItemDetailViewModel
import com.example.xcommandcenter.ui.navigation.Routes
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectRepository,
    links: LinkRepository,
) : ItemDetailViewModel(savedStateHandle, ItemType.PROJECT, links) {

    var project by mutableStateOf(
        ProjectEntity(title = "", parentId = savedStateHandle.get<Long>(Routes.ARG_PARENT)?.takeIf { it != 0L }),
    )
        private set

    /** All projects, for the parent picker and the children list. */
    val allProjects: StateFlow<List<ProjectEntity>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        val id = itemId.value
        if (id != 0L) viewModelScope.launch { project = repository.get(id) ?: return@launch onMissing() }
    }

    fun update(transform: (ProjectEntity) -> ProjectEntity) {
        project = transform(project)
    }

    fun save() {
        if (project.title.isBlank()) {
            message = "A title is required"
            return
        }
        val toSave = project.copy(id = itemId.value, title = project.title.trim())
        viewModelScope.launch {
            try {
                onSaved(repository.save(toSave))
            } catch (e: IllegalArgumentException) {
                message = e.message
            }
        }
    }

    /** Set when ticking "Completed" was refused because of open subprojects. */
    var blockedCompletion by mutableStateOf<BlockedCompletion?>(null)
        private set

    /** Applied at once, not on save; the form's copy of `completed` is never written. */
    fun setCompleted(completed: Boolean) {
        val id = itemId.value
        viewModelScope.launch {
            if (completed) {
                val open = repository.complete(id)
                if (open > 0) blockedCompletion = BlockedCompletion(id, project.title, open)
            } else {
                repository.reopen(id)
            }
        }
    }

    fun completeWithSubprojects() {
        val id = blockedCompletion?.projectId ?: return
        blockedCompletion = null
        viewModelScope.launch { repository.completeWithSubprojects(id) }
    }

    fun dismissBlockedCompletion() {
        blockedCompletion = null
    }

    fun delete() = deleteThen { repository.delete(it) }
}
