package com.example.xcommandcenter.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.TabOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persists the user's tab order, and which tabs are hidden, as comma-separated lists of tab names. */
class TabOrderRepository(private val context: Context) {
    private val key = stringPreferencesKey("tab_order")
    private val hiddenKey = stringPreferencesKey("tab_hidden")

    val order: Flow<List<ItemType>> = context.settingsStore.data.map { prefs -> TabOrder.parse(prefs[key]) }

    /** Tabs left off the main screen; their items stay, and still show where they are linked. */
    val hidden: Flow<Set<ItemType>> = context.settingsStore.data.map { prefs -> TabOrder.parseHidden(prefs[hiddenKey]) }

    suspend fun setHidden(tab: ItemType, hide: Boolean) {
        context.settingsStore.edit { prefs ->
            val now = TabOrder.withHidden(TabOrder.parseHidden(prefs[hiddenKey]), tab, hide)
            prefs[hiddenKey] = now.joinToString(",") { it.name }
        }
    }

    suspend fun setAllHidden(tabs: Set<ItemType>) {
        context.settingsStore.edit { prefs -> prefs[hiddenKey] = tabs.joinToString(",") { it.name } }
    }

    suspend fun setOrder(order: List<ItemType>) {
        context.settingsStore.edit { prefs -> prefs[key] = TabOrder.serialize(order) }
    }
}
