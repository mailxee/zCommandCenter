package com.example.xcommandcenter.ui.block

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.xcommandcenter.data.entity.BlockEntity
import com.example.xcommandcenter.data.repository.BlockRepository
import com.example.xcommandcenter.data.repository.LinkRepository
import com.example.xcommandcenter.data.repository.ReminderRepository
import com.example.xcommandcenter.data.repository.TagRepository
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.common.ItemCardSource
import com.example.xcommandcenter.ui.common.ItemDetailViewModel
import com.example.xcommandcenter.ui.navigation.Routes
import kotlinx.coroutines.launch
import java.time.LocalTime

class BlockDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: BlockRepository,
    links: LinkRepository,
    tags: TagRepository,
    cards: ItemCardSource,
    reminders: ReminderRepository,
) : ItemDetailViewModel(savedStateHandle, ItemType.BLOCK, links, tags, cards, reminders) {

    /** A new block starts where it was tapped on the schedule, else at the next half hour; it lasts an hour. */
    var block by mutableStateOf(
        (savedStateHandle.get<Int>(Routes.ARG_START)?.takeIf { it in 0 until DAY } ?: nextHalfHour()).let { start ->
            BlockEntity(title = "", startMinute = start, endMinute = minOf(start + 60, DAY))
        },
    )
        private set

    init {
        val id = itemId.value
        if (id != 0L) viewModelScope.launch {
            block = repository.get(id) ?: return@launch onMissing()
            markClean()
        }
        markClean()
    }

    override fun formState(): Any = block

    fun update(transform: (BlockEntity) -> BlockEntity) {
        block = transform(block)
    }

    /** Moves the block, keeping its length (up to midnight), and its reminders with it. */
    fun setStart(minute: Int) {
        val length = block.endMinute - block.startMinute
        shiftReminders(minute - block.startMinute)
        block = block.copy(startMinute = minute, endMinute = minOf(minute + length, DAY))
    }

    /** Midnight as an end is the end of the day. */
    fun setEnd(minute: Int) {
        val end = if (minute == 0) DAY else minute
        if (end <= block.startMinute) {
            message = "The end must be after the start"
            return
        }
        block = block.copy(endMinute = end)
    }

    fun save(close: Boolean) {
        if (block.title.isBlank()) {
            message = "A title is required"
            return
        }
        val toSave = block.copy(id = itemId.value, title = block.title.trim(), notes = block.notes.trim())
        viewModelScope.launch { onSaved(repository.save(toSave), close) }
    }

    fun delete() = deleteThen { repository.delete(it) }

    companion object {
        const val DAY = 24 * 60

        private fun nextHalfHour(): Int {
            val now = LocalTime.now()
            return minOf((now.hour * 60 + now.minute) / 30 * 30 + 30, DAY - 30)
        }
    }
}
