package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.xcommandcenter.data.entity.PersonEntity
import com.example.xcommandcenter.data.entity.PersonItemEntity
import com.example.xcommandcenter.data.entity.PersonProfileEntity
import com.example.xcommandcenter.domain.ItemType
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao : ItemCleanup {
    @Query("SELECT * FROM people ORDER BY sortOrder, createdAt DESC")
    fun observeAll(): Flow<List<PersonEntity>>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun get(id: Long): PersonEntity?

    /** The sortOrder for a new item: ahead of every other one, as this tab lists newest first. */
    @Query("SELECT COALESCE(MIN(sortOrder) - 1, 0) FROM people")
    suspend fun newSortOrder(): Int

    @Query("UPDATE people SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    /** Stores [ids] as the new order, first to last. */
    @Transaction
    suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Insert
    suspend fun insert(person: PersonEntity): Long

    @Update
    suspend fun update(person: PersonEntity)

    @Query("DELETE FROM people WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("SELECT * FROM person_profiles WHERE personId = :personId ORDER BY position")
    suspend fun profiles(personId: Long): List<PersonProfileEntity>

    @Query("SELECT * FROM person_items WHERE personId = :personId ORDER BY position")
    suspend fun items(personId: Long): List<PersonItemEntity>

    /** Everyone's profiles, for the cards and the search. */
    @Query("SELECT * FROM person_profiles ORDER BY personId, position")
    fun observeAllProfiles(): Flow<List<PersonProfileEntity>>

    /** Everyone's items, for the cards and the search. */
    @Query("SELECT * FROM person_items ORDER BY personId, position")
    fun observeAllItems(): Flow<List<PersonItemEntity>>

    @Insert
    suspend fun insertProfiles(rows: List<PersonProfileEntity>)

    @Insert
    suspend fun insertItems(rows: List<PersonItemEntity>)

    @Query("DELETE FROM person_profiles WHERE personId = :personId")
    suspend fun deleteProfiles(personId: Long)

    @Query("DELETE FROM person_items WHERE personId = :personId")
    suspend fun deleteItems(personId: Long)

    /** Makes [profiles] and [items] the person's whole lists, numbered 0..n-1 in the given order. */
    @Transaction
    suspend fun replaceLists(personId: Long, profiles: List<String>, items: List<String>) {
        deleteProfiles(personId)
        deleteItems(personId)
        insertProfiles(profiles.mapIndexed { i, url -> PersonProfileEntity(personId = personId, position = i, url = url) })
        insertItems(items.mapIndexed { i, text -> PersonItemEntity(personId = personId, position = i, text = text) })
    }

    /** The profiles and items go with the person through the foreign keys' ON DELETE CASCADE. */
    @Transaction
    suspend fun delete(id: Long) {
        deleteRefs(ItemType.PERSON, id)
        deleteRow(id)
    }
}
