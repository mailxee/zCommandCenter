package com.example.xcommandcenter.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.xcommandcenter.XCommandCenterApp
import com.example.xcommandcenter.data.repository.ReminderNotice
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Fired by the alarms; posts the notification. Also takes the notifications' buttons: completing
 * a task, or marking a habit today. Works with the app closed.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TASK_DONE, ACTION_HABIT_MARK -> return onButton(context, intent)
        }
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

    private fun onButton(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ITEM_ID, 0L)
        if (id == 0L) return
        val container = (context.applicationContext as XCommandCenterApp).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                if (intent.action == ACTION_TASK_DONE) {
                    // Already completed (from the app, or a second press) leaves it as is.
                    if (container.taskRepository.get(id)?.completed == false) container.taskRepository.setCompleted(id, true)
                    Notifications.cancel(context, ItemRef(ItemType.TASK, id))
                } else {
                    container.habitRepository.mark(id, LocalDate.now())
                    Notifications.cancel(context, ItemRef(ItemType.HABIT, id))
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "com.example.xcommandcenter.action.ITEM_REMINDER"
        const val EXTRA_REMINDER_ID = "reminderId"

        /** A task reminder's "Done" button. */
        const val ACTION_TASK_DONE = "com.example.xcommandcenter.action.TASK_DONE"

        /** A habit reminder's "Done" (positive) or "Missed today" (negative) button: marks today. */
        const val ACTION_HABIT_MARK = "com.example.xcommandcenter.action.HABIT_MARK"
        const val EXTRA_ITEM_ID = "itemId"
    }
}
