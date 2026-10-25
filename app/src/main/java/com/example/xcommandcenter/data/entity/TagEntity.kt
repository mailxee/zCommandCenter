package com.example.xcommandcenter.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType

/** A tag name, unique ignoring case ("Work" and "work" are the same tag). */
@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
)

/**
 * Which item carries which tag. Like links, the item side has no foreign key because it can be any
 * of eight tables; every item DAO's delete removes the item's rows here.
 */
@Entity(
    tableName = "item_tags",
    primaryKeys = ["type", "itemId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class ItemTagEntity(val type: ItemType, val itemId: Long, val tagId: Long)

/** One tag on one item, with the tag's name. */
data class ItemTag(val type: ItemType, val itemId: Long, val tagId: Long, val name: String) {
    val ref: ItemRef get() = ItemRef(type, itemId)
    val tag: TagEntity get() = TagEntity(tagId, name)
}
