package com.example.xcommandcenter.ui.links

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.domain.AppMode
import com.example.xcommandcenter.domain.GraphLayout
import com.example.xcommandcenter.domain.IdeaKind
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.LinkAnalysis
import com.example.xcommandcenter.domain.LinkRoute
import com.example.xcommandcenter.domain.MapFilter
import com.example.xcommandcenter.domain.MapStyle
import com.example.xcommandcenter.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The map's picture with a network of ideas: it opens at a readable size with full titles, and
 * folding asks for the right item. With a build/map-shots folder present, each layout is also
 * saved there as a picture, to look at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h720dp-xhdpi")
class MapCanvasTest {
    @get:Rule
    val compose = createComposeRule()

    private var nextId = 0L
    private val entries = LinkedHashMap<ItemRef, ItemIndexEntry>()
    private val under = mutableListOf<Pair<ItemRef, ItemRef>>()

    private fun idea(title: String, parent: ItemRef? = null): ItemRef {
        val ref = ItemRef(ItemType.IDEA, ++nextId)
        entries[ref] = ItemIndexEntry(ItemType.IDEA, ref.id, title, null)
        if (parent != null) under += ref to parent
        return ref
    }

    private val root = idea("Stoicism – living in agreement with nature")
    private val paradoxes: ItemRef
    private val virtue: ItemRef
    private val vice: ItemRef

    init {
        listOf(
            "Stoicism – The three parts of philosophy" to listOf("Logic", "Physics", "Ethics"),
            "Lived disciplines (Epictetus)" to listOf("Assent (sunkatathesis)", "Action (hormē)", "Desire (orexis)"),
            "Living harmoniously with…" to listOf("Self (living in harmony with oneself)", "World (living in harmony with the cosmos)", "Mankind (living in harmony with others)"),
        ).forEach { (branch, leaves) -> idea(branch, root).let { b -> leaves.forEach { idea(it, b) } } }
        paradoxes = idea("Stoic paradoxes", root)
        listOf("Virtue is the only good", "Virtue is complete in itself for happiness", "All forms of vice are equal", "Everyone who is not wise is mad", "Only the wise man is free", "Only the wise man is rich")
            .forEach { idea(it, paradoxes) }
        virtue = idea("Virtue or aretē", root)
        virtue.let { v -> listOf("Wisdom (sophia)", "Justice, lawfulness (dikaiosynē)", "Courage or fortitude (andreia)", "Moderation or self-control (sōphrosynē)").forEach { idea(it, v) } }
        vice = idea("Vice or Kakia", root)
        vice.let { v -> listOf("Folly or thoughtlessness (aphrosynē)", "Wrongdoing or injustice (adikia)", "Cowardice (deilia)", "Intemperance (akolasia)").forEach { idea(it, v) } }
    }

    private fun state(top: MapStyle, folded: Set<ItemRef> = emptySet(), chosen: Map<ItemRef, MapStyle> = emptyMap()): MapState {
        val refs = entries.keys.toList()
        val tree = GraphLayout.tree(listOf(root), refs, under, firm = under)
        val hidden = folded.flatMap { tree.descendants(it) }.toSet()
        val edges = under.filter { (c, p) -> c !in hidden && p !in hidden }.map { (c, p) ->
            MapEdge(LinkAnalysis.Link(-c.id, c, p), EdgeStyle.UNDER, null, kind = MapFilter.UNDER)
        }
        fun depth(r: ItemRef): Int = tree.parent[r]?.let { depth(it) + 1 } ?: 0
        return MapState(
            mode = AppMode.THINK,
            top = top,
            chosen = chosen,
            root = entries.getValue(root),
            nodes = refs.filter { it !in hidden }.map { MapNode(entries.getValue(it), kind = IdeaKind.IDEA) },
            edges = edges,
            route = LinkRoute(),
            findings = emptyList(),
            stored = emptyMap(),
            tree = tree,
            layers = refs.associateWith(::depth),
            folded = folded,
            foldHidden = hidden,
            explanations = mapOf(paradoxes to "Six claims Cicero collected in Paradoxa Stoicorum."),
        )
    }

    private var foldAsked: ItemRef? = null
    private val actions = MapActions(select = {}, tapNode = {}, moveNode = { _, _ -> }, toggleFold = { foldAsked = it })

    private fun show(initial: MapState): (MapState) -> Unit {
        var current by mutableStateOf(initial)
        compose.setContent {
            AppTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) { MapCanvas(current, null, null, actions) }
            }
        }
        compose.waitForIdle()
        return { current = it; compose.waitForIdle() }
    }

    /** Boxes are measured and placed off the main thread, which the rule doesn't wait for. */
    private fun waitFor(text: String, substring: Boolean = false) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }

    private fun waitForDescription(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty() }

    private fun shoot(name: String) {
        val dir = File("build/map-shots").takeIf { it.isDirectory } ?: return
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun theMindMapOpensReadableWithFullTitles() {
        show(state(MapStyle.MIND_MAP))
        waitFor("75% · Full titles")
        compose.onNodeWithText("75% · Full titles").assertIsDisplayed()
        compose.onNodeWithText("Stoicism – living in agreement with nature").assertIsDisplayed()
        compose.onNodeWithText("Lived disciplines (Epictetus)").assertIsDisplayed()
        shoot("mind-open")
        compose.onNodeWithText("Fit all").performClick()
        waitFor("One-line titles", substring = true)
        compose.onNodeWithText("One-line titles", substring = true).assertIsDisplayed()
        shoot("mind-fit")
        compose.onNodeWithContentDescription("Zoom in").performClick()
        compose.onNodeWithContentDescription("Zoom in").performClick()
        compose.onNodeWithContentDescription("Zoom in").performClick()
        waitFor("Six claims Cicero", substring = true)
        shoot("mind-close")
    }

    @Test
    fun foldingAsksForTheItemAndItsBranchGoes() {
        val update = show(state(MapStyle.MIND_MAP))
        waitForDescription("Fold the 6 items below")
        compose.onNodeWithContentDescription("Fold the 6 items below").performClick()
        assertEquals(paradoxes, foldAsked)
        update(state(MapStyle.MIND_MAP, folded = setOf(paradoxes)))
        waitForDescription("Unfold, 6 items hidden")
        compose.onNodeWithContentDescription("Unfold, 6 items hidden").assertIsDisplayed()
        compose.onNodeWithText("Virtue is the only good").assertDoesNotExist()
        shoot("mind-folded")
    }

    @Test
    fun everyLayoutShowsEveryItem() {
        val update = show(state(MapStyle.BY_ROUTE))
        waitFor("Stoicism – living in agreement with nature")
        shoot("route-open")
        compose.onNodeWithText("Fit all").performClick()
        waitFor("One-line titles", substring = true)
        shoot("route-fit")
        // Every layout, and one map mixing them.
        MapStyle.entries.forEach { style ->
            update(state(style))
            compose.onNodeWithText("Fit all").performClick()
            compose.mainClock.advanceTimeBy(500)
            waitFor("Logic")
            shoot("all-" + style.name.lowercase())
        }
        update(state(MapStyle.MIND_MAP, chosen = mapOf(paradoxes to MapStyle.RINGS, virtue to MapStyle.OUTLINE, vice to MapStyle.TREE)))
        compose.mainClock.advanceTimeBy(500)
        waitFor("Logic")
        shoot("mixed")
    }
}
