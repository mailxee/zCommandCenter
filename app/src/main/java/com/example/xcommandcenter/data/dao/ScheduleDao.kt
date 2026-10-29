package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.xcommandcenter.data.entity.BlockEntity
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.data.entity.ScheduleEntity
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules ORDER BY sortOrder, id")
    suspend fun all(): List<ScheduleEntity>

    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM schedules")
    suspend fun nextSortOrder(): Int

    @Insert
    suspend fun insert(schedule: ScheduleEntity): Long

    @Update
    suspend fun update(schedules: List<ScheduleEntity>)

    @Query("UPDATE schedules SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("SELECT * FROM blocks WHERE scheduleId = :scheduleId")
    suspend fun blocks(scheduleId: Long): List<BlockEntity>

    @Insert
    suspend fun insertBlock(block: BlockEntity): Long

    @Query("SELECT * FROM links WHERE (aType = 'BLOCK' AND aId = :blockId) OR (bType = 'BLOCK' AND bId = :blockId)")
    suspend fun blockLinks(blockId: Long): List<LinkEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertLinks(links: List<LinkEntity>)

    @Query("INSERT INTO item_tags (type, itemId, tagId) SELECT type, :to, tagId FROM item_tags WHERE type = 'BLOCK' AND itemId = :from")
    suspend fun copyTags(from: Long, to: Long)

    @Query("INSERT INTO reminders (itemType, itemId, time) SELECT itemType, :to, time FROM reminders WHERE itemType = 'BLOCK' AND itemId = :from")
    suspend fun copyReminders(from: Long, to: Long)

    /** A copy of schedule [id] named [name], last in order, with a copy of every block and the blocks' links, tags and reminders. Returns the new blocks' ids. */
    @Transaction
    suspend fun duplicate(id: Long, name: String): List<Long> {
        val source = all().firstOrNull { it.id == id } ?: return emptyList()
        val copy = insert(ScheduleEntity(name = name, sortOrder = nextSortOrder()))
        return blocks(source.id).map { block ->
            val newId = insertBlock(block.copy(id = 0, scheduleId = copy, createdAt = System.currentTimeMillis()))
            val self = ItemRef(ItemType.BLOCK, block.id)
            insertLinks(
                blockLinks(block.id).map { link ->
                    val a = ItemRef(link.aType, link.aId)
                    val other = if (a == self) ItemRef(link.bType, link.bId) else a
                    LinkEntity.between(ItemRef(ItemType.BLOCK, newId), other)
                },
            )
            copyTags(block.id, newId)
            copyReminders(block.id, newId)
            newId
        }
    }

    @Query("DELETE FROM links WHERE (aType = 'BLOCK' AND aId IN (SELECT id FROM blocks WHERE scheduleId = :id)) OR (bType = 'BLOCK' AND bId IN (SELECT id FROM blocks WHERE scheduleId = :id))")
    suspend fun deleteBlockLinks(id: Long)

    @Query("DELETE FROM item_tags WHERE type = 'BLOCK' AND itemId IN (SELECT id FROM blocks WHERE scheduleId = :id)")
    suspend fun deleteBlockTags(id: Long)

    @Query("DELETE FROM blocks WHERE scheduleId = :id")
    suspend fun deleteBlocks(id: Long)

    @Query("DELETE FROM schedules WHERE id = :id")
    suspend fun deleteRow(id: Long)

    /** Deletes the schedule with its blocks and their links and tags; the reminders go with `removeOrphans`. */
    @Transaction
    suspend fun delete(id: Long) {
        deleteBlockLinks(id)
        deleteBlockTags(id)
        deleteBlocks(id)
        deleteRow(id)
    }
}
