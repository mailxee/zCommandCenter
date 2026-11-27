package com.example.xcommandcenter.data.backup

import com.example.xcommandcenter.data.entity.IdeaEntity
import com.example.xcommandcenter.domain.IdeaKind
import org.w3c.dom.Element
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Reads a Word document (.docx) written as an outline into a network of ideas (§2.80), using only
 * the JDK's zip and XML support.
 *
 * Each bulleted or numbered point, and each heading, is an idea; how far it is indented (its list
 * level, or its heading level, headings above list points) says what it sits under. An idea's
 * name is the point's bold start, with a ":" or dash after it taken off, and the rest is its
 * explanation. A point with no bold start is named by its text before a ":", or else by its first
 * sentence. A paragraph that is neither a point nor a heading belongs to the point above it and
 * is added to its explanation; one before the first point may be the document's title.
 */
object WordOutline {
    private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"

    /** A name longer than this is not taken from the text before a ":" or a first sentence. */
    private const val NAME_MAX = 120

    /**
     * What a document holds: its title if it has one (a Title paragraph, or the document's title
     * property), its ideas with ids from 1 and their parents, how many plain paragraphs were added
     * to the explanation above them, and how many before the first point were left out.
     */
    data class Result(val title: String?, val network: BackupData, val joined: Int, val leftOut: Int)

    /** Throws [ImportException] for a file that isn't a Word document or has no outline in it. */
    fun read(input: InputStream, now: Long = System.currentTimeMillis()): Result {
        val files = try {
            unzip(input)
        } catch (e: Exception) {
            throw notWord()
        }
        val document = files["word/document.xml"]?.let(::parseOrNull) ?: throw notWord()
        val styles = files["word/styles.xml"]?.let(::parseOrNull)?.let(::readStyles).orEmpty()
        val propertyTitle = files["docProps/core.xml"]?.let(::parseOrNull)?.descendants("title")?.firstOrNull()?.textContent?.trim()
        return outline(paragraphs(document, styles), propertyTitle?.takeIf { it.isNotEmpty() }, now)
    }

    private fun notWord() = ImportException("This file couldn't be read as a Word document (.docx). Nothing was imported.")

    /** One paragraph: its runs of text, bold or not, and what it is in the outline. */
    internal data class Para(val runs: List<Pair<String, Boolean>>, val heading: Int?, val list: Int?, val title: Boolean) {
        val text: String get() = runs.joinToString("") { it.first }
    }

    /** What a paragraph style says about its paragraphs. */
    private data class Style(val name: String, val outline: Int?, val list: Int?, val bold: Boolean, val basedOn: String?)

    /** The ideas in [paras], with the title and counts for the preview. */
    internal fun outline(paras: List<Para>, propertyTitle: String?, now: Long): Result {
        val ideas = mutableListOf<IdeaEntity>()
        // The open ideas by depth, for finding a point's parent: the nearest one less deep.
        val open = ArrayDeque<Pair<Int, Long>>()
        val nextOrder = HashMap<Long?, Int>()
        var headingDepth: Int? = null
        var title: String? = null
        var leading = mutableListOf<String>()
        var joined = 0
        for (p in paras) {
            val text = p.text.trim()
            if (text.isEmpty()) continue
            val depth = when {
                p.heading != null -> p.heading.also { headingDepth = it }
                p.list != null -> (headingDepth?.plus(1) ?: 0) + p.list
                else -> null
            }
            if (depth == null) {
                when {
                    ideas.isNotEmpty() -> {
                        val last = ideas.removeAt(ideas.lastIndex)
                        ideas += last.copy(body = listOf(last.body, explanation(text)).filter { it.isNotEmpty() }.joinToString("\n\n"))
                        joined++
                    }
                    p.title && title == null -> title = text
                    else -> leading += text
                }
                continue
            }
            while (open.isNotEmpty() && open.last().first >= depth) open.removeLast()
            val parent = open.lastOrNull()?.second
            val (name, body) = split(p.runs, isHeading = p.heading != null)
            val id = ideas.size + 1L
            val order = nextOrder.getOrDefault(parent, 0)
            nextOrder[parent] = order + 1
            ideas += IdeaEntity(id = id, title = name, body = body, kind = IdeaKind.IDEA, parentId = parent, createdAt = now, sortOrder = order)
            open.addLast(depth to id)
        }
        if (ideas.isEmpty()) {
            throw ImportException(
                "No outline was found: the document has no bulleted or numbered points and no headings. Nothing was imported.",
            )
        }
        // A short paragraph before the first point with nothing else there is taken as the title.
        if (title == null && leading.size == 1 && leading[0].length <= NAME_MAX) {
            title = leading[0]
            leading = mutableListOf()
        }
        return Result(title ?: propertyTitle, BackupData(ideas = ideas), joined, leading.size)
    }

