package com.example.xcommandcenter.ui.links

import android.content.Context
import android.graphics.pdf.PdfDocument
import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.example.xcommandcenter.R
import com.example.xcommandcenter.domain.GraphLayout
import com.example.xcommandcenter.domain.IdeaKind
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.common.kindColor
import com.example.xcommandcenter.ui.common.kindIcon
import com.example.xcommandcenter.ui.common.tabAccent
import com.example.xcommandcenter.ui.common.tabIcon
import org.xmlpull.v1.XmlPullParser
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import androidx.compose.ui.graphics.Canvas as ComposeCanvas

/** What the map shows now, for printing it as it is (§2.81): the same items, layout and detail. */
internal class MapPrintView(val state: MapState, val detail: Detail, val bothSides: Boolean, val routeWidth: Float, val styles: NodeStyles)

/** The map's colours on paper: the light theme's, whatever the screen uses. */
internal class PrintColors(
    val kinds: Map<IdeaKind, Color>,
    val tabs: Map<ItemType, Color>,
    val outline: Color,
    val text: Color,
    val subtle: Color,
    val chip: Color,
)

/** The colours of the theme around it; call it inside the light theme. */
@Composable
internal fun printColors(): PrintColors {
    val c = MaterialTheme.colorScheme
    return PrintColors(
        kinds = IdeaKind.entries.associateWith { kindColor(it) },
        tabs = ItemType.entries.associateWith { tabAccent(it) },
        outline = c.outline,
        text = c.onSurface,
        subtle = c.onSurfaceVariant,
        chip = c.surfaceContainerHighest,
    )
}

/** Paper sizes, in points (1/72 inch), upright. */
enum class Paper(val label: String, val width: Float, val height: Float) {
    A4("A4", 595f, 842f),
    LETTER("Letter", 612f, 792f),
    A3("A3", 842f, 1191f),
    ;

    companion object {
        /** Letter where it is the usual paper, A4 elsewhere. */
        fun usual(country: String): Paper = if (country.uppercase() in setOf("US", "CA", "MX", "PH", "CL", "CO", "VE")) LETTER else A4
    }
}

/** On one page, as large as fits; or across pages at a size that reads well, to join. */
enum class PdfFit { ONE_PAGE, PAGES }

/**
 * The map as a vector PDF (§2.81): its boxes, lines, labels and icons drawn as shapes and text,
 * not a picture, so it prints sharp at any size. It is laid out again at print size from what the
 * screen shows, in the light theme's colours, with a title and the date at the top of each page.
 */
internal object MapPdf {
    private const val MARGIN = 28f
    private const val HEADER = 26f
    private const val FOOTER = 16f

    /** Across pages, how much each page repeats of the next, for joining them. */
    const val OVERLAP = 18f

    /** Across pages, the map's size: its 12 sp titles come out at about 10 pt. */
    private const val READABLE_SCALE = 0.8f

    /** On one page, a small map is enlarged no more than this. */
    private const val MOST_ENLARGED = 1.5f

    private val density = Density(1f, 1f)

    /** How the map goes on paper: laid out at print size, the page's size and turn, its scale and the grid of pages. */
    class Plan internal constructor(
        internal val placed: Placed,
        internal val styles: NodeStyles,
        val pageWidth: Float,
        val pageHeight: Float,
        val scale: Float,
        val columns: Int,
        val rows: Int,
    ) {
        val pages: Int get() = columns * rows
        val landscape: Boolean get() = pageWidth > pageHeight
        internal val areaWidth get() = pageWidth - 2 * MARGIN
        internal val areaHeight get() = pageHeight - 2 * MARGIN - HEADER - if (pages > 1) FOOTER else 0f
    }

