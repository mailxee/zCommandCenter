package com.example.xcommandcenter.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.xcommandcenter.domain.LinkRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The suggested route of links (Settings → Links), as `LinkRoute.encode` writes it. */
class RoutePrefsRepository(private val context: Context) {
    private val key = stringPreferencesKey("link_route")

    val route: Flow<LinkRoute> = context.settingsStore.data.map { prefs -> LinkRoute.parse(prefs[key]) }

    suspend fun setRoute(route: LinkRoute) {
        context.settingsStore.edit { prefs -> prefs[key] = route.encode() }
    }
}
