package com.example.xcommandcenter.ui.goal

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.common.DetailScaffold
import com.example.xcommandcenter.ui.common.LinkedItemsSection
import com.example.xcommandcenter.ui.common.LinksInText
import com.example.xcommandcenter.ui.common.PriorityField
import com.example.xcommandcenter.ui.common.TagsSection

@Composable
fun GoalDetailScreen(
    onBack: () -> Unit,
    onOpen: (ItemRef) -> Unit,
    viewModel: GoalDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val id by viewModel.itemId.collectAsStateWithLifecycle()
    val goal = viewModel.goal

    DetailScaffold(
        title = if (id == 0L) "New goal" else "Goal",
        viewModel = viewModel,
        onBack = onBack,
        onSave = viewModel::save,
        onDelete = if (id == 0L) null else viewModel::delete,
    ) {
        OutlinedTextField(
            value = goal.title,
            onValueChange = { v -> viewModel.update { it.copy(title = v) } },
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = goal.strategy,
            onValueChange = { v -> viewModel.update { it.copy(strategy = v) } },
            label = { Text("Strategy") },
            modifier = Modifier.fillMaxWidth(),
        )
        LinksInText(goal.strategy)
        PriorityField(goal.priority) { p -> viewModel.update { it.copy(priority = p) } }
        TagsSection(viewModel)
        LinkedItemsSection(viewModel, onOpen)
    }
}
