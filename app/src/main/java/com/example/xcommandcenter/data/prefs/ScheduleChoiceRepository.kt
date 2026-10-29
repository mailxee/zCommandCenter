package com.example.xcommandcenter.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** The schedule last picked on the Schedule tab, the day it was picked, and whether automatic switching is on. */
data class StoredScheduleChoice(val selectedId: Long? = null, val selectedOn: LocalDate? = null, val automatic: Boolean = false)

class ScheduleChoiceRepository(private val context: Context) {
    private val selectedKey = longPreferencesKey("schedule_selected")
    private val selectedOnKey = longPreferencesKey("schedule_selected_on")
    private val automaticKey = booleanPreferencesKey("schedule_automatic")

    val choice: Flow<StoredScheduleChoice> = context.settingsStore.data.map { prefs ->
        StoredScheduleChoice(prefs[selectedKey], prefs[selectedOnKey]?.let(LocalDate::ofEpochDay), prefs[automaticKey] ?: false)
    }

    suspend fun select(id: Long, on: LocalDate) {
        context.settingsStore.edit { prefs ->
            prefs[selectedKey] = id
            prefs[selectedOnKey] = on.toEpochDay()
        }
    }

    suspend fun setAutomatic(on: Boolean) {
        context.settingsStore.edit { prefs -> prefs[automaticKey] = on }
    }

    /** From a backup: the pick (as if made before today, so automatic switching applies) and the switch. */
    suspend fun restore(selectedId: Long?, automatic: Boolean) {
        context.settingsStore.edit { prefs ->
            if (selectedId != null) prefs[selectedKey] = selectedId else prefs.remove(selectedKey)
            prefs.remove(selectedOnKey)
            prefs[automaticKey] = automatic
        }
    }
}
