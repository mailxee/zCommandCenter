package com.example.xcommandcenter.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.xcommandcenter.domain.ThinkRoute
import com.example.xcommandcenter.domain.LinkRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The suggested routes of links (Settings → Links): Do mode's, as `LinkRoute.encode` writes it, and
 * Think mode's (`ThinkRoute`).
 */
class RoutePrefsRepository(private val context: Context) {
    private val key = stringPreferencesKey("link_route")
    private val thinkKey = stringPreferencesKey("think_route")

    val route: Flow<LinkRoute> = context.settingsStore.data.map { prefs -> LinkRoute.parse(prefs[key]) }

    val thinkRoute: Flow<ThinkRoute> = context.settingsStore.data.map { prefs -> ThinkRoute.parse(prefs[thinkKey]) }

    suspend fun setRoute(route: LinkRoute) {
        context.settingsStore.edit { prefs -> prefs[key] = route.encode() }
    }

    suspend fun setThinkRoute(route: ThinkRoute) {
        context.settingsStore.edit { prefs -> prefs[thinkKey] = route.encode() }
    }
}
