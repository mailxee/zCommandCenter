package com.example.xcommandcenter.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.xcommandcenter.data.entity.ITEM_INDEX_QUERY

/**
 * Version 16: the Goals tab is gone. Any goal still there becomes a top-level Vision in Ideas, as
 * Settings › Simplify did: title, strategy (as the explanation), priority and creation time kept,
 * after the existing ideas; its links re-made between the new ends (normalized again, the label
 * still read from the same item), its tags and reminders moved; then the goals table goes, and the
 * item index view is made again without it. A manual migration, since the goals must be read
 * before their table is dropped.
 *
 * Links are matched by their stored type names, as text, because "GOAL" is no longer an item type.
 */
object GoalsToVisions {
    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) = run(db)
    }

    fun run(db: SupportSQLiteDatabase) {
        val ideaOf = LinkedHashMap<Long, Long>()
        var order = db.query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM ideas").use { c -> c.moveToFirst(); c.getInt(0) }
        db.query("SELECT id, title, strategy, createdAt, priority FROM goals ORDER BY sortOrder, createdAt").use { c ->
            while (c.moveToNext()) {
                db.execSQL(
                    "INSERT INTO ideas (title, body, kind, parentId, createdAt, sortOrder, priority) VALUES (?, ?, 'VISION', NULL, ?, ?, ?)",
                    arrayOf<Any?>(c.getString(1), c.getString(2), c.getLong(3), order++, c.getString(4)),
                )
                ideaOf[c.getLong(0)] = db.query("SELECT last_insert_rowid()").use { r -> r.moveToFirst(); r.getLong(0) }
            }
        }
        data class Row(val id: Long, val aType: String, val aId: Long, val bType: String, val bId: Long, val createdAt: Long, val labelId: Long?, val fromA: Boolean)
        val rows = db.query("SELECT id, aType, aId, bType, bId, createdAt, labelId, labelFromA FROM links WHERE aType = 'GOAL' OR bType = 'GOAL'").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Row(c.getLong(0), c.getString(1), c.getLong(2), c.getString(3), c.getLong(4), c.getLong(5), if (c.isNull(6)) null else c.getLong(6), c.getInt(7) != 0))
                }
            }
        }
        fun moved(type: String, id: Long): Pair<String, Long> = if (type == "GOAL") ideaOf[id]?.let { "IDEA" to it } ?: (type to id) else type to id
        rows.forEach { r ->
            db.execSQL("DELETE FROM links WHERE id = ?", arrayOf<Any?>(r.id))
            val a = moved(r.aType, r.aId)
            val b = moved(r.bType, r.bId)
            if (a == b || a.first == "GOAL" || b.first == "GOAL") return@forEach
            // Normalized as LinkEntity.between does: by type name, then id; the label keeps its reader.
            val (subject, obj) = if (r.fromA) a to b else b to a
            val (first, second) = listOf(subject, obj).sortedWith(compareBy({ it.first }, { it.second }))
            val fromA = r.labelId == null || first == subject
            db.execSQL(
                "INSERT OR IGNORE INTO links (aType, aId, bType, bId, createdAt, labelId, labelFromA) VALUES (?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(first.first, first.second, second.first, second.second, r.createdAt, r.labelId, if (fromA) 1 else 0),
            )
        }
        ideaOf.forEach { (goal, idea) ->
            db.execSQL("UPDATE item_tags SET type = 'IDEA', itemId = ? WHERE type = 'GOAL' AND itemId = ?", arrayOf<Any?>(idea, goal))
            db.execSQL("UPDATE reminders SET itemType = 'IDEA', itemId = ? WHERE itemType = 'GOAL' AND itemId = ?", arrayOf<Any?>(idea, goal))
        }
        // Anything still naming a goal (there shouldn't be) would no longer load.
        db.execSQL("DELETE FROM item_tags WHERE type = 'GOAL'")
        db.execSQL("DELETE FROM reminders WHERE itemType = 'GOAL'")
        db.execSQL("DELETE FROM links WHERE aType = 'GOAL' OR bType = 'GOAL'")
        db.execSQL("DELETE FROM kept_findings WHERE `key` LIKE '%GOAL%' OR `key` LIKE 'why:%'")
        db.execSQL("DROP VIEW IF EXISTS item_index")
        db.execSQL("DROP TABLE IF EXISTS goals")
        // Room compares the view's text trimmed, as it writes it itself.
        db.execSQL("CREATE VIEW `item_index` AS ${ITEM_INDEX_QUERY.trim()}")
    }
}
