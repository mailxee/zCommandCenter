package com.example.xcommandcenter.handoff

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** A hand-off screen's message over the asking app: why nothing happened, or what already had. */
@Composable
fun HandoffMessage(text: String, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Manage Everything") },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onClose) { Text("OK") } },
    )
}
