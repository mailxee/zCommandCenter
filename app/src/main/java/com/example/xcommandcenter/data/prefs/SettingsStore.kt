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

/**
 * Forgets settings that name items or link labels by id, which after an import may name others:
 * which plans and ideas are collapsed, and on the map the kinds of link hidden or restyled by label,
 * dragged positions and each item's own layout. The map's other choices stay.
 */
suspend fun forgetIds(context: Context) {
    context.settingsStore.edit {
        it.remove(stringSetPreferencesKey(CollapsedPlansRepository.KEY))
        it.remove(stringSetPreferencesKey(CollapsedPlansRepository.IDEAS_KEY))
        MapPrefsRepository.forgetIds(it)
    }
}
