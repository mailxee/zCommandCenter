package com.example.xcommandcenter.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.xcommandcenter.domain.DataSharing
import com.example.xcommandcenter.domain.ShareApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * The apps allowed to read the shared data and the requests waiting for an answer (see
 * [DataSharing]), each kept as a JSON list in the settings file. Both start empty.
 */
class ShareAccessRepository(private val context: Context) {
    private val allowedKey = stringPreferencesKey("share_allowed")
    private val pendingKey = stringPreferencesKey("share_pending")

    val allowed: Flow<List<ShareApp>> = context.settingsStore.data.map { decode(it[allowedKey]) }
    val pending: Flow<List<ShareApp>> = context.settingsStore.data.map { decode(it[pendingKey]) }

    /**
     * Whether [caller] may read; one that may not is recorded as a request (or its request moved to
     * the top), with [caller]'s `at` as the time it asked.
     */
    suspend fun check(caller: ShareApp): Boolean {
        var allowedNow = false
        context.settingsStore.edit { prefs ->
            allowedNow = DataSharing.isAllowed(decode(prefs[allowedKey]), caller.packageName, caller.certSha256)
            if (!allowedNow) prefs.put(pendingKey, DataSharing.addPending(decode(prefs[pendingKey]), caller))
        }
        return allowedNow
    }

    suspend fun allow(app: ShareApp, now: Long = System.currentTimeMillis()) {
        context.settingsStore.edit { prefs ->
            val (allowed, pending) = DataSharing.allow(decode(prefs[allowedKey]), decode(prefs[pendingKey]), app, now)
            prefs.put(allowedKey, allowed)
            prefs.put(pendingKey, pending)
        }
    }

    /** Drops the request; the app can ask again, and is then listed again. */
    suspend fun deny(app: ShareApp) {
        context.settingsStore.edit { prefs -> prefs.put(pendingKey, DataSharing.remove(decode(prefs[pendingKey]), app)) }
    }

    suspend fun revoke(app: ShareApp) {
        context.settingsStore.edit { prefs -> prefs.put(allowedKey, DataSharing.remove(decode(prefs[allowedKey]), app)) }
    }

    private fun androidx.datastore.preferences.core.MutablePreferences.put(key: Preferences.Key<String>, apps: List<ShareApp>) {
        this[key] = encode(apps)
    }

    companion object {
        fun encode(apps: List<ShareApp>): String = JSONArray(
            apps.map {
                JSONObject().put("package", it.packageName).put("label", it.label).put("cert", it.certSha256).put("at", it.at)
            },
        ).toString()

        /** A missing or unreadable value is an empty list, so nothing is shared by mistake. */
        fun decode(json: String?): List<ShareApp> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val array = JSONArray(json)
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    ShareApp(o.getString("package"), o.optString("label"), o.getString("cert"), o.optLong("at"))
                }
            }.getOrDefault(emptyList())
        }
    }
}
