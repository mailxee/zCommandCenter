package com.example.xcommandcenter.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.example.xcommandcenter.domain.ThinkRoute
import com.example.xcommandcenter.domain.LinkRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The suggested routes of links (Settings → Links): Do mode's, as `LinkRoute.encode` writes it, and
 * Think mode's (`ThinkRoute`); and whether Tidy links checks that every goal has a why.
 */
class RoutePrefsRepository(private val context: Context) {
    private val key = stringPreferencesKey("link_route")
    private val thinkKey = stringPreferencesKey("think_route")
    private val whyKey = booleanPreferencesKey("check_goal_why")

    val route: Flow<LinkRoute> = context.settingsStore.data.map { prefs -> LinkRoute.parse(prefs[key]) }

    val thinkRoute: Flow<ThinkRoute> = context.settingsStore.data.map { prefs -> ThinkRoute.parse(prefs[thinkKey]) }

    val checkWhy: Flow<Boolean> = context.settingsStore.data.map { prefs -> prefs[whyKey] ?: true }

    suspend fun setRoute(route: LinkRoute) {
        context.settingsStore.edit { prefs -> prefs[key] = route.encode() }
    }

    suspend fun setThinkRoute(route: ThinkRoute) {
        context.settingsStore.edit { prefs -> prefs[thinkKey] = route.encode() }
    }

    suspend fun setCheckWhy(on: Boolean) {
        context.settingsStore.edit { prefs -> prefs[whyKey] = on }
    }
}