    private fun measurer(context: Context) = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr, cacheSize = 0)

    /** Lays [view] out at print size and works out its pages on [paper], turned whichever way fits best. */
    fun plan(context: Context, view: MapPrintView, paper: Paper, fit: PdfFit): Plan {
        val measurer = measurer(context)
        val sizes = view.state.nodes.associate { it.ref to measureNode(it, view.state, view.detail, measurer, view.styles, density) }
        val placed = place(view.state, sizes, view.detail, view.bothSides, view.routeWidth)
        val w = placed.width
        val h = placed.height
        fun areaW(pw: Float) = pw - 2 * MARGIN
        fun areaH(ph: Float, many: Boolean) = ph - 2 * MARGIN - HEADER - if (many) FOOTER else 0f
        val turns = listOf(paper.width to paper.height, paper.height to paper.width)
        return when (fit) {
            PdfFit.ONE_PAGE -> {
                val (pw, ph) = turns.maxBy { (pw, ph) -> min(areaW(pw) / w, areaH(ph, false) / h) }
                Plan(placed, view.styles, pw, ph, min(min(areaW(pw) / w, areaH(ph, false) / h), MOST_ENLARGED), 1, 1)
            }
            PdfFit.PAGES -> {
                val s = READABLE_SCALE
                fun grid(pw: Float, ph: Float): Pair<Int, Int> {
                    if (w * s <= areaW(pw) && h * s <= areaH(ph, false)) return 1 to 1
                    fun count(content: Float, area: Float) = if (content <= area) 1 else 1 + ceil((content - area) / (area - OVERLAP)).toInt()
                    return count(w * s, areaW(pw)) to count(h * s, areaH(ph, true))
                }
                val (pw, ph) = turns.minBy { (pw, ph) -> grid(pw, ph).let { it.first * it.second } }
                val (cols, rows) = grid(pw, ph)
                Plan(placed, view.styles, pw, ph, s, cols, rows)
            }
        }
    }

    /** Writes [plan] as a PDF to [out]. */
    fun write(context: Context, plan: Plan, colors: PrintColors, title: String, out: OutputStream, date: LocalDate = LocalDate.now()) {
        val doc = PdfDocument()
        try {
            val icons = Icons(context)
            val measurer = measurer(context)
            var number = 0
            for (row in 0 until plan.rows) for (col in 0 until plan.columns) {
                number++
                val page = doc.startPage(PdfDocument.PageInfo.Builder(plan.pageWidth.toInt(), plan.pageHeight.toInt(), number).create())
                CanvasDrawScope().draw(density, LayoutDirection.Ltr, ComposeCanvas(page.canvas), Size(plan.pageWidth, plan.pageHeight)) {
                    drawPage(plan, row, col, number, colors, title, date, measurer, icons)
                }
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    /** One page: the title and date, its part of the map, and on a map across pages where it goes. */
    internal fun DrawScope.drawPage(
        plan: Plan, row: Int, col: Int, number: Int, colors: PrintColors, title: String, date: LocalDate,
        measurer: TextMeasurer, icons: Icons,
    ) {
        drawRect(Color.White)
        val dateText = date.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))
        val dateLayout = measurer.measure(dateText, TextStyle(fontSize = 9.sp, color = colors.subtle))
        drawText(dateLayout, topLeft = Offset(plan.pageWidth - MARGIN - dateLayout.size.width, MARGIN + 3f))
        val titleLayout = measurer.measure(
            title, TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.text), TextOverflow.Ellipsis, maxLines = 1,
            constraints = Constraints(maxWidth = (plan.areaWidth - dateLayout.size.width - 12f).toInt().coerceAtLeast(1)),
        )
        drawText(titleLayout, topLeft = Offset(MARGIN, MARGIN))
        if (plan.pages > 1) {
            val where = "Page $number of ${plan.pages} · row ${row + 1} of ${plan.rows}, column ${col + 1} of ${plan.columns} · " +
                "pages overlap by ${(OVERLAP / 72f * 25.4f).toInt()} mm for joining"
            val footer = measurer.measure(where, TextStyle(fontSize = 8.sp, color = colors.subtle), TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = plan.areaWidth.toInt()))
            drawText(footer, topLeft = Offset(MARGIN, plan.pageHeight - MARGIN - footer.size.height))
        }
        val top = MARGIN + HEADER
        val stepX = plan.areaWidth - OVERLAP
        val stepY = plan.areaHeight - OVERLAP
        // On one page the map is centred; across pages each shows its part.
        val ox = if (plan.pages == 1) (plan.areaWidth - plan.placed.width * plan.scale) / 2 else -col * stepX
        val oy = if (plan.pages == 1) 0f else -row * stepY
        drawContext.canvas.save()
        drawContext.canvas.clipRect(MARGIN, top, MARGIN + plan.areaWidth, top + plan.areaHeight)
        translate(MARGIN + ox, top + oy) {
            scale(plan.scale, Offset.Zero) { drawMap(plan.placed, plan.styles, colors, measurer, icons) }
        }
        drawContext.canvas.restore()
    }

    /** The map's lines, boxes and labels, in the order the screen stacks them. */
    private fun DrawScope.drawMap(p: Placed, styles: NodeStyles, colors: PrintColors, measurer: TextMeasurer, icons: Icons) {
        val state = p.state
        fun centreOf(ref: ItemRef): Offset = p.at.getValue(ref).let { Offset(it.x - p.originX, it.y - p.originY) }
        val edges = state.edges.filter { it.link.a in p.at && it.link.b in p.at }
        val shapes = edges.associate { it.link.id to shapeOf(it, p, ::centreOf, 1f) }
        val over = edges.filter { crossesTree(it, p) }.map { it.link.id }.toSet()
        edges.filter { it.link.id !in over }.forEach { drawEdge(it, shapes.getValue(it.link.id), null, null, colors.outline) }
        state.nodes.forEach { node -> p.sizes[node.ref]?.let { drawNode(node, p, styles, it, centreOf(node.ref), colors, measurer, icons) } }
        edges.filter { it.link.id in over }.forEach { drawEdge(it, shapes.getValue(it.link.id), null, null, colors.outline) }
        val labelStyle = TextStyle(fontSize = 10.sp, fontStyle = FontStyle.Italic)
        edges.filter { it.label != null }.forEach { edge ->
            val mid = shapes.getValue(edge.link.id).pointAt(0.5f)
            val layout = measurer.measure(
                edge.label.orEmpty(), labelStyle.copy(color = if (edge.style == EdgeStyle.CONTRADICTS) ContradictColor else colors.subtle),
                TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = (LabelWidth.value - 6f).toInt()),
            )
            val w = layout.size.width + 6f
            val left = mid.x - w / 2
            val top = mid.y - 8f
            drawRoundRect(Color.White, Offset(left, top), Size(w, layout.size.height.toFloat()), CornerRadius(4f))
            drawText(layout, topLeft = Offset(left + 3f, top))
        }
    }

    /** One box, as the screen's NodeBox draws it: icon, title, explanation when shown, and the fold count. */
    private fun DrawScope.drawNode(node: MapNode, p: Placed, styles: NodeStyles, size: GraphLayout.Size, c: Offset, colors: PrintColors, measurer: TextMeasurer, icons: Icons) {
        val state = p.state
        val accent = node.kind?.let { colors.kinds.getValue(it) } ?: if (node.otherMode) OtherModeColor else colors.tabs.getValue(node.entry.type)
        val root = node.ref == state.root?.ref
        val left = c.x - size.w / 2
        val top = c.y - size.h / 2
        val radius = CornerRadius(10f)
        drawRoundRect(Color.White, Offset(left, top), Size(size.w, size.h), radius)
        if (node.otherMode) drawRoundRect(OtherModeColor.copy(alpha = 0.08f), Offset(left, top), Size(size.w, size.h), radius)
        val border = if (root) 2f else 1f
        drawRoundRect(
            if (root) accent else accent.copy(alpha = 0.6f), Offset(left + border / 2, top + border / 2),
            Size(size.w - border, size.h - border), CornerRadius(10f - border / 2), style = Stroke(border),
        )
        var x = left + NodePadStart.value
        val y = top + NodePadV.value
        if (node.otherMode) {
            icons.draw(this, R.drawable.ic_swap, Offset(x, y + 1f), SwapIcon.value, OtherModeColor)
            x += SwapIcon.value + SwapGap.value
        }
        icons.draw(this, node.kind?.let { kindIcon(it) } ?: tabIcon(node.entry.type), Offset(x, y + 1f), NodeIcon.value, accent)
        x += NodeIcon.value + NodeIconGap.value
        val below = foldable(node, state)
        val folded = node.ref in state.folded
        val fold = if (below > 0) measurer.measure(foldLabel(folded, below), styles.fold.copy(color = if (folded) Color.White else accent), maxLines = 1) else null
        val foldW = fold?.let { max(FoldSize.value, it.size.width + FoldPad.value * 2) } ?: 0f
        val right = left + size.w - NodePadEnd.value - if (fold != null) foldW + FoldGap.value else 0f
        val textW = (right - x).toInt().coerceAtLeast(1)
        val titleLayout = measurer.measure(
            node.entry.title, (if (root) styles.root else styles.title).copy(color = colors.text), TextOverflow.Ellipsis,
            maxLines = p.detail.titleLines, constraints = Constraints(maxWidth = textW),
        )
        drawText(titleLayout, topLeft = Offset(x, y))
        explanationOf(node, state, p.detail)?.let {
            val e = measurer.measure(it, styles.explain.copy(color = colors.subtle), TextOverflow.Ellipsis, maxLines = 2, constraints = Constraints(maxWidth = textW))
            drawText(e, topLeft = Offset(x, y + titleLayout.size.height + ExplainGap.value))
        }
        if (fold != null) {
            val h = max(FoldSize.value, fold.size.height.toFloat())
            val fx = left + size.w - NodePadEnd.value - foldW
            drawRoundRect(if (folded) accent else colors.chip, Offset(fx, c.y - h / 2), Size(foldW, h), CornerRadius(h / 2))
            drawText(fold, topLeft = Offset(fx + (foldW - fold.size.width) / 2, c.y - fold.size.height / 2f))
        }
    }

    /** The app's icons as paths, read from their vector drawables, so they stay shapes in the PDF. */
    internal class Icons(private val context: Context) {
        private class Icon(val viewport: Float, val paths: List<Path>)

        private val cache = HashMap<Int, Icon?>()

        private fun load(@DrawableRes id: Int): Icon? = cache.getOrPut(id) {
            runCatching {
                val android = "http://schemas.android.com/apk/res/android"
                val parser = context.resources.getXml(id)
                var viewport = 24f
                val paths = mutableListOf<Path>()
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != XmlPullParser.START_TAG) continue
                    when (parser.name) {
                        "vector" -> viewport = parser.getAttributeFloatValue(android, "viewportWidth", 24f)
                        "path" -> parser.getAttributeValue(android, "pathData")?.let { paths += PathParser().parsePathString(it).toPath() }
                    }
                }
                parser.close()
                Icon(viewport, paths)
            }.getOrNull()
        }

        /** Draws icon [id] filled with [color], [size] across, its top left at [at]. */
        fun draw(scope: DrawScope, @DrawableRes id: Int, at: Offset, size: Float, color: Color) {
            val icon = load(id) ?: return
            scope.translate(at.x, at.y) {
                scale(size / icon.viewport, Offset.Zero) { icon.paths.forEach { drawPath(it, color) } }
            }
        }
    }

    /** "Map-<title>-<date>.pdf", with characters file systems refuse taken out. */
    fun fileName(title: String, date: LocalDate = LocalDate.now()): String {
        val safe = title.replace(Regex("[\\\\/:*?\"<>|\\s]+"), " ").trim().take(40).replace(' ', '-')
        return if (safe.isEmpty()) "Map-$date.pdf" else "Map-$safe-$date.pdf"
    }
}
