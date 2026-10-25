package com.example.xcommandcenter.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.xcommandcenter.domain.Recurrence
import com.example.xcommandcenter.domain.RecurrenceType
import com.example.xcommandcenter.domain.shortName
import java.time.DayOfWeek

/**
 * Schedule editor shared by habits and tasks. Numbers are edited as text so they can be cleared
 * while typing; check them with [recurrenceError] before saving. When [noneLabel] is given, a
 * first chip with that label selects "no schedule" (type null).
 */
@Composable
fun RecurrenceFields(
    type: RecurrenceType?,
    weekdays: Int,
    dayOfMonthText: String,
    intervalText: String,
    onType: (RecurrenceType?) -> Unit,
    onWeekdays: (Int) -> Unit,
    onDayOfMonth: (String) -> Unit,
    onInterval: (String) -> Unit,
    intervalHelp: String,
    noneLabel: String? = null,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (noneLabel != null) {
            FilterChip(selected = type == null, onClick = { onType(null) }, label = { Text(noneLabel) })
        }
        RecurrenceType.entries.forEach { option ->
            FilterChip(selected = type == option, onClick = { onType(option) }, label = { Text(option.label) })
        }
    }
    when (type) {
        null, RecurrenceType.DAILY -> Unit
        RecurrenceType.WEEKLY -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val days = Recurrence.daysFromMask(weekdays)
            DayOfWeek.entries.forEach { day ->
                FilterChip(
                    selected = day in days,
                    onClick = { onWeekdays(Recurrence.maskFromDays(if (day in days) days - day else days + day)) },
                    label = { Text(day.shortName()) },
                )
            }
        }
        RecurrenceType.MONTHLY -> NumberField(
            "Day of month (1-31)",
            dayOfMonthText,
            onDayOfMonth,
            help = "In shorter months, days past the end fall on the last day.",
        )
        RecurrenceType.EVERY_N_DAYS -> NumberField("N (days)", intervalText, onInterval, help = intervalHelp)
    }
}

/** The message to show for an invalid schedule, or null when it can be saved. */
fun recurrenceError(type: RecurrenceType?, weekdays: Int, dayOfMonthText: String, intervalText: String): String? {
    val dayOfMonth = dayOfMonthText.toIntOrNull()
    val interval = intervalText.toIntOrNull()
    return when {
        type == RecurrenceType.WEEKLY && weekdays == 0 -> "Pick at least one weekday"
        type == RecurrenceType.MONTHLY && (dayOfMonth == null || dayOfMonth !in 1..31) -> "Day of month must be between 1 and 31"
        type == RecurrenceType.EVERY_N_DAYS && (interval == null || interval !in 1..365) -> "N must be between 1 and 365"
        else -> null
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, help: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter(Char::isDigit).take(3)) },
        label = { Text(label) },
        supportingText = { Text(help) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
