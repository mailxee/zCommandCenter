package com.example.xcommandcenter.ui.task

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.TaskRepository
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.Priority
import com.example.xcommandcenter.ui.common.ItemDetailViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class TaskForm(
    val title: String = "",
    val description: String = "",
    val dueDate: LocalDate? = null,
    val reminder: LocalDateTime? = null,
    val priority: Priority = Priority.MEDIUM,
    val completed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

class TaskDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
    links: LinkRepository,
) : ItemDetailViewModel(savedStateHandle, ItemType.TASK, links) {

    var form by mutableStateOf(TaskForm())
        private set

    init {
        val id = itemId.value
        if (id != 0L) viewModelScope.launch {
            val task = repository.get(id) ?: return@launch onMissing()
            form = TaskForm(
                title = task.title,
                description = task.description,
                dueDate = task.dueDate,
                reminder = task.reminderAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() },
                priority = task.priority,
                completed = task.completed,
                createdAt = task.createdAt,
            )
        }
    }

    fun update(transform: (TaskForm) -> TaskForm) {
        form = transform(form)
    }

    fun save() {
        val f = form
        if (f.title.isBlank()) {
            message = "A title is required"
            return
        }
        val reminderAt = f.reminder?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        val task = TaskEntity(
            id = itemId.value,
            title = f.title.trim(),
            description = f.description.trim(),
            dueDate = f.dueDate,
            reminderAt = reminderAt,
            priority = f.priority,
            completed = f.completed,
            createdAt = f.createdAt,
        )
        viewModelScope.launch {
            val id = repository.save(task)
            val inPast = reminderAt != null && reminderAt <= System.currentTimeMillis()
            onSaved(id, if (inPast) "Saved. The reminder time has passed, so it will not fire." else "Saved")
        }
    }

    fun delete() = deleteThen { repository.delete(it) }
}
