package com.example.xcommandcenter.ui.quote

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
fun QuoteDetailScreen(
    onBack: () -> Unit,
    onOpen: (ItemRef) -> Unit,
    viewModel: QuoteDetailViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val id by viewModel.itemId.collectAsStateWithLifecycle()
    val quote = viewModel.quote

    DetailScaffold(
        title = if (id == 0L) "New quote" else "Quote",
        viewModel = viewModel,
        onBack = onBack,
        onSave = viewModel::save,
        onDelete = if (id == 0L) null else viewModel::delete,
    ) {
        OutlinedTextField(
            value = quote.text,
            onValueChange = { v -> viewModel.update { it.copy(text = v) } },
            label = { Text("Quote") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = quote.author,
            onValueChange = { v -> viewModel.update { it.copy(author = v) } },
            label = { Text("Author (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = quote.source,
            onValueChange = { v -> viewModel.update { it.copy(source = v) } },
            label = { Text("Source (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LinksInText(quote.text + "\n" + quote.source)
        PriorityField(quote.priority) { p -> viewModel.update { it.copy(priority = p) } }
        TagsSection(viewModel)
        LinkedItemsSection(viewModel, onOpen)
    }
}
