package com.example.xcommandcenter.ui.block

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.durationText
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.common.DetailScaffold
import com.example.xcommandcenter.ui.common.LinkedItemsSection
import com.example.xcommandcenter.ui.common.LinksInText
import com.example.xcommandcenter.ui.common.RemindersSection
import com.example.xcommandcenter.ui.common.TagsSection
import com.example.xcommandcenter.ui.common.TimePickerDialog
import com.example.xcommandcenter.ui.common.minuteText

@Composable
fun BlockDetailScreen(
    onBack: () -> Unit,
    onOpen: (ItemRef) -> Unit,
    viewModel: BlockDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val id by viewModel.itemId.collectAsStateWithLifecycle()
    val block = viewModel.block

    DetailScaffold(
        title = if (id == 0L) "New block" else "Block",
        viewModel = viewModel,
        onBack = onBack,
        onSave = viewModel::save,
        onDelete = if (id == 0L) null else viewModel::delete,
    ) {
        OutlinedTextField(
            value = block.title,
            onValueChange = { v -> viewModel.update { it.copy(title = v) } },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TimeButton("Starts", block.startMinute, Modifier.weight(1f), viewModel::setStart)
            TimeButton("Ends", block.endMinute, Modifier.weight(1f), viewModel::setEnd)
        }
        Text(
            "Lasts ${durationText(block.endMinute - block.startMinute)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = block.notes,
            onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
            label = { Text("Notes") },
            modifier = Modifier.fillMaxWidth(),
        )
        LinksInText(block.notes)
        RemindersSection(viewModel, blockStart = block.startMinute)
        TagsSection(viewModel)
        LinkedItemsSection(viewModel, onOpen)
    }
}

/** A labelled time of day that opens a time picker. */
@Composable
private fun TimeButton(label: String, minute: Int, modifier: Modifier, onPicked: (Int) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { picking = true }, modifier = modifier) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(minuteText(minute), style = MaterialTheme.typography.titleMedium)
        }
    }
    if (picking) {
        TimePickerDialog(java.time.LocalTime.of(minute / 60 % 24, minute % 60), onDismiss = { picking = false }) { time ->
            picking = false
            onPicked(time.hour * 60 + time.minute)
        }
    }
}
