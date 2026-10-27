package com.example.xcommandcenter

import android.app.Application
import com.example.xcommandcenter.reminder.Notifications
import kotlinx.coroutines.launch

class XCommandCenterApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        // Opens the database (seeding it on first launch) and makes sure every pending reminder,
        // including seeded ones, has an alarm registered.
        container.applicationScope.launch { container.reminderRepository.rescheduleAll() }
    }
}
