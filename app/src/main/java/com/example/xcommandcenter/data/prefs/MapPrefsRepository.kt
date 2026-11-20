package com.example.xcommandcenter.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.xcommandcenter.domain.GraphLayout
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.MapPositions
import com.example.xcommandcenter.domain.AppMode
import com.example.xcommandcenter.domain.MapStyle
import com.example.xcommandcenter.domain.MapStyles
import com.example.xcommandcenter.domain.LineStyle
import com.example.xcommandcenter.domain.LineWidth
import com.example.xcommandcenter.domain.MapFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The map's Show panel (tabs and kinds of link left out, each kind of link's line style and width), dragged offsets and chosen layouts. */
class MapPrefsRepository(private val context: Context) {
    private val typesKey = TYPES_KEY
    private val linksKey = LINKS_KEY
    private val stylesKey = STYLES_KEY
    private val widthsKey = WIDTHS_KEY
    private val positionsKey = POSITIONS_KEY
    private val mapStylesKey = LAYOUTS_KEY

    /** Forgets what is kept for a deleted label, so it can't carry over to another. */
    suspend fun forgetLabel(id: Long) {
        context.settingsStore.edit { prefs ->
            val key = MapFilter.label(id)
            prefs[linksKey] = prefs[linksKey].orEmpty() - key
            prefs[stylesKey] = MapFilter.encodeStyles(MapFilter.parseStyles(prefs[stylesKey]) - key)
            prefs[widthsKey] = MapFilter.encodeWidths(MapFilter.parseWidths(prefs[widthsKey]) - key)
        }
    }

    /** Layouts chosen on the map, per mode (see [MapStyles]). */
    val styles: Flow<Map<AppMode, Map<ItemRef?, MapStyle>>> = context.settingsStore.data.map { prefs -> MapStyles.parse(prefs[mapStylesKey]) }

    suspend fun setMapStyle(mode: AppMode, ref: ItemRef?, style: MapStyle?) {
        context.settingsStore.edit { prefs -> prefs[mapStylesKey] = MapStyles.set(prefs[mapStylesKey], mode, ref, style) }
    }

    suspend fun forget(view: String, refs: Collection<ItemRef>) {
        context.settingsStore.edit { prefs -> prefs[positionsKey] = MapPositions.forget(prefs[positionsKey], view, refs) }
    }

    /** How far items were dragged, per view (see [MapPositions]). */
    val positions: Flow<Map<String, Map<ItemRef, GraphLayout.Point>>> =
        context.settingsStore.data.map { prefs -> MapPositions.parse(prefs[positionsKey]) }

    suspend fun move(view: String, ref: ItemRef, point: GraphLayout.Point) {
        context.settingsStore.edit { prefs -> prefs[positionsKey] = MapPositions.move(prefs[positionsKey], view, ref, point) }
    }

    suspend fun resetPositions(view: String) {
        context.settingsStore.edit { prefs -> prefs[positionsKey] = MapPositions.reset(prefs[positionsKey], view) }
    }

    val filter: Flow<MapFilter> = context.settingsStore.data.map { prefs ->
        MapFilter(
            hiddenTypes = MapFilter.parseTypes(prefs[typesKey]),
            hiddenLinks = prefs[linksKey].orEmpty(),
            styles = MapFilter.parseStyles(prefs[stylesKey]),
            widths = MapFilter.parseWidths(prefs[widthsKey]),
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

    suspend fun setWidth(kind: String, width: LineWidth) {
        context.settingsStore.edit { prefs ->
            prefs[widthsKey] = MapFilter.encodeWidths(MapFilter.parseWidths(prefs[widthsKey]) + (kind to width))
        }
    }

    /** Every kind of link back to its usual style and width; what is hidden stays. */
    suspend fun resetLines() {
        context.settingsStore.edit { prefs ->
            prefs.remove(stylesKey)
            prefs.remove(widthsKey)
        }
    }

    /** Shows everything again; line styles stay. */
    suspend fun showAll() {
        context.settingsStore.edit { prefs ->
            prefs.remove(typesKey)
            prefs.remove(linksKey)
        }
    }

    companion object {
        private val TYPES_KEY = stringSetPreferencesKey("map_hidden_types")
        private val LINKS_KEY = stringSetPreferencesKey("map_hidden_links")
        private val STYLES_KEY = stringSetPreferencesKey("map_line_styles")
        private val WIDTHS_KEY = stringSetPreferencesKey("map_line_widths")
        private val POSITIONS_KEY = stringSetPreferencesKey("map_positions")
        private val LAYOUTS_KEY = stringSetPreferencesKey("map_layouts")

        /** See [forgetIds][com.example.xcommandcenter.data.prefs.forgetIds]: what names labels or items goes. */
        fun forgetIds(prefs: MutablePreferences) {
            prefs[LINKS_KEY] = MapFilter.withoutLabels(prefs[LINKS_KEY].orEmpty())
            prefs[STYLES_KEY] = MapFilter.encodeStyles(MapFilter.parseStyles(prefs[STYLES_KEY]).filterKeys { !MapFilter.isLabel(it) })
            prefs[WIDTHS_KEY] = MapFilter.encodeWidths(MapFilter.parseWidths(prefs[WIDTHS_KEY]).filterKeys { !MapFilter.isLabel(it) })
            prefs.remove(POSITIONS_KEY)
            prefs[LAYOUTS_KEY] = MapStyles.wholeMapsOnly(prefs[LAYOUTS_KEY])
        }
    }
}
