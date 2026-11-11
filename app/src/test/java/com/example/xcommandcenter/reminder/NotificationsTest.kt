package com.example.xcommandcenter.reminder

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.domain.HabitCategory
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Reminders carry the buttons that act without opening the app (§2.68). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationsTest {
    private val app: Application = ApplicationProvider.getApplicationContext()
    private val manager get() = app.getSystemService(NotificationManager::class.java)

    @Before
    fun allow() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        Notifications.createChannels(app)
    }

    private fun buttons() = shadowOf(manager).allNotifications.single().actions.orEmpty().map { it.title.toString() }

    @Test
    fun aTaskReminderHasDone() {
        Notifications.showItemReminder(app, ItemRef(ItemType.TASK, 1), "Call")
        assertEquals(listOf("Done"), buttons())
    }

    @Test
    fun otherRemindersHaveNoButton() {
        Notifications.showItemReminder(app, ItemRef(ItemType.PLAN, 1), "Move")
        assertEquals(emptyList<String>(), buttons())
    }

    @Test
    fun habitRemindersMarkDoneOrMissed() {
        Notifications.showHabitReminder(app, HabitEntity(id = 1, name = "Walk"))
        assertEquals(listOf("Done"), buttons())
        Notifications.cancel(app, ItemRef(ItemType.HABIT, 1))
        Notifications.showHabitReminder(app, HabitEntity(id = 2, name = "Snacks", category = HabitCategory.NEGATIVE))
        assertEquals(listOf("Missed today"), buttons())
    }
}
