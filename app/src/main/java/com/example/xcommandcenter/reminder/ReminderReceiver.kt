package com.example.xcommandcenter.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fired by the alarm; posts the notification. Works with the app closed. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        if (taskId != 0L) Notifications.showTaskReminder(context, taskId, title)
    }

    companion object {
        const val ACTION_REMIND = "com.example.xcommandcenter.action.REMIND"
        const val EXTRA_TASK_ID = "taskId"
        const val EXTRA_TITLE = "title"
    }
}