    /** A point's name and explanation (see [WordOutline]). */
    internal fun split(runs: List<Pair<String, Boolean>>, isHeading: Boolean = false): Pair<String, String> {
        val whole = runs.joinToString("") { it.first }.trim()
        // The bold start: bold runs, and blank ones between them, up to the first plain text.
        val firstPlain = runs.indexOfFirst { !it.second && it.first.isNotBlank() }.let { if (it < 0) runs.size else it }
        val bold = runs.take(firstPlain).joinToString("") { it.first }.trim()
        val rest = runs.drop(firstPlain).joinToString("") { it.first }
        if (bold.isNotEmpty() && (rest.isNotBlank() || bold.length <= NAME_MAX || isHeading)) {
            return name(bold) to explanation(rest)
        }
        if (isHeading) return name(whole) to ""
        // No bold start (or all of a long point is bold): the text before a ":", or the first sentence.
        val colon = whole.indexOf(':')
        if (colon in 1..NAME_MAX) return name(whole.substring(0, colon)) to explanation(whole.substring(colon + 1))
        if (whole.length <= NAME_MAX) return name(whole) to ""
        val sentence = Regex("""^(.{1,$NAME_MAX}?[.!?])\s""").find(whole)
        if (sentence != null) return name(sentence.groupValues[1].removeSuffix(".")) to explanation(whole.substring(sentence.range.last + 1))
        // One long sentence: its start names it, and all of it explains it.
        val cut = whole.take(NAME_MAX).substringBeforeLast(' ').ifEmpty { whole.take(NAME_MAX) }
        return "$cut…" to whole
    }

    private val trailing = Regex("""[\s:;,\-–—]+$""")
    private val leadingSeparator = Regex("""^[\s:;,\-–—]+""")
    private val example = Regex("""(?<=[.!?)"”])\s+(?=Examples?:)""")

    private fun name(text: String) = text.trim().replace(trailing, "").replace(Regex("""\s+"""), " ").ifEmpty { text.trim() }

    /** The rest of a point: what separated it from the name taken off, and each "Example:" in a paragraph of its own. */
    private fun explanation(text: String) = text.replace(leadingSeparator, "").trim().replace(example, "\n\n")

    // Reading the XML.

