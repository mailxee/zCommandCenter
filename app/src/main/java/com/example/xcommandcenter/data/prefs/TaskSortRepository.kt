package com.example.xcommandcenter.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persists how the Tasks tab is sorted, as the sort's name; null until the user picks one. */
class TaskSortRepository(private val context: Context) {
    private val key = stringPreferencesKey("task_sort")

    val sort: Flow<String?> = context.settingsStore.data.map { prefs -> prefs[key] }

    suspend fun setSort(name: String) {
        context.settingsStore.edit { prefs -> prefs[key] = name }
    }
}
