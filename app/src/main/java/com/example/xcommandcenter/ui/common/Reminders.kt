package com.example.xcommandcenter.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.example.xcommandcenter.R
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.domain.HabitStats
import com.example.xcommandcenter.domain.ItemType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The item's reminders, any number, each with a remove button, and "Add reminder". A habit's are
 * times of day, used on each scheduled day, each shown with [nextHabitReminder], when it next goes
 * off; a schedule block's are times of day, every day, offered relative to its [blockStart] minute;
 * anything else's are a date and a time, once. Saved with the form.
 */
@Composable
fun RemindersSection(viewModel: ItemDetailViewModel, nextHabitReminder: (Long) -> LocalDateTime? = { null }, blockStart: Int? = null) {
    val habit = viewModel.type == ItemType.HABIT
    val daily = viewModel.type.remindsDaily
    SectionTitle("Reminders")
    val now = System.currentTimeMillis()
    viewModel.reminders.forEach { time ->
        val past = !daily && time <= now
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val next = if (habit) nextHabitReminder(time)?.let { " · next ${it.display()}" }.orEmpty() else ""
            val before = blockStart?.let { beforeStartText(time.toInt(), it) }?.let { " · $it" }.orEmpty()
            Text(
                reminderText(viewModel.type, time) + next + before + if (past) " (past)" else "",
                style = MaterialTheme.typography.bodyLarge,
                color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.removeReminder(time) }) {
                Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove reminder")
            }
        }
    }
    if (blockStart != null) {
        AddBlockReminderButton(blockStart, onAdd = viewModel::addReminder)
    } else {
        AddReminderButton(habit, onAdd = viewModel::addReminder)
    }
    if (habit && viewModel.reminders.isNotEmpty()) {
        Text(
            "On each scheduled day, unless the day is already marked.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** How a block's reminder at [minute] relates to the block's [start]: "at start", "15 min before". */
fun beforeStartText(minute: Int, start: Int): String {
    val before = Math.floorMod(start - minute, MINUTES_PER_DAY)
    return when {
        before == 0 -> "at start"
        before < 60 -> "$before min before"
        before % 60 == 0 && before < 12 * 60 -> "${before / 60} h before"
        before < 12 * 60 -> "${before / 60} h ${before % 60} min before"
        else -> "${MINUTES_PER_DAY - before} min after start"
    }
}

private const val MINUTES_PER_DAY = 24 * 60

/** "Add reminder" for a block: a choice relative to its [start] minute, or any time of day. */
@Composable
private fun AddBlockReminderButton(start: Int, onAdd: (Long) -> Unit) {
    var menu by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { menu = true }) { Text("Add reminder") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            listOf(0, 5, 10, 15, 30, 60).forEach { before ->
                DropdownMenuItem(
                    text = { Text(if (before == 0) "At start" else if (before == 60) "1 hour before" else "$before min before") },
                    onClick = {
                        menu = false
                        onAdd(Math.floorMod(start - before, MINUTES_PER_DAY).toLong())
                    },
                )
            }
            DropdownMenuItem(text = { Text("Pick a time…") }, onClick = { menu = false; picking = true })
        }
    }
    if (picking) {
        TimePickerDialog(LocalTime.of(start / 60, start % 60), onDismiss = { picking = false }) { time ->
            picking = false
            onAdd(time.hour * 60L + time.minute)
        }
    }
}

/** Picks a time for a habit, or a date and then a time for anything else. */
@Composable
private fun AddReminderButton(habit: Boolean, onAdd: (Long) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var pickedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    OutlinedButton(onClick = { picking = true }) { Text("Add reminder") }
    val suggested = LocalTime.now().plusMinutes(5).withSecond(0).withNano(0)
    if (picking && habit) {
        TimePickerDialog(LocalTime.of(9, 0), onDismiss = { picking = false }) { time ->
            picking = false
            onAdd(time.hour * 60L + time.minute)
        }
    } else if (picking) {
        DatePickerDialogFor(LocalDate.now(), onDismiss = { picking = false }) {
            picking = false
            pickedDate = it
        }
    }
    pickedDate?.let { date ->
        TimePickerDialog(suggested, onDismiss = { pickedDate = null }) { time ->
            pickedDate = null
            onAdd(LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }
    }
}

/** A reminder as shown: a habit's time of day, a block's "Every day at …", or anything else's date and time. */
fun reminderText(type: ItemType, time: Long): String =
    if (type.remindsDaily) {
        (if (type == ItemType.BLOCK) "Every day at " else "") + LocalTime.of((time / 60).toInt(), (time % 60).toInt()).display()
    } else {
        Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDateTime().display()
    }

/**
 * When a habit's reminder at [minute] of the day next goes off and shows: on a day the habit is
 * due (given its [marks], see [HabitStats.dueDates]), at a time still ahead of [now], and not
 * today if today is already marked.
 */
fun HabitEntity.nextReminder(minute: Long, now: LocalDateTime, marks: Set<LocalDate>): LocalDateTime? {
    val time = LocalTime.of((minute / 60).toInt(), (minute % 60).toInt())
    val from = if (now.toLocalDate() in marks) now.toLocalDate().atTime(LocalTime.MAX) else now
    return HabitStats.nextReminder(category, recurrence, startDate, marks, time, from)
}

/**
 * The reminder line on an item's card: a habit's times of day and when the next one is (given the
 * [habit] and its [marks]), or the next reminder still to come on anything else (with how many
 * more follow). Null when there is nothing to show.
 */
fun reminderLine(
    type: ItemType,
    times: List<Long>,
    now: Long = System.currentTimeMillis(),
    habit: HabitEntity? = null,
    marks: Set<LocalDate> = emptySet(),
): String? {
    if (type == ItemType.BLOCK) {
        if (times.isEmpty()) return null
        return (if (times.size == 1) "Reminder " else "Reminders ") +
            times.sorted().joinToString(", ") { LocalTime.of((it / 60).toInt(), (it % 60).toInt()).display() }
    }
    if (type == ItemType.HABIT) {
        if (times.isEmpty()) return null
        val at = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDateTime()
        val next = habit?.let { h -> times.mapNotNull { h.nextReminder(it, at, marks) }.minOrNull() }
        return (if (times.size == 1) "Reminder " else "Reminders ") + times.sorted().joinToString(", ") { reminderText(type, it) } +
            next?.let { " · next ${it.display()}" }.orEmpty()
    }
    val upcoming = times.filter { it > now }.sorted()
    val next = upcoming.firstOrNull() ?: return null
    val more = upcoming.size - 1
    return "Reminder ${reminderText(type, next)}" + if (more > 0) " (+$more more)" else ""
}
