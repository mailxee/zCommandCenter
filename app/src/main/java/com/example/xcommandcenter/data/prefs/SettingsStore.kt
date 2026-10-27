package com.example.xcommandcenter.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/** The app's one preferences file; DataStore allows a single instance per file per process. */
internal val Context.settingsStore by preferencesDataStore(name = "settings")

/** Forgets every saved setting (tab order, task sort, collapsed plans); used by "Delete everything". */
suspend fun clearSettings(context: Context) {
    context.settingsStore.edit { it.clear() }
}

/** Forgets which projects are collapsed; ids from before an import may now name other projects. */
suspend fun clearCollapsedProjects(context: Context) {
    context.settingsStore.edit { it.remove(stringSetPreferencesKey(CollapsedProjectsRepository.KEY)) }
}
