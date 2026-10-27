package com.example.xcommandcenter.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists how the Tasks tab is sorted, as the sort's name (null until the user picks one), and
 * whether it shows completed tasks (yes until the user hides them).
 */
class TaskSortRepository(private val context: Context) {
    private val key = stringPreferencesKey("task_sort")
    private val showCompletedKey = booleanPreferencesKey("task_show_completed")

    val sort: Flow<String?> = context.settingsStore.data.map { prefs -> prefs[key] }

    val showCompleted: Flow<Boolean> = context.settingsStore.data.map { prefs -> prefs[showCompletedKey] ?: true }

    suspend fun setSort(name: String) {
        context.settingsStore.edit { prefs -> prefs[key] = name }
    }

    suspend fun setShowCompleted(show: Boolean) {
        context.settingsStore.edit { prefs -> prefs[showCompletedKey] = show }
    }
}
