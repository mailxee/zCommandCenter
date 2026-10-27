package com.example.xcommandcenter.ui.task

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.ChecklistItemEntity
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

/** One checklist item being edited; [key] tells rows apart while their text changes. */
data class ChecklistLine(val key: Long, val text: String, val done: Boolean = false)

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
    /** Saved with the rest of the form. Open items don't stop the task from being completed. */
    val checklist: List<ChecklistLine> = emptyList(),
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

    private var nextLineKey = 0L

    init {
        val id = itemId.value
        if (id != 0L) viewModelScope.launch {
            val task = repository.get(id) ?: return@launch onMissing()
            val checklist = repository.checklist(id).map { ChecklistLine(nextLineKey++, it.text, it.done) }
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
                checklist = checklist,
            )
        }
    }

    fun update(transform: (TaskForm) -> TaskForm) {
        form = transform(form)
    }

    fun addChecklistItem(text: String) {
        if (text.isBlank()) return
        form = form.copy(checklist = form.checklist + ChecklistLine(nextLineKey++, text.trim()))
    }

    fun updateChecklistItem(key: Long, transform: (ChecklistLine) -> ChecklistLine) {
        form = form.copy(checklist = form.checklist.map { if (it.key == key) transform(it) else it })
    }

    fun removeChecklistItem(key: Long) {
        form = form.copy(checklist = form.checklist.filterNot { it.key == key })
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
        // Blank items are dropped; the rest keep their order.
        val checklist = f.checklist.filter { it.text.isNotBlank() }
            .map { ChecklistItemEntity(taskId = itemId.value, position = 0, text = it.text.trim(), done = it.done) }
        viewModelScope.launch { onSaved(repository.save(task, checklist)) }
    }

    fun delete() = deleteThen { repository.delete(it) }
}
