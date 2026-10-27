package com.example.xcommandcenter.ui.plan

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** A plan that couldn't be completed because [openCount] plans below it are still open. */
data class BlockedCompletion(val planId: Long, val title: String, val openCount: Int)

/** Offers to complete the plan together with every plan below it. */
@Composable
fun CompletePlanDialog(blocked: BlockedCompletion, onCompleteAll: () -> Unit, onDismiss: () -> Unit) {
    val subplans = if (blocked.openCount == 1) "1 open subplan" else "${blocked.openCount} open subplans"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Complete plan?") },
        text = { Text("“${blocked.title}” has $subplans. Complete it together with all of its subplans?") },
        confirmButton = { TextButton(onClick = onCompleteAll) { Text("Complete all") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
