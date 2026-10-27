package com.example.xcommandcenter.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Registers task and habit reminders with [AlarmManager], which delivers them to [ReminderReceiver]
 * even when the app's process is not running. One alarm per task and one per habit, keyed by the
 * id; the two kinds use different intent actions, so their ids can't clash.
 *
 * Exact alarms need the user's permission on Android 12+ (see [canScheduleExact]); without it the
 * reminder is still delivered, but the system may batch it a few minutes late.
 */
class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun schedule(taskId: Long, title: String, atMillis: Long) = set(pendingIntent(taskId, title), atMillis)

    fun cancel(taskId: Long) {
        alarmManager.cancel(pendingIntent(taskId, title = ""))
    }

    /** One alarm per habit: its next reminder. The receiver schedules the one after when it fires. */
    fun scheduleHabit(habitId: Long, atMillis: Long) = set(habitIntent(habitId), atMillis)

    fun cancelHabit(habitId: Long) {
        alarmManager.cancel(habitIntent(habitId))
    }

    private fun set(intent: PendingIntent, atMillis: Long) {
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, intent)
        }
    }

    private fun habitIntent(habitId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        habitId.toInt(),
        Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_HABIT_REMIND)
            .putExtra(ReminderReceiver.EXTRA_HABIT_ID, habitId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // The extras are not part of PendingIntent identity, so cancel() matches with any title.
    private fun pendingIntent(taskId: Long, title: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ReminderReceiver.ACTION_REMIND)
            .putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
            .putExtra(ReminderReceiver.EXTRA_TITLE, title)
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
