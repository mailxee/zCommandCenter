package com.example.xcommandcenter.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType

/**
 * The single many-to-many link table. A link is undirected: it is stored once, with the pair
 * normalized so that side A sorts before side B (see [between]). The unique index then rejects
 * both A-B and B-A duplicates, and queries match the item on either side.
 */
@Entity(
    tableName = "links",
    indices = [
        Index(value = ["aType", "aId", "bType", "bId"], unique = true),
        Index(value = ["bType", "bId"]),
    ],
)
data class LinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val aType: ItemType,
    val aId: Long,
    val bType: ItemType,
    val bId: Long,
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        private val order = compareBy<ItemRef>({ it.type.name }, { it.id })

        fun between(first: ItemRef, second: ItemRef): LinkEntity {
            require(first != second) { "An item cannot be linked to itself" }
            val (a, b) = if (order.compare(first, second) <= 0) first to second else second to first
            return LinkEntity(aType = a.type, aId = a.id, bType = b.type, bId = b.id)
        }
    }
}
