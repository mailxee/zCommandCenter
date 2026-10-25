package com.example.xcommandcenter.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.TabOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Persists the user's tab order as a comma-separated list of tab names. */
class TabOrderRepository(private val context: Context) {
    private val key = stringPreferencesKey("tab_order")

    val order: Flow<List<ItemType>> = context.settingsStore.data.map { prefs -> TabOrder.parse(prefs[key]) }

    suspend fun setOrder(order: List<ItemType>) {
        context.settingsStore.edit { prefs -> prefs[key] = TabOrder.serialize(order) }
    }
}
