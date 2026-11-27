package com.example.xcommandcenter.ui.links

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.example.xcommandcenter.domain.AppMode
import com.example.xcommandcenter.domain.GraphLayout
import com.example.xcommandcenter.domain.IdeaKind
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.LinkAnalysis
import com.example.xcommandcenter.domain.LinkRoute
import com.example.xcommandcenter.domain.MapFilter
import com.example.xcommandcenter.domain.MapStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * The map as a PDF (§2.81): on one page it is scaled to fit and centred; across pages it keeps a
 * readable size and splits into a grid of overlapping pages. Robolectric can't write a PDF, so
 * the pages are drawn as pictures with the same drawing; with a build/map-shots folder present
 * they are saved there, to look at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MapPdfTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun state(branches: Int, leaves: Int): MapState {
        var id = 0L
        val entries = LinkedHashMap<ItemRef, ItemIndexEntry>()
        val under = mutableListOf<Pair<ItemRef, ItemRef>>()
        fun idea(title: String, parent: ItemRef?): ItemRef {
            val ref = ItemRef(ItemType.IDEA, ++id)
            entries[ref] = ItemIndexEntry(ItemType.IDEA, ref.id, title, null)
            if (parent != null) under += ref to parent
            return ref
        }
        val root = idea("Messy Jobs (book)", null)
        repeat(branches) { b ->
            val branch = idea("Part ${b + 1}: a branch with a longer title", root)
            repeat(leaves) { l -> idea("Point ${b + 1}.${l + 1} of the branch", branch) }
        }
        val refs = entries.keys.toList()
        val tree = GraphLayout.tree(listOf(root), refs, under, firm = under)
        fun depth(r: ItemRef): Int = tree.parent[r]?.let { depth(it) + 1 } ?: 0
        return MapState(
            mode = AppMode.THINK, top = MapStyle.MIND_MAP, root = entries.getValue(root),
            nodes = refs.map { MapNode(entries.getValue(it), kind = IdeaKind.IDEA) },
            edges = under.map { (c, p) -> MapEdge(LinkAnalysis.Link(-c.id, c, p), EdgeStyle.UNDER, null, kind = MapFilter.UNDER) },
            route = LinkRoute(), findings = emptyList(), stored = emptyMap(), tree = tree, layers = refs.associateWith(::depth),
        )
    }

    private val styles = NodeStyles(
        title = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        root = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        explain = TextStyle(fontSize = 11.sp),
        fold = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
    )

    private val colors = PrintColors(
        kinds = IdeaKind.entries.associateWith { Color(0xFF6A4FB0) }, tabs = ItemType.entries.associateWith { Color(0xFF1565C0) },
        outline = Color(0xFF79747E), text = Color(0xFF1C1B1F), subtle = Color(0xFF49454F), chip = Color(0xFFE6E0E9),
    )

    private fun view(state: MapState) = MapPrintView(state, Detail.NORMAL, bothSides = false, routeWidth = 480f, styles = styles)

    /**
     * Draws every page of [plan] the way [MapPdf.write] does, onto pictures (Robolectric can't
     * write a PDF), saved to build/map-shots when that folder exists. Returns how many pages.
     */
    private fun pages(plan: MapPdf.Plan, name: String): Int {
        val measurer = TextMeasurer(createFontFamilyResolver(context), Density(1f), LayoutDirection.Ltr)
        val icons = MapPdf.Icons(context)
        var number = 0
        for (row in 0 until plan.rows) for (col in 0 until plan.columns) {
            number++
            val k = 2f // Twice the points, to see the detail.
            val bitmap = Bitmap.createBitmap((plan.pageWidth * k).toInt(), (plan.pageHeight * k).toInt(), Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap).apply { scale(k, k) }
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(canvas), Size(plan.pageWidth, plan.pageHeight)) {
                with(MapPdf) { drawPage(plan, row, col, number, colors, "Messy Jobs (book)", LocalDate.of(2026, 11, 27), measurer, icons) }
            }
            File("build/map-shots").takeIf { it.isDirectory }?.let { dir ->
                File(dir, "$name-$number.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
        return number
    }

    @Test
    fun onOnePageTheWholeMapFits() {
        val plan = MapPdf.plan(context, view(state(6, 6)), Paper.A4, PdfFit.ONE_PAGE)
        assertEquals(1, plan.pages)
        assertTrue(plan.placed.width * plan.scale <= plan.areaWidth + 0.5f)
        assertTrue(plan.placed.height * plan.scale <= plan.areaHeight + 0.5f)
        assertEquals(1, pages(plan, "map-one-page"))
    }

    @Test
    fun acrossPagesItStaysReadable() {
        val big = MapPdf.plan(context, view(state(8, 7)), Paper.A4, PdfFit.PAGES)
        assertEquals(0.8f, big.scale)
        assertTrue("pages: ${big.columns} x ${big.rows}", big.pages > 1)
        // The pages, overlapping, cover the whole map.
        assertTrue((big.areaWidth - MapPdf.OVERLAP) * big.columns + MapPdf.OVERLAP >= big.placed.width * big.scale)
        assertTrue((big.areaHeight - MapPdf.OVERLAP) * big.rows + MapPdf.OVERLAP >= big.placed.height * big.scale)
        // Bigger paper, fewer pages.
        assertTrue(MapPdf.plan(context, view(state(8, 7)), Paper.A3, PdfFit.PAGES).pages < big.pages)
        assertEquals(big.pages, pages(big, "map-pages"))
        // A small map needs only one page.
        assertEquals(1, MapPdf.plan(context, view(state(1, 2)), Paper.A4, PdfFit.PAGES).pages)
    }

    @Test
    fun fileNamesAreSafe() {
        assertEquals("Map-Messy-Jobs-(book)-2026-11-27.pdf", MapPdf.fileName("Messy Jobs (book)", LocalDate.of(2026, 11, 27)))
        assertEquals("Map-Every-network-2026-11-27.pdf", MapPdf.fileName("Every network", LocalDate.of(2026, 11, 27)))
    }
}
