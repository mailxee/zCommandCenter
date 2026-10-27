package com.example.xcommandcenter.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.xcommandcenter.XCommandCenterApp
import kotlinx.coroutines.launch

/** Fired by the alarms; posts the notification. Works with the app closed. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REMIND -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                if (taskId != 0L) Notifications.showTaskReminder(context, taskId, title)
            }
            ACTION_HABIT_REMIND -> {
                val habitId = intent.getLongExtra(EXTRA_HABIT_ID, 0L)
                if (habitId == 0L) return
                // Reading the habit and scheduling its next reminder need the database.
                val container = (context.applicationContext as XCommandCenterApp).container
                val pending = goAsync()
                container.applicationScope.launch {
                    try {
                        container.habitRepository.reminderFired(habitId)?.let { Notifications.showHabitReminder(context, it) }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.example.xcommandcenter.action.REMIND"
        const val ACTION_HABIT_REMIND = "com.example.xcommandcenter.action.HABIT_REMIND"
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_TITLE = "title"
        const val EXTRA_HABIT_ID = "habitId"
    }
}
