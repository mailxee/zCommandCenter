package com.example.xcommandcenter.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.LineStyle
import com.example.xcommandcenter.domain.MapFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The map's Show panel: tabs and kinds of link left out, and each kind of link's line style. */
class MapPrefsRepository(private val context: Context) {
    private val typesKey = stringSetPreferencesKey("map_hidden_types")
    private val linksKey = stringSetPreferencesKey("map_hidden_links")
    private val stylesKey = stringSetPreferencesKey("map_line_styles")

    val filter: Flow<MapFilter> = context.settingsStore.data.map { prefs ->
        MapFilter(
            hiddenTypes = MapFilter.parseTypes(prefs[typesKey]),
            hiddenLinks = prefs[linksKey].orEmpty(),
            styles = MapFilter.parseStyles(prefs[stylesKey]),
        )
    }

    suspend fun setTypeShown(type: ItemType, shown: Boolean) {
        context.settingsStore.edit { prefs ->
            val now = prefs[typesKey].orEmpty()
            prefs[typesKey] = if (shown) now - type.name else now + type.name
        }
    }

    suspend fun setLinkShown(kind: String, shown: Boolean) {
        context.settingsStore.edit { prefs ->
            val now = prefs[linksKey].orEmpty()
            prefs[linksKey] = if (shown) now - kind else now + kind
        }
    }

    suspend fun setStyle(kind: String, style: LineStyle) {
        context.settingsStore.edit { prefs ->
            prefs[stylesKey] = MapFilter.encodeStyles(MapFilter.parseStyles(prefs[stylesKey]) + (kind to style))
        }
    }

    /** Shows everything again; line styles stay. */
    suspend fun showAll() {
        context.settingsStore.edit { prefs ->
            prefs.remove(typesKey)
            prefs.remove(linksKey)
        }
    }
}
