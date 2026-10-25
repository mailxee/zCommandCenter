package com.example.xcommandcenter.data

import com.example.xcommandcenter.data.backup.Sheet
import com.example.xcommandcenter.data.backup.Xlsx
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxTest {
    @Test
    fun `written workbook reads back the same`() {
        val sheets = listOf(
            Sheet("Tasks", listOf(listOf("ID", "Title", "Note"), listOf("1", "Buy <milk> & \"eggs\"", "line one\nline two"), listOf("2", "", "007"))),
            Sheet("Habit history", listOf(listOf("Habit ID", "Date"), listOf("3", "2026-10-25"))),
        )
        val bytes = ByteArrayOutputStream().also { Xlsx.write(sheets, it) }.toByteArray()
        val read = Xlsx.read(ByteArrayInputStream(bytes))
        assertEquals(listOf("Tasks", "Habit history"), read.map { it.name })
        // A blank cell in the middle of a row reads back as "", and the leading zero survives as text.
        assertEquals(sheets[0].rows, read[0].rows)
        assertEquals(sheets[1].rows, read[1].rows)
    }

    @Test
    fun `reads shared strings, booleans and gaps the way Excel saves them`() {
        val bytes = zip(
            "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Goals" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="x" Target="/xl/worksheets/sheet1.xml"/></Relationships>""",
            "xl/sharedStrings.xml" to """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><si><t>ID</t></si><si><r><t>Ti</t></r><r><t>tle</t></r></si><si><t>Run</t></si></sst>""",
            "xl/worksheets/sheet1.xml" to """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
                <row r="3"><c r="A3"><v>12</v></c><c r="C3" t="b"><v>1</v></c><c r="B3" t="s"><v>2</v></c></row>
                </sheetData></worksheet>""",
        )
        val rows = Xlsx.read(ByteArrayInputStream(bytes)).single().rows
        assertEquals(listOf("ID", "Title"), rows[0])
        assertEquals(emptyList<String>(), rows[1])
        assertEquals(listOf("12", "Run", "TRUE"), rows[2])
    }

    @Test
    fun `column letters convert both ways`() {
        assertEquals("A", Xlsx.columnName(0))
        assertEquals("Z", Xlsx.columnName(25))
        assertEquals("AA", Xlsx.columnName(26))
        assertEquals(27, Xlsx.columnIndex("AB12"))
    }

    private fun zip(vararg files: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, xml) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(xml.toByteArray())
                zip.closeEntry()
            }
        }
    }.toByteArray()
}
