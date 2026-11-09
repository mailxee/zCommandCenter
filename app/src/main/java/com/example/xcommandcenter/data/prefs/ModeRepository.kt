package com.example.xcommandcenter.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.xcommandcenter.domain.AppMode
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.TabMode
import com.example.xcommandcenter.domain.TabModes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The mode the app is in (kept across launches) and which mode shows each tab. */
class ModeRepository(private val context: Context) {
    private val modeKey = stringPreferencesKey("app_mode")
    private val tabModesKey = stringPreferencesKey("tab_modes")

    val mode: Flow<AppMode> = context.settingsStore.data.map { prefs ->
        AppMode.entries.firstOrNull { it.name == prefs[modeKey] } ?: AppMode.DO
    }

    val tabModes: Flow<TabModes> = context.settingsStore.data.map { prefs -> TabModes.parse(prefs[tabModesKey]) }

    suspend fun setMode(mode: AppMode) {
        context.settingsStore.edit { it[modeKey] = mode.name }
    }

    suspend fun setTabMode(current: TabModes, tab: ItemType, mode: TabMode) = setTabModes(current.with(tab, mode))

    suspend fun setTabModes(modes: TabModes) {
        context.settingsStore.edit { it[tabModesKey] = modes.encode() }
    }
}
