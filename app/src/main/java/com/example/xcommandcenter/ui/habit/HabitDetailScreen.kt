package com.example.xcommandcenter.ui.habit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcommandcenter.domain.HabitCategory
import com.example.xcommandcenter.domain.HabitStats
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.common.DetailScaffold
import com.example.xcommandcenter.ui.common.LinkedItemsSection
import com.example.xcommandcenter.ui.common.RecurrenceFields
import com.example.xcommandcenter.ui.common.SectionTitle
import com.example.xcommandcenter.ui.common.TagsSection
import com.example.xcommandcenter.ui.home.times
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val chipDate = DateTimeFormatter.ofPattern("EEE d MMM")

@Composable
fun HabitDetailScreen(
    onBack: () -> Unit,
    onOpen: (ItemRef) -> Unit,
    viewModel: HabitDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val id by viewModel.itemId.collectAsStateWithLifecycle()
    val linked by viewModel.linked.collectAsStateWithLifecycle()
    val marks by viewModel.marks.collectAsStateWithLifecycle()
    val habit = viewModel.habit

    DetailScaffold(
        title = if (id == 0L) "New habit" else "Habit",
        viewModel = viewModel,
        onBack = onBack,
        onSave = viewModel::save,
        onDelete = if (id == 0L) null else viewModel::delete,
        deleteWarning = "This also removes its history and all of its links.",
    ) {
        OutlinedTextField(
            value = habit.name,
            onValueChange = { v -> viewModel.update { it.copy(name = v) } },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle("Category")
        HabitCategory.entries.forEach { category ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = habit.category == category,
                    onClick = { viewModel.update { it.copy(category = category) } },
                )
                Text(
                    when (category) {
                        HabitCategory.POSITIVE -> "Positive: mark each time you do it"
                        HabitCategory.NEGATIVE -> "Negative: mark only when you miss it"
                    },
                )
            }
        }

        SectionTitle("Recurrence")
        RecurrenceFields(
            type = habit.recurrenceType,
            weekdays = habit.weekdays,
            dayOfMonthText = viewModel.dayOfMonthText,
            intervalText = viewModel.intervalText,
            onType = { type -> if (type != null) viewModel.update { it.copy(recurrenceType = type) } },
            onWeekdays = { mask -> viewModel.update { it.copy(weekdays = mask) } },
            onDayOfMonth = { viewModel.dayOfMonthText = it },
            onInterval = { viewModel.intervalText = it },
            intervalHelp = "Counted from the habit's start date.",
        )

        OutlinedTextField(
            value = habit.notes,
            onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
            label = { Text("Notes") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        viewModel.saved?.let { saved ->
            val today = LocalDate.now()
            val positive = saved.category == HabitCategory.POSITIVE
            val streak = HabitStats.streak(saved.category, saved.recurrence, saved.startDate, marks, today)
            SectionTitle("History")
            Text(
                if (positive) "Current streak: $streak · Done ${times(marks.size)}"
                else "Clean streak: $streak · Missed ${times(marks.size)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                if (positive) "Tap an occurrence to mark it done. Unmarked days are unknown and do not count."
                else "Tap an occurrence to mark it missed. Unmarked occurrences count as done.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                HabitStats.recentOccurrences(saved.recurrence, saved.startDate, today, 21).forEach { date ->
                    FilterChip(
                        selected = date in marks,
                        onClick = { viewModel.toggleMark(date) },
                        label = { Text(if (date == today) "Today" else date.format(chipDate)) },
                    )
                }
            }
        }

        TagsSection(viewModel)
        LinkedItemsSection(viewModel, linked, onOpen)
    }
}
