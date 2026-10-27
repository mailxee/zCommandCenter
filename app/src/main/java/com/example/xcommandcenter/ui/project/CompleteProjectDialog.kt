package com.example.xcommandcenter.ui.project

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** A project that couldn't be completed because [openCount] projects below it are still open. */
data class BlockedCompletion(val projectId: Long, val title: String, val openCount: Int)

/** Offers to complete the project together with every project below it. */
@Composable
fun CompleteProjectDialog(blocked: BlockedCompletion, onCompleteAll: () -> Unit, onDismiss: () -> Unit) {
    val subprojects = if (blocked.openCount == 1) "1 open subplan" else "${blocked.openCount} open subplans"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Complete plan?") },
        text = { Text("“${blocked.title}” has $subprojects. Complete it together with all of its subplans?") },
        confirmButton = { TextButton(onClick = onCompleteAll) { Text("Complete all") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
