package com.example.xcommandcenter.reminder

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.xcommandcenter.XCommandCenterApp
import kotlinx.coroutines.launch

/**
 * Re-registers reminders after events that drop or degrade alarms: a reboot, an app update, or
 * the user granting the exact-alarm permission.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> Unit
            else -> return
        }
        val container = (context.applicationContext as XCommandCenterApp).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.taskRepository.rescheduleAllReminders()
                container.habitRepository.rescheduleAllReminders()
            } finally {
                pending.finish()
            }
        }
    }
}
