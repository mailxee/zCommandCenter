package com.example.xcommandcenter.data.dao

import androidx.room.Query
import com.example.xcommandcenter.domain.ItemType

/**
 * What every item DAO does when deleting an item: removing its links and tags, which have no
 * foreign key because they can point at any table (see ARCHITECTURE.md §2.4). Shared so a new item
 * type gets it by extending this, instead of repeating the queries with its name typed in.
 * The item's reminders are removed by the repositories, which also cancel their alarms.
 */
interface ItemCleanup {
    @Query("DELETE FROM links WHERE (aType = :type AND aId = :id) OR (bType = :type AND bId = :id)")
    suspend fun deleteLinksOf(type: ItemType, id: Long)

    @Query("DELETE FROM item_tags WHERE type = :type AND itemId = :id")
    suspend fun deleteTagsOf(type: ItemType, id: Long)

    /** Call inside the item's delete transaction, before deleting its row. */
    suspend fun deleteRefs(type: ItemType, id: Long) {
        deleteLinksOf(type, id)
        deleteTagsOf(type, id)
    }
}
