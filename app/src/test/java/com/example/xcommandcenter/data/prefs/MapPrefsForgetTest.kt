package com.example.xcommandcenter.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.example.xcommandcenter.domain.AppMode
import com.example.xcommandcenter.domain.GraphLayout
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.LineStyle
import com.example.xcommandcenter.domain.LineWidth
import com.example.xcommandcenter.domain.MapFilter
import com.example.xcommandcenter.domain.MapStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What the map keeps by label or item id doesn't carry over to another label or item: a deleted
 * label's choices go, and an import (whose ids may name other things) forgets them all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MapPrefsForgetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs = MapPrefsRepository(context)
    private val idea = ItemRef(ItemType.IDEA, 7)

    @Before
    fun setUp() = runBlocking {
        context.settingsStore.edit { it.clear() }
        prefs.setLinkShown(MapFilter.label(3), false)
        prefs.setLinkShown(MapFilter.label(4), false)
        prefs.setLinkShown(MapFilter.UNDER, false)
        prefs.setStyle(MapFilter.label(3), LineStyle.DASHED)
        prefs.setStyle(MapFilter.PLAIN, LineStyle.DOTTED)
        prefs.setWidth(MapFilter.label(3), LineWidth.THICK)
        prefs.setWidth(MapFilter.UNDER, LineWidth.THIN)
        prefs.setMapStyle(AppMode.THINK, null, MapStyle.TREE)
        prefs.setMapStyle(AppMode.THINK, idea, MapStyle.RINGS)
        prefs.move("view", idea, GraphLayout.Point(10f, 10f))
    }

    @Test
    fun deletingALabelForgetsItsChoices() = runBlocking {
        prefs.forgetLabel(3)
        val filter = prefs.filter.first()
        assertEquals(setOf(MapFilter.label(4), MapFilter.UNDER), filter.hiddenLinks)
        assertEquals(mapOf(MapFilter.PLAIN to LineStyle.DOTTED), filter.styles)
        assertEquals(mapOf(MapFilter.UNDER to LineWidth.THIN), filter.widths)
    }

    @Test
    fun resettingLinesKeepsWhatIsHidden() = runBlocking {
        prefs.resetLines()
        val filter = prefs.filter.first()
        assertTrue(filter.styles.isEmpty() && filter.widths.isEmpty())
        assertEquals(setOf(MapFilter.label(3), MapFilter.label(4), MapFilter.UNDER), filter.hiddenLinks)
    }

    @Test
    fun anImportForgetsWhatNamesLabelsOrItems() = runBlocking {
        forgetIds(context)
        val filter = prefs.filter.first()
        assertEquals(setOf(MapFilter.UNDER), filter.hiddenLinks)
        assertEquals(mapOf(MapFilter.PLAIN to LineStyle.DOTTED), filter.styles)
        assertEquals(mapOf(MapFilter.UNDER to LineWidth.THIN), filter.widths)
        assertTrue(prefs.positions.first().isEmpty())
        assertEquals(mapOf(AppMode.THINK to mapOf<ItemRef?, MapStyle>(null to MapStyle.TREE)), prefs.styles.first())
    }
}
