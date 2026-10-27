package com.example.xcommandcenter.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists which plans are collapsed in the Plans tab. Ids of deleted plans may linger;
 * they match nothing, so they are harmless.
 */
class CollapsedPlansRepository(private val context: Context) {
    private val key = stringSetPreferencesKey(KEY)

    val collapsed: Flow<Set<Long>> = context.settingsStore.data.map { prefs ->
        prefs[key].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun setCollapsed(planId: Long, collapsed: Boolean) {
        context.settingsStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            prefs[key] = if (collapsed) current + planId.toString() else current - planId.toString()
        }
    }

    companion object {
        const val KEY = "collapsed_plans"
    }
}
