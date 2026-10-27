package com.example.xcommandcenter.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.xcommandcenter.R

/**
 * Frame shared by all detail screens: back, delete, save (stays) and save-and-close actions, a snackbar for the view
 * model's messages, and a scrolling column for the content. Navigates back once [closed], which a
 * successful save or a delete sets. Leaving with unsaved changes (back arrow or system back) asks
 * whether to save or discard them first.
 */
@Composable
fun DetailScaffold(
    title: String,
    viewModel: ItemDetailViewModel,
    onBack: () -> Unit,
    onSave: ((close: Boolean) -> Unit)?,
    onDelete: (() -> Unit)?,
    deleteWarning: String = "This also removes all of its links.",
    content: @Composable ColumnScope.() -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    val unsaved = onSave != null && viewModel.hasUnsavedChanges
    val leave = { if (unsaved) confirmLeave = true else onBack() }
    BackHandler(enabled = unsaved) { confirmLeave = true }

    LaunchedEffect(viewModel.closed) { if (viewModel.closed) onBack() }
    LaunchedEffect(viewModel.message) {
        viewModel.message?.let {
            viewModel.messageShown()
            snackbar.showSnackbar(it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = itemTextStyle(viewModel.type, LocalTextStyle.current)) },
                navigationIcon = {
                    IconButton(onClick = leave) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
                    }
                },
                actions = {
                    if (onDelete != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete")
                        }
                    }
                    if (onSave != null) {
                        IconButton(onClick = { onSave(false) }) {
                            Icon(painterResource(R.drawable.ic_save), contentDescription = "Save")
                        }
                        IconButton(onClick = { onSave(true) }) {
                            Icon(painterResource(R.drawable.ic_check), contentDescription = "Save and close")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete?") },
            text = { Text(deleteWarning) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (confirmLeave && onSave != null) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Unsaved changes") },
            text = { Text("Save your changes before leaving?") },
            // Save closes the screen once saved; if the form is invalid, its message shows and the screen stays.
            confirmButton = {
                TextButton(onClick = { confirmLeave = false; onSave(true) }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { confirmLeave = false }) { Text("Keep editing") }
                    TextButton(onClick = { confirmLeave = false; onBack() }) { Text("Discard") }
                }
            },
        )
    }
}
