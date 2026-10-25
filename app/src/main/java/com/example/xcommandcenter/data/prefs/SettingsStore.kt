package com.example.xcommandcenter.data.prefs

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/** The app's one preferences file; DataStore allows a single instance per file per process. */
internal val Context.settingsStore by preferencesDataStore(name = "settings")
