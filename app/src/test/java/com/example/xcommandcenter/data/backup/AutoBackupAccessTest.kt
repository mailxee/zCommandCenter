package com.example.xcommandcenter.data.backup

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import com.example.xcommandcenter.XCommandCenterApp
import com.example.xcommandcenter.data.prefs.settingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A folder the app has lost access to (as after a reinstall, or when Google Drive withdraws it) is
 * reported in plain words, not as Android's permission error, and counts as having no access.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AutoBackupAccessTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repository = (context as XCommandCenterApp).container.autoBackupRepository

    @Test
    fun aFolderWithoutAccessIsReportedPlainly() = runBlocking {
        val folder = "content://com.google.android.apps.docs.storage/tree/acc%3D1%3Bdoc%3Dencoded%3Dabc"
        context.settingsStore.edit { it[stringPreferencesKey("auto_backup_folder")] = folder }
        assertFalse(repository.hasAccess(folder))
        assertFalse(repository.backUp())
        assertEquals(AutoBackupRepository.ACCESS_LOST, repository.settings.first().lastError)
    }
}