    private fun unzip(input: InputStream): Map<String, ByteArray> {
        val files = HashMap<String, ByteArray>()
        val wanted = setOf("word/document.xml", "word/styles.xml", "docProps/core.xml")
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                val name = e.name.removePrefix("/")
                if (!e.isDirectory && name in wanted) files[name] = zip.readBytes()
            }
        }
        return files
    }

    private fun parseOrNull(bytes: ByteArray): Element? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // Documents never need DTDs; refuse them so a crafted file can't pull in external entities.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        factory.newDocumentBuilder().parse(bytes.inputStream()).documentElement
    }.getOrNull()

    private fun Element.children(localName: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<Element>().filter { it.local == localName }

    private fun Element.child(localName: String): Element? = children(localName).firstOrNull()

    private fun Element.descendants(localName: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", localName)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private val Element.local: String get() = localName ?: tagName.substringAfter(':')

    private fun Element.attr(name: String): String? =
        getAttributeNS(W_NS, name).ifEmpty { getAttribute("w:$name") }.ifEmpty { null }

    /** A toggle such as `<w:b/>`: on unless its value says off. */
    private fun Element.on(): Boolean = attr("val")?.lowercase() !in setOf("0", "false", "off", "none")

    /** The document's paragraph styles by id. */
    private fun readStyles(root: Element): Map<String, Style> = root.children("style")
        .filter { (it.attr("type") ?: "paragraph") == "paragraph" }
        .mapNotNull { s ->
            val id = s.attr("styleId") ?: return@mapNotNull null
            val pPr = s.child("pPr")
            id to Style(
                name = s.child("name")?.attr("val")?.lowercase().orEmpty(),
                outline = pPr?.child("outlineLvl")?.attr("val")?.toIntOrNull(),
                list = pPr?.child("numPr")?.let(::listLevel),
                bold = s.child("rPr")?.child("b")?.on() == true,
                basedOn = s.child("basedOn")?.attr("val"),
            )
        }.toMap()

    /** The level of a numbering property, or null when it turns numbering off. */
    private fun listLevel(numPr: Element): Int? =
        if (numPr.child("numId")?.attr("val") == "0") null else numPr.child("ilvl")?.attr("val")?.toIntOrNull() ?: 0

    private val headingName = Regex("""heading\s*(\d)""")
    // "List Bullet 2" and the like are points; "List Paragraph" and "List Continue" are only indented text.
    private val listName = Regex("""list (?:bullet|number)(?:\s*(\d))?""")

    /** The body's paragraphs in order, with what their styles make them; text boxes inside a paragraph aren't read. */
    private fun paragraphs(document: Element, styles: Map<String, Style>): List<Para> {
        val body = document.descendants("body").firstOrNull() ?: return emptyList()
        fun styleChain(id: String?): List<Style> = generateSequence(id?.let(styles::get)) { s -> s.basedOn?.let(styles::get) }.take(10).toList()
        return body.descendants("p").filter { p -> generateSequence(p.parentNode) { it.parentNode }.none { it is Element && it.local == "p" } }.map { p ->
            val pPr = p.child("pPr")
            val chain = styleChain(pPr?.child("pStyle")?.attr("val"))
            val heading = pPr?.child("outlineLvl")?.attr("val")?.toIntOrNull()?.takeIf { it in 0..8 }
                ?: chain.firstNotNullOfOrNull { s -> headingName.find(s.name)?.groupValues?.get(1)?.toInt()?.minus(1) ?: s.outline?.takeIf { it in 0..8 } }
            val numPr = pPr?.child("numPr")
            val list = when {
                numPr != null -> listLevel(numPr)
                else -> chain.firstNotNullOfOrNull { s ->
                    s.list ?: listName.matchEntire(s.name)?.let { m -> (m.groupValues[1].toIntOrNull() ?: 1) - 1 }
                }
            }
            val styleBold = chain.any { it.bold }
            Para(runs(p, styleBold), heading, if (heading != null) null else list, chain.any { it.name == "title" })
        }
    }

    /** A paragraph's runs in order, links and fields included, with whether each is bold. */
    private fun runs(p: Element, styleBold: Boolean): List<Pair<String, Boolean>> = p.descendants("r")
        .filter { r -> generateSequence(r.parentNode) { it.parentNode }.takeWhile { it !== p }.none { it is Element && it.local in setOf("del", "moveFrom", "txbxContent") } }
        .map { r ->
            val rPr = r.child("rPr")
            val b = rPr?.child("b")?.on() ?: (rPr?.child("rStyle")?.attr("val")?.lowercase()?.let { it == "strong" || it.contains("bold") } ?: false || styleBold)
            val text = buildString {
                (0 until r.childNodes.length).map { r.childNodes.item(it) }.filterIsInstance<Element>().forEach { c ->
                    when (c.local) {
                        "t" -> append(c.textContent)
                        "tab" -> append(' ')
                        "br", "cr" -> append('\n')
                        "noBreakHyphen" -> append('-')
                    }
                }
            }
            text to b
        }
        .filter { it.first.isNotEmpty() }
}
