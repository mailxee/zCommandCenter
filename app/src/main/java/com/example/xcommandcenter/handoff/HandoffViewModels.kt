package com.example.xcommandcenter.handoff

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.repository.HabitRepository
import com.example.xcommandcenter.domain.HabitHandoff
import com.example.xcommandcenter.share.CallerVerdict
import com.example.xcommandcenter.share.HandoffGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/** ADD_HABIT: whether the caller may ask; the habit editor does the rest. */
class AddHabitHandoffViewModel(private val gate: HandoffGate) : ViewModel() {
    private val _verdict = MutableStateFlow<CallerVerdict?>(null)

    /** Null while checking. */
    val verdict: StateFlow<CallerVerdict?> = _verdict.asStateFlow()

    /** Checks once per screen; a rotation keeps the answer. */
    fun start(callingPackage: String?) {
        if (started) return
        started = true
        viewModelScope.launch { _verdict.value = gate.check(callingPackage) }
    }

    private var started = false
}

/** What MARK_HABIT's screen shows. */
sealed interface MarkState {
    data object Checking : MarkState

    /** The request can't go ahead; nothing was written. [marked] if the day was marked already. */
    data class Message(val text: String, val marked: Boolean) : MarkState

    data class Ask(val text: String, val confirm: String) : MarkState

    /** Marked on the user's confirmation. */
    data object Marked : MarkState
}

/** MARK_HABIT: checks the caller and the request, then marks the day once the user confirms. */
class MarkHabitViewModel(private val gate: HandoffGate, private val habits: HabitRepository) : ViewModel() {
    private val _state = MutableStateFlow<MarkState>(MarkState.Checking)
    val state: StateFlow<MarkState> = _state.asStateFlow()

    private var started = false
    private var habitId = 0L
    private var date: LocalDate? = null

    fun start(callingPackage: String?, habitId: Long?, epochDay: Long?, today: LocalDate = LocalDate.now()) {
        if (started) return
        started = true
        viewModelScope.launch {
            when (val verdict = gate.check(callingPackage)) {
                is CallerVerdict.NotAllowed -> { _state.value = MarkState.Message(verdict.message, marked = false); return@launch }
                CallerVerdict.Unknown -> { _state.value = MarkState.Message(CallerVerdict.Unknown.MESSAGE, marked = false); return@launch }
                CallerVerdict.Allowed -> Unit
            }
            val day = epochDay?.let { runCatching { LocalDate.ofEpochDay(it) }.getOrNull() }
            val entity = habitId?.let { habits.get(it) }
            val habit = entity?.let { HabitHandoff.Habit(it.name, it.category, it.recurrence, it.startDate) }
            val marks = if (entity != null) habits.markDates(entity.id) else emptySet()
            this@MarkHabitViewModel.habitId = habitId ?: 0L
            date = day
            _state.value = when (val decision = HabitHandoff.markDecision(habitId != null && day != null, habit, day, marks, today)) {
                is HabitHandoff.MarkDecision.Refused -> MarkState.Message(decision.message, marked = false)
                is HabitHandoff.MarkDecision.AlreadyMarked -> MarkState.Message(decision.message, marked = true)
                is HabitHandoff.MarkDecision.Ask -> MarkState.Ask(decision.message, decision.confirm)
            }
        }
    }

    /** Writes the mark (with the time it was made); the screen closes once [MarkState.Marked]. */
    fun confirm() {
        val day = date ?: return
        if (_state.value !is MarkState.Ask) return
        viewModelScope.launch {
            habits.mark(habitId, day)
            _state.value = MarkState.Marked
        }
    }
}
