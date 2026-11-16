package com.example.xcommandcenter.handoff

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcommandcenter.handoff.Handoff.longExtra
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.theme.AppTheme

/**
 * MARK_HABIT: asks the user to confirm marking a habit's day for another app, over that app's
 * screen. RESULT_OK (with habit_id and date) when the day is marked as the screen closes, whether
 * just now or already; RESULT_CANCELED otherwise.
 */
class MarkHabitActivity : ComponentActivity() {
    private val viewModel: MarkHabitViewModel by viewModels { AppViewModelProvider.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        val habitId = intent.longExtra(Handoff.EXTRA_HABIT_ID)
        val epochDay = intent.longExtra(Handoff.EXTRA_DATE)
        viewModel.start(callingPackage, habitId, epochDay)
        setContent {
            AppTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val marked = state == MarkState.Marked || (state as? MarkState.Message)?.marked == true
                LaunchedEffect(marked) {
                    if (marked) {
                        val data = Intent().putExtra(Handoff.EXTRA_HABIT_ID, habitId).putExtra(Handoff.EXTRA_DATE, epochDay)
                        setResult(Activity.RESULT_OK, data)
                    }
                    if (state == MarkState.Marked) finish()
                }
                when (val s = state) {
                    MarkState.Checking, MarkState.Marked -> Unit
                    is MarkState.Message -> HandoffMessage(s.text, ::finish)
                    is MarkState.Ask -> AlertDialog(
                        onDismissRequest = ::finish,
                        title = { Text("Manage Everything") },
                        text = { Text(s.text) },
                        confirmButton = { TextButton(onClick = viewModel::confirm) { Text(s.confirm) } },
                        dismissButton = { TextButton(onClick = ::finish) { Text("Cancel") } },
                    )
                }
            }
        }
    }
}
