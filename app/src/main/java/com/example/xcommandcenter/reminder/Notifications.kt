package com.example.xcommandcenter.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.xcommandcenter.MainActivity
import com.example.xcommandcenter.R
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.domain.HabitCategory
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType

object Notifications {
    private const val CHANNEL_REMINDERS = "task_reminders"
    private const val CHANNEL_HABITS = "habit_reminders"

    /** Habit notifications use their own tag, so a habit's id can't replace a task's notification. */
    private const val TAG_HABIT = "habit"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Task reminders", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Reminders you set on tasks" },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_HABITS, "Habit reminders", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Daily reminders to mark your habits" },
        )
    }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU

    fun showTaskReminder(context: Context, taskId: Long, title: String) {
        if (!hasPermission(context)) return
        val open = openItem(context, ItemRef(ItemType.TASK, taskId))
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "Task reminder" })
            .setContentText("Task reminder")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(taskId.toInt(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; nothing else to do.
        }
    }

    /** Asks to mark a habit today: done for a positive habit, missed (if so) for a negative one. */
    fun showHabitReminder(context: Context, habit: HabitEntity) {
        if (!hasPermission(context)) return
        val text = when (habit.category) {
            HabitCategory.POSITIVE -> "Did you do it today? Mark it done."
            HabitCategory.NEGATIVE -> "Mark today if you missed it."
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_HABITS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(habit.name.ifBlank { "Habit reminder" })
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openItem(context, ItemRef(ItemType.HABIT, habit.id)))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(TAG_HABIT, habit.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call; nothing else to do.
        }
    }

    /** Opens [ref]'s page when the notification is tapped. */
    private fun openItem(context: Context, ref: ItemRef): PendingIntent = PendingIntent.getActivity(
        context,
        // Distinct per item, so one notification's intent doesn't replace another's.
        31 * ref.id.toInt() + ref.type.ordinal,
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TYPE, ref.type.name)
            .putExtra(MainActivity.EXTRA_OPEN_ID, ref.id)
            .putExtra(MainActivity.EXTRA_TOKEN, System.currentTimeMillis())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
