package com.example.xcommandcenter.ui.task

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.Priority
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.common.DateField
import com.example.xcommandcenter.ui.common.DateTimeField
import com.example.xcommandcenter.ui.common.DetailScaffold
import com.example.xcommandcenter.ui.common.LinkedItemsSection
import com.example.xcommandcenter.ui.common.RecurrenceFields
import com.example.xcommandcenter.ui.common.SectionTitle
import com.example.xcommandcenter.ui.common.TagsSection

@Composable
fun TaskDetailScreen(
    onBack: () -> Unit,
    onOpen: (ItemRef) -> Unit,
    viewModel: TaskDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val id by viewModel.itemId.collectAsStateWithLifecycle()
    val linked by viewModel.linked.collectAsStateWithLifecycle()
    val form = viewModel.form

    DetailScaffold(
        title = if (id == 0L) "New task" else "Task",
        viewModel = viewModel,
        onBack = onBack,
        onSave = viewModel::save,
        onDelete = if (id == 0L) null else viewModel::delete,
    ) {
        OutlinedTextField(
            value = form.title,
            onValueChange = { v -> viewModel.update { it.copy(title = v) } },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.description,
            onValueChange = { v -> viewModel.update { it.copy(description = v) } },
            label = { Text("Description") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        SectionTitle("Priority")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Priority.entries.forEachIndexed { index, priority ->
                SegmentedButton(
                    selected = form.priority == priority,
                    onClick = { viewModel.update { it.copy(priority = priority) } },
                    shape = SegmentedButtonDefaults.itemShape(index, Priority.entries.size),
                    label = { Text(priority.label, maxLines = 1) },
                )
            }
        }
        DateField("Due date", form.dueDate) { date -> viewModel.update { it.copy(dueDate = date) } }
        DateTimeField("Reminder", form.reminder) { time -> viewModel.update { it.copy(reminder = time) } }
        SectionTitle("Repeat")
        RecurrenceFields(
            type = form.recurrenceType,
            weekdays = form.weekdays,
            dayOfMonthText = viewModel.dayOfMonthText,
            intervalText = viewModel.intervalText,
            onType = { type -> viewModel.update { it.copy(recurrenceType = type) } },
            onWeekdays = { mask -> viewModel.update { it.copy(weekdays = mask) } },
            onDayOfMonth = { viewModel.dayOfMonthText = it },
            onInterval = { viewModel.intervalText = it },
            intervalHelp = "Counted from the due date.",
            noneLabel = "Doesn't repeat",
        )
        if (form.recurrenceType != null) {
            Text(
                "Completing this task adds the next occurrence, due on the next scheduled date after this one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = form.completed, onCheckedChange = { c -> viewModel.update { it.copy(completed = c) } })
            Text("Completed")
        }
        TagsSection(viewModel)
        LinkedItemsSection(viewModel, linked, onOpen)
    }
}
