package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.xcommandcenter.data.entity.ItemTag
import com.example.xcommandcenter.data.entity.ItemTagEntity
import com.example.xcommandcenter.data.entity.TagEntity
import com.example.xcommandcenter.domain.ItemType
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    /** Tags on at least one item. Tags left behind by deleted items are not offered anywhere. */
    @Query("SELECT * FROM tags WHERE id IN (SELECT tagId FROM item_tags) ORDER BY name")
    fun observeInUse(): Flow<List<TagEntity>>

    @Query("SELECT it.type, it.itemId, t.id AS tagId, t.name FROM item_tags it JOIN tags t ON t.id = it.tagId ORDER BY t.name")
    fun observeAllItemTags(): Flow<List<ItemTag>>

    @Query("SELECT t.* FROM tags t JOIN item_tags it ON it.tagId = t.id WHERE it.type = :type AND it.itemId = :itemId ORDER BY t.name")
    fun observeTags(type: ItemType, itemId: Long): Flow<List<TagEntity>>

    /** Case-insensitive, through the column's NOCASE collation. */
    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun findByName(name: String): TagEntity?

    @Insert
    suspend fun insertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemTag(itemTag: ItemTagEntity)

    @Query("DELETE FROM item_tags WHERE type = :type AND itemId = :itemId AND tagId = :tagId")
    suspend fun deleteItemTag(type: ItemType, itemId: Long, tagId: Long)

    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM item_tags)")
    suspend fun deleteUnused()

    /** Reuses an existing tag of the same name, whatever its case. */
    @Transaction
    suspend fun addTag(type: ItemType, itemId: Long, name: String) {
        val tagId = findByName(name)?.id ?: insertTag(TagEntity(name = name))
        insertItemTag(ItemTagEntity(type, itemId, tagId))
    }

    @Transaction
    suspend fun removeTag(type: ItemType, itemId: Long, tagId: Long) {
        deleteItemTag(type, itemId, tagId)
        deleteUnused()
    }
}
