package com.example.xcommandcenter.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
private val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun LocalDate.display(): String = format(dateFormat)
fun LocalDateTime.display(): String = "${toLocalDate().display()}, ${toLocalTime().format(timeFormat)}"

/** A labelled date with a picker and a clear button. */
@Composable
fun DateField(label: String, date: LocalDate?, onChange: (LocalDate?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { picking = true }) { Text(date?.display() ?: "Set") }
        if (date != null) TextButton(onClick = { onChange(null) }) { Text("Clear") }
    }
    if (picking) {
        DatePickerDialogFor(date ?: LocalDate.now(), onDismiss = { picking = false }) {
            picking = false
            onChange(it)
        }
    }
}

/** A labelled date and time: picks the date first, then the time. */
@Composable
fun DateTimeField(label: String, value: LocalDateTime?, onChange: (LocalDateTime?) -> Unit) {
    var pickedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { pickingDate = true }) { Text(value?.display() ?: "Set") }
        if (value != null) TextButton(onClick = { onChange(null) }) { Text("Clear") }
    }
    if (pickingDate) {
        DatePickerDialogFor(value?.toLocalDate() ?: LocalDate.now(), onDismiss = { pickingDate = false }) {
            pickingDate = false
            pickedDate = it
        }
    }
    pickedDate?.let { date ->
        TimePickerDialog(
            initial = value?.toLocalTime() ?: LocalTime.now().plusMinutes(5).withSecond(0).withNano(0),
            onDismiss = { pickedDate = null },
        ) { time ->
            pickedDate = null
            onChange(LocalDateTime.of(date, time))
        }
    }
}

@Composable
private fun DatePickerDialogFor(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    // The Material date picker works in UTC midnight millis.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
private fun TimePickerDialog(initial: LocalTime, onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Section heading used inside detail screens. */
@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

/** List rows inside cards and dialogs take the container's color instead of painting their own. */
@Composable
fun clearListItemColors(): ListItemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
