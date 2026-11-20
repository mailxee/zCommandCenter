package com.example.xcommandcenter.data.backup

/**
 * Workbooks from before version 16 have a Goals sheet. Goals are gone, so on import each becomes a
 * Vision in the Ideas sheet, as Settings › Simplify did: its title, strategy (as the explanation),
 * creation time and priority kept, after the ideas already there. Rows elsewhere that name a goal
 * (links, tags, reminders) are pointed at its vision, and the Goals row of the Settings sheet goes.
 * Works on the sheets' text, before [BackupFormat.fromSheets] reads them, so the rest of the import
 * never meets a goal. A workbook without a Goals sheet is returned as it is.
 */
object LegacyGoals {
    private const val GOALS = "Goals"
    private const val IDEAS = "Ideas"
    private val ideaHeader = listOf("ID", "Idea", "Kind", "Explanation", "Parent ID", "Created", "Order", "Priority")
    private val goalNames = setOf("goal", "goals")

    fun toVisions(sheets: List<Sheet>): List<Sheet> {
        val goals = sheets.firstOrNull { it.name.trim().equals(GOALS, true) } ?: return sheets
        val ideas = sheets.firstOrNull { it.name.trim().equals(IDEAS, true) } ?: Sheet(IDEAS, listOf(ideaHeader))

        val g = Columns(goals)
        val i = Columns(ideas)
        var nextId = (ideas.rows.drop(1).mapNotNull { i.get(it, "ID").toLongOrNull() }.maxOrNull() ?: 0) + 1
        var nextOrder = (ideas.rows.drop(1).mapNotNull { i.get(it, "Order").toIntOrNull() }.maxOrNull() ?: -1) + 1
        val ideaOf = HashMap<String, String>()
        val visions = goals.rows.drop(1).filter { row -> row.any { it.isNotBlank() } && g.get(row, "Title").isNotBlank() }.map { row ->
            val id = (nextId++).toString()
            g.get(row, "ID").takeIf { it.isNotBlank() }?.let { ideaOf[it.trim()] = id }
            val values = mapOf(
                "ID" to id, "Idea" to g.get(row, "Title"), "Kind" to "Vision", "Explanation" to g.get(row, "Strategy"),
                "Parent ID" to "", "Created" to g.get(row, "Created"), "Order" to (nextOrder++).toString(), "Priority" to g.get(row, "Priority"),
            )
            i.header.map { values[i.original(it)].orEmpty() }
        }
        val newIdeas = Sheet(ideas.name, listOf(i.header) + ideas.rows.drop(1) + visions)

        return buildList {
            sheets.forEach { sheet ->
                when (sheet.name.trim().lowercase()) {
                    GOALS.lowercase() -> Unit
                    IDEAS.lowercase() -> add(newIdeas)
                    BackupFormat.LINKS.lowercase() -> add(repoint(sheet, ideaOf, "Type A" to "ID A", "Type B" to "ID B"))
                    BackupFormat.TAGS.lowercase(), BackupFormat.REMINDERS.lowercase() -> add(repoint(sheet, ideaOf, "Type" to "ID"))
                    BackupFormat.SETTINGS.lowercase() -> {
                        val c = Columns(sheet)
                        add(sheet.copy(rows = listOf(sheet.rows.first()) + sheet.rows.drop(1).filterNot { c.get(it, "Tab").trim().lowercase() in goalNames }))
                    }
                    else -> add(sheet)
                }
            }
            if (sheets.none { it.name.trim().equals(IDEAS, true) }) add(newIdeas)
        }
    }

    /** [sheet] with each goal named by a (type, id) column pair now naming its vision. */
    private fun repoint(sheet: Sheet, ideaOf: Map<String, String>, vararg pairs: Pair<String, String>): Sheet {
        if (sheet.rows.isEmpty()) return sheet
        val c = Columns(sheet)
        val at = pairs.mapNotNull { (type, id) -> c.index(type)?.let { t -> c.index(id)?.let { t to it } } }
        return sheet.copy(
            rows = listOf(sheet.rows.first()) + sheet.rows.drop(1).mapNotNull { row ->
                val out = row.toMutableList()
                at.forEach { (t, id) ->
                    if (row.getOrNull(t)?.trim()?.lowercase() in goalNames) {
                        // A goal not on the Goals sheet was a reference to a missing item, which the import skipped anyway.
                        val idea = ideaOf[row.getOrNull(id)?.trim().orEmpty()] ?: return@mapNotNull null
                        out[t] = "Idea"
                        out[id] = idea
                    }
                }
                out
            },
        )
    }

    /** A sheet's columns by header, ignoring case. */
    private class Columns(sheet: Sheet) {
        val header: List<String> = sheet.rows.firstOrNull().orEmpty()
        private val byName = header.withIndex().associate { (k, v) -> v.trim().lowercase() to k }
        fun index(name: String): Int? = byName[name.lowercase()]
        fun get(row: List<String>, name: String): String = index(name)?.let { row.getOrNull(it) }.orEmpty()
        /** The standard name for [column] as written in this sheet. */
        fun original(column: String): String = ideaHeader.firstOrNull { it.equals(column.trim(), true) } ?: column
    }
}
