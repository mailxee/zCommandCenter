package com.example.xcommandcenter.share

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.xcommandcenter.data.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every path answers with its table's Room columns, and writing is refused. The caller here is the
 * app itself, which is always let in; who else may read is [com.example.xcommandcenter.domain.DataSharing]'s, tested there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShareProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val authority = "${context.packageName}.share"

    init {
        Robolectric.setupContentProvider(ShareProvider::class.java, authority)
    }

    private fun uri(path: String) = Uri.parse("content://$authority/$path")

    /** Queries arrive on binder threads, never the main one (where Room refuses to run). */
    private fun <T> offMain(block: () -> T): T {
        var result: Result<T>? = null
        Thread { result = runCatching(block) }.apply { start(); join() }
        return result!!.getOrThrow()
    }

    @Test
    fun everyPathReturnsItsTableColumns() {
        val columns = offMain {
            SharePath.entries.associate { path ->
                path.path to context.contentResolver.query(uri(path.path), null, null, null, null)!!.use { it.columnNames.toList() }
            }
        }
        assertTrue("completedAt" in columns.getValue("tasks"))
        assertEquals(listOf("habitId", "date", "markedAt"), columns.getValue("habit_marks"))
        assertEquals(listOf("type", "itemId", "tagId"), columns.getValue("item_tags"))
        assertEquals(listOf("contract_version", "db_version", "app_version", "app_version_code"), columns.getValue("meta"))
        println(columns.entries.joinToString("\n") { (path, names) -> "/$path: ${names.joinToString()}" })
    }

    @Test
    fun metaHasOneRowWithTheVersions() {
        context.contentResolver.query(uri("meta"), null, null, null, null)!!.use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals(ShareProvider.CONTRACT_VERSION, c.getInt(0))
            assertEquals(AppDatabase.VERSION, c.getInt(1))
        }
    }

    @Test
    fun writingAndUnknownPathsAreRefused() {
        val refused = listOf(
            { context.contentResolver.insert(uri("tasks"), ContentValues()) },
            { context.contentResolver.update(uri("tasks"), ContentValues(), null, null) },
            { context.contentResolver.delete(uri("tasks"), null, null) },
        ).map { runCatching { it() }.exceptionOrNull() }
        assertTrue(refused.all { it is UnsupportedOperationException })
        assertTrue(runCatching { offMain { context.contentResolver.query(uri("notes"), null, null, null, null) } }.exceptionOrNull() is IllegalArgumentException)
    }
}
