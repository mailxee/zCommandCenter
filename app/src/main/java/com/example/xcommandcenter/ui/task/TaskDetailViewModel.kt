package com.example.xcommandcenter.ui.task

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.TaskEntity
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.TagRepository
import com.example.xcommandcenter.data.repository.TaskRepository
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.Priority
import com.example.xcommandcenter.domain.RecurrenceType
import com.example.xcommandcenter.ui.common.ItemDetailViewModel
import com.example.xcommandcenter.ui.common.recurrenceError
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
    /** Null for a one-off task. */
    val recurrenceType: RecurrenceType? = null,
    val weekdays: Int = 0,
)

class TaskDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: TaskRepository,
    links: LinkRepository,
    tags: TagRepository,
) : ItemDetailViewModel(savedStateHandle, ItemType.TASK, links, tags) {

    var form by mutableStateOf(TaskForm())
        private set

    // Numeric schedule fields are edited as text so the user can clear them while typing.
    var dayOfMonthText by mutableStateOf("1")
    var intervalText by mutableStateOf("2")

    init {
        val id = itemId.value
        if (id != 0L) viewModelScope.launch {
            val task = repository.get(id) ?: return@launch onMissing()
            dayOfMonthText = task.dayOfMonth.toString()
            intervalText = task.intervalDays.toString()
            form = TaskForm(
                title = task.title,
                description = task.description,
                dueDate = task.dueDate,
                reminder = task.reminderAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() },
                priority = task.priority,
                completed = task.completed,
                createdAt = task.createdAt,
                recurrenceType = task.recurrenceType,
                weekdays = task.weekdays,
            )
        }
    }

    fun update(transform: (TaskForm) -> TaskForm) {
        form = transform(form)
    }

    fun save() {
        val f = form
        val error = when {
            f.title.isBlank() -> "A title is required"
            f.recurrenceType != null && f.dueDate == null -> "A repeating task needs a due date"
            else -> recurrenceError(f.recurrenceType, f.weekdays, dayOfMonthText, intervalText)
        }
        if (error != null) {
            message = error
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
            recurrenceType = f.recurrenceType,
            weekdays = f.weekdays,
            dayOfMonth = dayOfMonthText.toIntOrNull() ?: 1,
            intervalDays = intervalText.toIntOrNull() ?: 2,
        )
        viewModelScope.launch {
            val before = itemId.value.takeIf { it != 0L }?.let { repository.get(it) }
            val id = repository.save(task)
            val inPast = reminderAt != null && reminderAt <= System.currentTimeMillis() && !f.completed
            val spawned = f.completed && f.recurrenceType != null && (before == null || (!before.completed && !before.nextCreated))
            onSaved(
                id,
                when {
                    spawned -> "Saved. The next occurrence was added to your tasks."
                    inPast -> "Saved. The reminder time has passed, so it will not fire."
                    else -> "Saved"
                },
            )
        }
    }

    fun delete() = deleteThen { repository.delete(it) }
}
