package com.example.xcommandcenter.data.backup

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/** One worksheet: its tab name and its rows of cell text. The first row is the header. */
data class Sheet(val name: String, val rows: List<List<String>>)

/**
 * The small part of the Office Open XML spreadsheet format the backup needs, using only the JDK's
 * zip and XML support.
 *
 * Writing produces a valid workbook: inline-string cells, whole numbers as numeric cells, a bold
 * frozen header row. Reading accepts what Excel, LibreOffice or Google Sheets save: shared or
 * inline strings, numbers, booleans and formula results, all returned as text.
 */
object Xlsx {
    private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private val wholeNumber = Regex("-?(0|[1-9][0-9]{0,14})")

    fun write(sheets: List<Sheet>, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, xml: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", contentTypes(sheets.size))
            entry("_rels/.rels", ROOT_RELS)
            entry("xl/workbook.xml", workbook(sheets))
            entry("xl/_rels/workbook.xml.rels", workbookRels(sheets.size))
            entry("xl/styles.xml", STYLES)
            sheets.forEachIndexed { i, sheet -> entry("xl/worksheets/sheet${i + 1}.xml", worksheet(sheet)) }
        }
    }

    /** Throws [IllegalArgumentException] when the file is not a readable workbook. */
    fun read(input: InputStream): List<Sheet> {
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory) files[e.name.removePrefix("/")] = zip.readBytes()
            }
        }
        val workbook = files["xl/workbook.xml"] ?: throw IllegalArgumentException("This file isn't an Excel workbook (.xlsx).")
        val targets = files["xl/_rels/workbook.xml.rels"]?.let(::parse)?.childElements("Relationship")
            ?.associate { it.getAttribute("Id") to it.getAttribute("Target") }.orEmpty()
        val shared = files["xl/sharedStrings.xml"]?.let(::parse)?.childElements("si")?.map { it.text() }.orEmpty()

        return parse(workbook).descendants("sheet").map { sheetEl ->
            val name = sheetEl.getAttribute("name")
            val relId = sheetEl.getAttributeNS(REL_NS, "id").ifEmpty { sheetEl.getAttribute("r:id") }
            val target = targets[relId] ?: throw IllegalArgumentException("Sheet \"$name\" can't be found in the file.")
            val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/" + target.removePrefix("./")
            val xml = files[path] ?: throw IllegalArgumentException("Sheet \"$name\" can't be found in the file.")
            Sheet(name, readRows(parse(xml), shared))
        }
    }

    private fun readRows(doc: Element, shared: List<String>): List<List<String>> {
        val rows = sortedMapOf<Int, MutableMap<Int, String>>()
        var nextRow = 1
        doc.descendants("row").forEach { rowEl ->
            val r = rowEl.getAttribute("r").toIntOrNull() ?: nextRow
            nextRow = r + 1
            val cells = rows.getOrPut(r) { sortedMapOf() }
            var nextCol = 0
            rowEl.childElements("c").forEach { c ->
                val col = c.getAttribute("r").takeIf { it.isNotEmpty() }?.let(::columnIndex) ?: nextCol
                nextCol = col + 1
                val v = c.childElements("v").firstOrNull()?.textContent.orEmpty()
                cells[col] = when (c.getAttribute("t")) {
                    "s" -> v.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                    "inlineStr" -> c.childElements("is").firstOrNull()?.text().orEmpty()
                    "b" -> if (v == "1") "TRUE" else "FALSE"
                    "e" -> ""
                    else -> v
                }
            }
        }
        val last = rows.keys.maxOrNull() ?: return emptyList()
        return (1..last).map { r ->
            val cells = rows[r].orEmpty()
            val width = (cells.keys.maxOrNull() ?: -1) + 1
            (0 until width).map { cells[it].orEmpty() }
        }
    }

    // --- writing ---

    private fun worksheet(sheet: Sheet): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="$MAIN_NS"><sheetViews><sheetView workbookViewId="0">""")
        append("""<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/></sheetView></sheetViews><sheetData>""")
        sheet.rows.forEachIndexed { r, row ->
            append("""<row r="${r + 1}">""")
            row.forEachIndexed { c, value ->
                if (value.isEmpty()) return@forEachIndexed
                val ref = columnName(c) + (r + 1)
                val style = if (r == 0) """ s="1"""" else ""
                if (r > 0 && wholeNumber.matches(value)) {
                    append("""<c r="$ref"$style><v>$value</v></c>""")
                } else {
                    append("""<c r="$ref"$style t="inlineStr"><is><t xml:space="preserve">${escape(value)}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun workbook(sheets: List<Sheet>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="$MAIN_NS" xmlns:r="$REL_NS"><sheets>""")
        sheets.forEachIndexed { i, s -> append("""<sheet name="${escape(s.name)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""") }
        append("</sheets></workbook>")
    }

    private fun workbookRels(count: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..count) {
            append("""<Relationship Id="rId$i" Type="$REL_NS/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${count + 1}" Type="$REL_NS/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private fun contentTypes(count: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..count) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("</Types>")
    }

    private const val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    /** Style 0 is plain, style 1 is the bold header. */
    private const val STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""

    /** XML-escapes text and drops characters XML 1.0 can't hold (most control characters). */
    private fun escape(text: String): String = buildString {
        text.forEach { ch ->
            when {
                ch == '&' -> append("&amp;")
                ch == '<' -> append("&lt;")
                ch == '>' -> append("&gt;")
                ch == '"' -> append("&quot;")
                ch == '\t' || ch == '\n' || ch == '\r' || ch >= ' ' -> append(ch)
            }
        }
    }

    /** 0 -> A, 25 -> Z, 26 -> AA. */
    internal fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append('A' + rem)
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    /** "B7" -> 1, "AA3" -> 26. */
    internal fun columnIndex(ref: String): Int =
        ref.takeWhile { it.isLetter() }.uppercase().fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) } - 1

    // --- XML helpers ---

    private fun parse(bytes: ByteArray): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // Workbooks never need DTDs; refuse them so a crafted file can't pull in external entities.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        return factory.newDocumentBuilder().parse(bytes.inputStream()).documentElement
    }

    private fun Element.childElements(localName: String): List<Element> =
        (0 until childNodes.length).map { childNodes.item(it) }
            .filterIsInstance<Element>()
            .filter { (it.localName ?: it.tagName.substringAfter(':')) == localName }

    private fun Element.descendants(localName: String): List<Element> {
        val nodes = getElementsByTagNameNS("*", localName)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    /** Text of a string item: plain `<t>` or rich-text runs `<r><t>`; phonetic hints are skipped. */
    private fun Element.text(): String = buildString {
        fun visit(node: Node) {
            if (node is Element) {
                val name = node.localName ?: node.tagName
                if (name == "rPh") return
                if (name == "t") {
                    append(node.textContent)
                    return
                }
            }
            for (i in 0 until node.childNodes.length) visit(node.childNodes.item(i))
        }
        visit(this@text)
    }
}
