package com.example.xcommandcenter.ui.habit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.data.repository.HabitRepository
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.TagRepository
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.common.ItemDetailViewModel
import com.example.xcommandcenter.ui.common.recurrenceError
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class HabitDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: HabitRepository,
    links: LinkRepository,
    tags: TagRepository,
) : ItemDetailViewModel(savedStateHandle, ItemType.HABIT, links, tags) {

    /** The habit being edited. */
    var habit by mutableStateOf(HabitEntity(name = ""))
        private set

    /** The last saved version; history and streaks follow its schedule, not unsaved edits. */
    var saved by mutableStateOf<HabitEntity?>(null)
        private set

    // Numeric fields are edited as text so the user can clear them while typing.
    var dayOfMonthText by mutableStateOf("1")
    var intervalText by mutableStateOf("2")

    val marks: StateFlow<Set<LocalDate>> = itemId
        .flatMapLatest { id -> if (id == 0L) flowOf(emptySet()) else repository.observeMarks(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        val id = itemId.value
        if (id != 0L) viewModelScope.launch {
            val loaded = repository.get(id) ?: return@launch onMissing()
            load(loaded)
        }
    }

    private fun load(loaded: HabitEntity) {
        habit = loaded
        saved = loaded
        dayOfMonthText = loaded.dayOfMonth.toString()
        intervalText = loaded.intervalDays.toString()
    }

    fun update(transform: (HabitEntity) -> HabitEntity) {
        habit = transform(habit)
    }

    fun toggleMark(date: LocalDate) {
        val id = itemId.value
        if (id != 0L) viewModelScope.launch { repository.toggleMark(id, date) }
    }

    fun save() {
        val dayOfMonth = dayOfMonthText.toIntOrNull()
        val interval = intervalText.toIntOrNull()
        val error = if (habit.name.isBlank()) {
            "A name is required"
        } else {
            recurrenceError(habit.recurrenceType, habit.weekdays, dayOfMonthText, intervalText)
        }
        if (error != null) {
            message = error
            return
        }
        val toSave = habit.copy(
            id = itemId.value,
            name = habit.name.trim(),
            dayOfMonth = dayOfMonth ?: habit.dayOfMonth,
            intervalDays = interval ?: habit.intervalDays,
        )
        viewModelScope.launch {
            val id = repository.save(toSave)
            load(toSave.copy(id = id))
            onSaved(id)
        }
    }

    fun delete() = deleteThen { repository.delete(it) }
}
