package com.example.xcommandcenter.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.xcommandcenter.domain.ItemStyle
import com.example.xcommandcenter.domain.ItemStyles
import com.example.xcommandcenter.domain.ItemType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persists each tab's item style under its own key; a tab without one uses its default. */
class ItemStyleRepository(private val context: Context) {
    val styles: Flow<ItemStyles> = context.settingsStore.data.map { prefs ->
        ItemStyles(ItemType.tabs.associateWith { tab -> ItemStyle.parse(prefs[key(tab)], ItemStyle.default(tab)) })
    }

    suspend fun setStyle(tab: ItemType, style: ItemStyle) {
        context.settingsStore.edit { prefs -> prefs[key(tab)] = style.serialize() }
    }

    suspend fun reset(tab: ItemType) {
        context.settingsStore.edit { prefs -> prefs.remove(key(tab)) }
    }

    private fun key(tab: ItemType) = stringPreferencesKey("item_style_${tab.name}")
}
