package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.xcommandcenter.data.backup.BackupData
import com.example.xcommandcenter.data.entity.BookEntity
import com.example.xcommandcenter.data.entity.ChapterEntity
import com.example.xcommandcenter.data.entity.ChecklistItemEntity
import com.example.xcommandcenter.data.entity.GoalEntity
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.data.entity.HabitMarkEntity
import com.example.xcommandcenter.data.entity.ItemTagEntity
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.data.entity.NoteEntity
import com.example.xcommandcenter.data.entity.ProjectEntity
import com.example.xcommandcenter.data.entity.QuoteEntity
import com.example.xcommandcenter.data.entity.TagEntity
import com.example.xcommandcenter.data.entity.TaskEntity

/** Whole-database reads and writes for export, import and "Delete everything". */
@Dao
interface BackupDao {
    @Query("SELECT * FROM tasks ORDER BY id") suspend fun tasks(): List<TaskEntity>
    @Query("SELECT * FROM checklist_items ORDER BY taskId, position") suspend fun checklistItems(): List<ChecklistItemEntity>
    @Query("SELECT * FROM goals ORDER BY id") suspend fun goals(): List<GoalEntity>
    @Query("SELECT * FROM habits ORDER BY id") suspend fun habits(): List<HabitEntity>
    @Query("SELECT * FROM habit_marks") suspend fun habitMarks(): List<HabitMarkEntity>
    @Query("SELECT * FROM projects ORDER BY id") suspend fun projects(): List<ProjectEntity>
    @Query("SELECT * FROM notes ORDER BY id") suspend fun notes(): List<NoteEntity>
    @Query("SELECT * FROM books ORDER BY id") suspend fun books(): List<BookEntity>
    @Query("SELECT * FROM chapters ORDER BY bookId, position") suspend fun chapters(): List<ChapterEntity>
    @Query("SELECT * FROM quotes ORDER BY id") suspend fun quotes(): List<QuoteEntity>
    @Query("SELECT * FROM links ORDER BY id") suspend fun links(): List<LinkEntity>
    @Query("SELECT * FROM tags ORDER BY name") suspend fun tags(): List<TagEntity>
    @Query("SELECT * FROM item_tags") suspend fun itemTags(): List<ItemTagEntity>

    /** A consistent snapshot: all tables read inside one transaction. */
    @Transaction
    suspend fun snapshot(): BackupData = BackupData(
        tasks(), goals(), habits(), habitMarks(), projects(), notes(), books(), chapters(), quotes(), links(), tags(), itemTags(), checklistItems(),
    )

    @Insert suspend fun insertTasks(rows: List<TaskEntity>)
    @Insert suspend fun insertChecklistItems(rows: List<ChecklistItemEntity>)
    @Insert suspend fun insertGoals(rows: List<GoalEntity>)
    @Insert suspend fun insertHabits(rows: List<HabitEntity>)
    @Insert suspend fun insertHabitMarks(rows: List<HabitMarkEntity>)
    @Insert suspend fun insertProjects(rows: List<ProjectEntity>)
    @Insert suspend fun insertNotes(rows: List<NoteEntity>)
    @Insert suspend fun insertBooks(rows: List<BookEntity>)
    @Insert suspend fun insertChapters(rows: List<ChapterEntity>)
    @Insert suspend fun insertQuotes(rows: List<QuoteEntity>)
    @Insert suspend fun insertLinks(rows: List<LinkEntity>)
    @Insert suspend fun insertTags(rows: List<TagEntity>)
    @Insert suspend fun insertItemTags(rows: List<ItemTagEntity>)

    // Children before parents, so foreign keys never point at a deleted row mid-way.
    @Query("DELETE FROM item_tags") suspend fun deleteItemTags()
    @Query("DELETE FROM tags") suspend fun deleteTags()
    @Query("DELETE FROM links") suspend fun deleteLinks()
    @Query("DELETE FROM habit_marks") suspend fun deleteHabitMarks()
    @Query("DELETE FROM chapters") suspend fun deleteChapters()
    @Query("DELETE FROM checklist_items") suspend fun deleteChecklistItems()
    @Query("DELETE FROM tasks") suspend fun deleteTasks()
    @Query("DELETE FROM goals") suspend fun deleteGoals()
    @Query("DELETE FROM habits") suspend fun deleteHabits()
    @Query("DELETE FROM projects") suspend fun deleteProjects()
    @Query("DELETE FROM notes") suspend fun deleteNotes()
    @Query("DELETE FROM books") suspend fun deleteBooks()
    @Query("DELETE FROM quotes") suspend fun deleteQuotes()

    @Transaction
    suspend fun deleteAll() {
        deleteItemTags()
        deleteTags()
        deleteLinks()
        deleteHabitMarks()
        deleteChapters()
        deleteChecklistItems()
        deleteTasks()
        deleteGoals()
        deleteHabits()
        deleteProjects()
        deleteNotes()
        deleteBooks()
        deleteQuotes()
    }

    /**
     * Makes [data] the whole database, all or nothing: if any insert fails, the transaction rolls
     * back and the old data stays. Parents go in before children; [data]'s projects are already
     * ordered parents-first.
     */
    @Transaction
    suspend fun replaceAll(data: BackupData) {
        deleteAll()
        insertTasks(data.tasks)
        insertChecklistItems(data.checklistItems)
        insertGoals(data.goals)
        insertHabits(data.habits)
        insertHabitMarks(data.habitMarks)
        insertProjects(data.projects)
        insertNotes(data.notes)
        insertBooks(data.books)
        insertChapters(data.chapters)
        insertQuotes(data.quotes)
        insertLinks(data.links)
        insertTags(data.tags)
        insertItemTags(data.itemTags)
    }
}
