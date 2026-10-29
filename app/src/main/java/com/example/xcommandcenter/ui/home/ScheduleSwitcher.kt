package com.example.xcommandcenter.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcommandcenter.R
import com.example.xcommandcenter.data.entity.ScheduleEntity
import com.example.xcommandcenter.domain.Recurrence
import com.example.xcommandcenter.domain.shortName
import java.time.DayOfWeek

/** Which dialog the switcher has open. */
private enum class ScheduleDialog { NEW, RENAME, DUPLICATE, DAYS, DELETE }

/**
 * The Schedule tab's switcher: the schedule on show, and a menu to switch to another, add, rename,
 * duplicate or delete one, set the weekdays each is used on, and turn automatic switching on or
 * off. With it on, a line lists which schedule each weekday starts on.
 */
@Composable
fun ScheduleSwitcher(viewModel: HomeViewModel) {
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val active by viewModel.activeSchedule.collectAsStateWithLifecycle()
    val automatic by viewModel.scheduleAutomatic.collectAsStateWithLifecycle()
    var menu by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf<ScheduleDialog?>(null) }
    val current = active ?: return

    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Box {
            TextButton(onClick = { menu = true }) {
                Text(current.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(painterResource(R.drawable.ic_expand_more), contentDescription = "Switch schedule")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                schedules.forEach { schedule ->
                    DropdownMenuItem(
                        text = { Text(schedule.name) },
                        leadingIcon = { if (schedule.id == current.id) Icon(painterResource(R.drawable.ic_check), contentDescription = "On show") },
                        trailingIcon = daysText(schedule.days)?.let { { Text(it, style = MaterialTheme.typography.labelSmall) } },
                        onClick = { menu = false; viewModel.selectSchedule(schedule.id) },
                    )
                }
                HorizontalDivider()
                fun open(which: ScheduleDialog) {
                    menu = false
                    dialog = which
                }
                DropdownMenuItem(text = { Text("New schedule") }, onClick = { open(ScheduleDialog.NEW) })
                DropdownMenuItem(text = { Text("Rename") }, onClick = { open(ScheduleDialog.RENAME) })
                DropdownMenuItem(text = { Text("Duplicate") }, onClick = { open(ScheduleDialog.DUPLICATE) })
                DropdownMenuItem(text = { Text("Days to use it on…") }, onClick = { open(ScheduleDialog.DAYS) })
                DropdownMenuItem(text = { Text("Delete") }, enabled = schedules.size > 1, onClick = { open(ScheduleDialog.DELETE) })
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Switch automatically") },
                    leadingIcon = { if (automatic) Icon(painterResource(R.drawable.ic_check), contentDescription = "On") },
                    onClick = { menu = false; viewModel.setScheduleAutomatic(!automatic) },
                )
            }
        }
        if (automatic) {
            val plan = schedules.mapNotNull { s -> daysText(s.days)?.let { "${s.name} $it" } }
            Text(
                if (plan.isEmpty()) "Switching automatically: set the days for each schedule in the menu." else "Switching automatically: " + plan.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }

    when (dialog) {
        ScheduleDialog.NEW -> NameDialog("New schedule", "", "Add", onDismiss = { dialog = null }) {
            dialog = null
            viewModel.createSchedule(it)
        }
        ScheduleDialog.RENAME -> NameDialog("Rename schedule", current.name, "Rename", onDismiss = { dialog = null }) {
            dialog = null
            viewModel.renameSchedule(current.id, it)
        }
        ScheduleDialog.DUPLICATE -> NameDialog(
            "Duplicate \"${current.name}\"", "${current.name} copy", "Duplicate",
            note = "Every block is copied with its links, tags and reminders.",
            onDismiss = { dialog = null },
        ) {
            dialog = null
            viewModel.duplicateSchedule(current.id, it)
        }
        ScheduleDialog.DAYS -> DaysDialog(current, onDismiss = { dialog = null }) {
            dialog = null
            viewModel.setScheduleDays(current.id, it)
        }
        ScheduleDialog.DELETE -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Delete \"${current.name}\"?") },
            text = { Text("Its blocks are deleted too, with their links, tags and reminders.") },
            confirmButton = { TextButton(onClick = { dialog = null; viewModel.deleteSchedule(current.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        null -> Unit
    }
}

/** "Mon, Tue" for a weekday mask; null for none. */
private fun daysText(mask: Int): String? =
    Recurrence.daysFromMask(mask).sorted().takeIf { it.isNotEmpty() }?.joinToString(", ") { it.shortName() }

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, note: String? = null, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The weekdays [schedule] is switched to automatically; a day picked here is taken off any other schedule. */
@Composable
private fun DaysDialog(schedule: ScheduleEntity, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var mask by rememberSaveable { mutableIntStateOf(schedule.days) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Days for \"${schedule.name}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    DayOfWeek.entries.forEach { day ->
                        val bit = 1 shl (day.value - 1)
                        FilterChip(selected = mask and bit != 0, onClick = { mask = mask xor bit }, label = { Text(day.shortName()) })
                    }
                }
                Text(
                    "With \"Switch automatically\" on, each of these days starts on this schedule. A day picked here is taken off any other schedule.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(mask) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
