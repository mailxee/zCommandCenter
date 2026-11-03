package com.example.xcommandcenter.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * How many days after a task's or plan's reminder goes off, with the item still open and nothing
 * later, the reminder is repeated (see `ReminderRepository.carryOver`); 0 turns carry-over off.
 */
class ReminderPrefsRepository(private val context: Context) {
    private val key = intPreferencesKey("reminder_carry_days")

    val carryDays: Flow<Int> = context.settingsStore.data.map { prefs -> prefs[key] ?: DEFAULT_CARRY_DAYS }

    suspend fun carryDaysNow(): Int = carryDays.first()

    suspend fun setCarryDays(days: Int) {
        context.settingsStore.edit { prefs -> prefs[key] = days.coerceAtLeast(0) }
    }

    companion object {
        const val DEFAULT_CARRY_DAYS = 1

        /** The choices Settings offers. */
        val choices = listOf(0, 1, 2, 3, 7)
    }
}
