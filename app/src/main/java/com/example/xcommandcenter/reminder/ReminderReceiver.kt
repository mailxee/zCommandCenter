package com.example.xcommandcenter.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.xcommandcenter.XCommandCenterApp
import com.example.xcommandcenter.data.repository.ReminderNotice
import kotlinx.coroutines.launch

/** Fired by the alarms; posts the notification. Works with the app closed. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Alarms set by versions before reminders had their own table use other actions; they are ignored.
        if (intent.action != ACTION_REMIND) return
        val reminderId = intent.getLongExtra(EXTRA_REMINDER_ID, 0L)
        if (reminderId == 0L) return
        // Reading the item and scheduling a habit's next reminder need the database.
        val container = (context.applicationContext as XCommandCenterApp).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                when (val notice = container.reminderRepository.fired(reminderId)) {
                    is ReminderNotice.Habit -> Notifications.showHabitReminder(context, notice.habit)
                    is ReminderNotice.Item -> Notifications.showItemReminder(context, notice.ref, notice.title)
                    null -> Unit
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.example.xcommandcenter.action.ITEM_REMINDER"
        const val EXTRA_REMINDER_ID = "reminderId"
    }
}
