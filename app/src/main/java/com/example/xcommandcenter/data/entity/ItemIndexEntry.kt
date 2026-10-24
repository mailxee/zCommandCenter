package com.example.xcommandcenter.data.entity

import androidx.room.DatabaseView
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType

/**
 * One row per item across every table, with a display title. Linked-item lists and the link
 * picker query this view, so they need no per-type code, and Room re-emits their Flows whenever
 * an underlying table changes (renaming a book updates every list it is linked from).
 *
 * The type literals must match [ItemType] names.
 */
@DatabaseView(
    viewName = "item_index",
    value = """
        SELECT 'TASK' AS type, id, title, NULL AS subtitle FROM tasks
        UNION ALL SELECT 'GOAL', id, title, NULL FROM goals
        UNION ALL SELECT 'HABIT', id, name, NULL FROM habits
        UNION ALL SELECT 'PROJECT', id, title, NULL FROM projects
        UNION ALL SELECT 'NOTE', id,
            CASE WHEN TRIM(title) <> '' THEN title ELSE REPLACE(SUBSTR(body, 1, 80), CHAR(10), ' ') END,
            NULL FROM notes
        UNION ALL SELECT 'BOOK', id, title, NULLIF(author, '') FROM books
        UNION ALL SELECT 'CHAPTER', c.id, c.title, b.title FROM chapters c JOIN books b ON b.id = c.bookId
        UNION ALL SELECT 'QUOTE', id, text, NULLIF(author, '') FROM quotes
    """,
)
data class ItemIndexEntry(
    val type: ItemType,
    val id: Long,
    val title: String,
    /** Author for books and quotes, parent book title for chapters. */
    val subtitle: String?,
) {
    val ref: ItemRef get() = ItemRef(type, id)
}

/** A linked item together with the item it is linked from; feeds the tab lists. */
data class OwnedLink(
    val ownerType: ItemType,
    val ownerId: Long,
    val type: ItemType,
    val id: Long,
    val title: String,
    val subtitle: String?,
) {
    val owner: ItemRef get() = ItemRef(ownerType, ownerId)
    val entry: ItemIndexEntry get() = ItemIndexEntry(type, id, title, subtitle)
}
