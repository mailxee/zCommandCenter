package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.data.entity.HabitMarkEntity
import com.example.xcommandcenter.domain.ItemType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface HabitDao : ItemCleanup {
    @Query("SELECT * FROM habits ORDER BY sortOrder, createdAt")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun get(id: Long): HabitEntity?

    @Query("SELECT date FROM habit_marks WHERE habitId = :habitId")
    suspend fun markDates(habitId: Long): List<LocalDate>

    /** The sortOrder for a new item: after every other one. */
    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM habits")
    suspend fun newSortOrder(): Int

    @Query("UPDATE habits SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    /** Stores [ids] as the new order, first to last. */
    @Transaction
    suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Insert
    suspend fun insert(habit: HabitEntity): Long

    @Update
    suspend fun update(habit: HabitEntity)

    @Query("SELECT * FROM habit_marks")
    fun observeAllMarks(): Flow<List<HabitMarkEntity>>

    @Query("SELECT * FROM habit_marks WHERE habitId = :habitId")
    fun observeMarks(habitId: Long): Flow<List<HabitMarkEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMark(mark: HabitMarkEntity)

    @Query("DELETE FROM habit_marks WHERE habitId = :habitId AND date = :date")
    suspend fun deleteMark(habitId: Long, date: LocalDate)

    @Query("SELECT COUNT(*) FROM habit_marks WHERE habitId = :habitId AND date = :date")
    suspend fun markCount(habitId: Long, date: LocalDate): Int

    /** Marks [date] if it isn't already; for a notification's button, which may be pressed twice. */
    @Transaction
    suspend fun setMark(habitId: Long, date: LocalDate) {
        if (markCount(habitId, date) == 0) insertMark(HabitMarkEntity(habitId, date))
    }

    @Transaction
    suspend fun toggleMark(habitId: Long, date: LocalDate) {
        if (markCount(habitId, date) > 0) deleteMark(habitId, date) else insertMark(HabitMarkEntity(habitId, date))
    }

    /** Marks go with the habit through the foreign key's ON DELETE CASCADE. */
    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Transaction
    suspend fun delete(id: Long) {
        deleteRefs(ItemType.HABIT, id)
        deleteRow(id)
    }
}
