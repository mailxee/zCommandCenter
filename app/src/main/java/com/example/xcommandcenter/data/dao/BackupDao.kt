package com.example.xcommandcenter.data.dao

import com.example.xcommandcenter.data.entity.PersonProfileEntity
import com.example.xcommandcenter.data.entity.PersonItemEntity
import com.example.xcommandcenter.data.entity.PersonEntity
import com.example.xcommandcenter.data.entity.IdeaEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.xcommandcenter.data.backup.BackupData
import com.example.xcommandcenter.data.entity.BlockEntity
import com.example.xcommandcenter.data.entity.BookEntity
import com.example.xcommandcenter.data.entity.ChapterEntity
import com.example.xcommandcenter.data.entity.ChecklistItemEntity
import com.example.xcommandcenter.data.entity.GoalEntity
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.data.entity.HabitMarkEntity
import com.example.xcommandcenter.data.entity.ItemTagEntity
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.data.entity.NoteEntity
import com.example.xcommandcenter.data.entity.NoteListItemEntity
import com.example.xcommandcenter.data.entity.PlanEntity
import com.example.xcommandcenter.data.entity.QuoteEntity
import com.example.xcommandcenter.data.db.AppDatabase
import com.example.xcommandcenter.data.entity.ReminderEntity
import com.example.xcommandcenter.data.entity.ScheduleEntity
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
    @Query("SELECT * FROM plans ORDER BY id") suspend fun plans(): List<PlanEntity>
    @Query("SELECT * FROM notes ORDER BY id") suspend fun notes(): List<NoteEntity>
    @Query("SELECT * FROM note_list_items ORDER BY noteId, position") suspend fun noteListItems(): List<NoteListItemEntity>
    @Query("SELECT * FROM books ORDER BY id") suspend fun books(): List<BookEntity>
    @Query("SELECT * FROM chapters ORDER BY bookId, position") suspend fun chapters(): List<ChapterEntity>
    @Query("SELECT * FROM quotes ORDER BY id") suspend fun quotes(): List<QuoteEntity>
    @Query("SELECT * FROM links ORDER BY id") suspend fun links(): List<LinkEntity>
    @Query("SELECT * FROM tags ORDER BY name") suspend fun tags(): List<TagEntity>
    @Query("SELECT * FROM item_tags") suspend fun itemTags(): List<ItemTagEntity>
    @Query("SELECT * FROM reminders ORDER BY itemType, itemId, time") suspend fun reminders(): List<ReminderEntity>
    @Query("SELECT * FROM blocks ORDER BY id") suspend fun blocks(): List<BlockEntity>
    @Query("SELECT * FROM schedules ORDER BY sortOrder, id") suspend fun schedules(): List<ScheduleEntity>
    @Query("SELECT * FROM people ORDER BY id") suspend fun people(): List<PersonEntity>
    @Query("SELECT * FROM person_profiles ORDER BY personId, position") suspend fun personProfiles(): List<PersonProfileEntity>
    @Query("SELECT * FROM person_items ORDER BY personId, position") suspend fun personItems(): List<PersonItemEntity>
    @Query("SELECT * FROM ideas ORDER BY id") suspend fun ideas(): List<IdeaEntity>

    /** A consistent snapshot: all tables read inside one transaction. */
    @Transaction
    suspend fun snapshot(): BackupData = BackupData(
        tasks(), goals(), habits(), habitMarks(), plans(), notes(), books(), chapters(), quotes(), links(), tags(), itemTags(), checklistItems(), noteListItems(),
        reminders(), blocks(), schedules(), people(), personProfiles(), personItems(), ideas(),
    )

    @Insert suspend fun insertTasks(rows: List<TaskEntity>)
    @Insert suspend fun insertChecklistItems(rows: List<ChecklistItemEntity>)
    @Insert suspend fun insertGoals(rows: List<GoalEntity>)
    @Insert suspend fun insertHabits(rows: List<HabitEntity>)
    @Insert suspend fun insertHabitMarks(rows: List<HabitMarkEntity>)
    @Insert suspend fun insertPlans(rows: List<PlanEntity>)
    @Insert suspend fun insertNotes(rows: List<NoteEntity>)
    @Insert suspend fun insertNoteListItems(rows: List<NoteListItemEntity>)
    @Insert suspend fun insertBooks(rows: List<BookEntity>)
    @Insert suspend fun insertChapters(rows: List<ChapterEntity>)
    @Insert suspend fun insertQuotes(rows: List<QuoteEntity>)
    @Insert suspend fun insertLinks(rows: List<LinkEntity>)
    @Insert suspend fun insertTags(rows: List<TagEntity>)
    @Insert suspend fun insertItemTags(rows: List<ItemTagEntity>)
    @Insert suspend fun insertReminders(rows: List<ReminderEntity>)
    @Insert suspend fun insertBlocks(rows: List<BlockEntity>)
    @Insert suspend fun insertSchedules(rows: List<ScheduleEntity>)
    @Insert suspend fun insertPeople(rows: List<PersonEntity>)
    @Insert suspend fun insertPersonProfiles(rows: List<PersonProfileEntity>)
    @Insert suspend fun insertPersonItems(rows: List<PersonItemEntity>)
    @Insert suspend fun insertIdeas(rows: List<IdeaEntity>)

    // Children before parents, so foreign keys never point at a deleted row mid-way.
    @Query("DELETE FROM item_tags") suspend fun deleteItemTags()
    @Query("DELETE FROM reminders") suspend fun deleteReminders()
    @Query("DELETE FROM tags") suspend fun deleteTags()
    @Query("DELETE FROM links") suspend fun deleteLinks()
    @Query("DELETE FROM habit_marks") suspend fun deleteHabitMarks()
    @Query("DELETE FROM chapters") suspend fun deleteChapters()
    @Query("DELETE FROM checklist_items") suspend fun deleteChecklistItems()
    @Query("DELETE FROM note_list_items") suspend fun deleteNoteListItems()
    @Query("DELETE FROM tasks") suspend fun deleteTasks()
    @Query("DELETE FROM goals") suspend fun deleteGoals()
    @Query("DELETE FROM habits") suspend fun deleteHabits()
    @Query("DELETE FROM plans") suspend fun deletePlans()
    @Query("DELETE FROM notes") suspend fun deleteNotes()
    @Query("DELETE FROM books") suspend fun deleteBooks()
    @Query("DELETE FROM quotes") suspend fun deleteQuotes()
    @Query("DELETE FROM blocks") suspend fun deleteBlocks()
    @Query("DELETE FROM schedules") suspend fun deleteSchedules()
    @Query("DELETE FROM person_profiles") suspend fun deletePersonProfiles()
    @Query("DELETE FROM person_items") suspend fun deletePersonItems()
    @Query("DELETE FROM people") suspend fun deletePeople()
    @Query("DELETE FROM ideas") suspend fun deleteIdeas()
    @Query("DELETE FROM kept_findings") suspend fun deleteKeptFindings()

    @Transaction
    suspend fun deleteAll() {
        deleteItemTags()
        deleteReminders()
        deleteTags()
        deleteLinks()
        deleteHabitMarks()
        deleteChapters()
        deleteChecklistItems()
        deleteNoteListItems()
        deleteTasks()
        deleteGoals()
        deleteHabits()
        deletePlans()
        deleteNotes()
        deleteBooks()
        deleteQuotes()
        deleteBlocks()
        deleteSchedules()
        deletePersonProfiles()
        deletePersonItems()
        deletePeople()
        deleteIdeas()
        // Kept findings name links by id, which new data reuses for other links.
        deleteKeptFindings()
    }

    /** [deleteAll], leaving the one schedule there always is. */
    @Transaction
    suspend fun deleteEverything() {
        deleteAll()
        insertSchedules(listOf(ScheduleEntity(id = 1, name = AppDatabase.FIRST_SCHEDULE)))
    }

    /**
     * Makes [data] the whole database, all or nothing: if any insert fails, the transaction rolls
     * back and the old data stays. Parents go in before children; [data]'s plans are already
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
        insertPlans(data.plans)
        insertNotes(data.notes)
        insertNoteListItems(data.noteListItems)
        insertBooks(data.books)
        insertChapters(data.chapters)
        insertQuotes(data.quotes)
        insertSchedules(data.schedules.ifEmpty { listOf(ScheduleEntity(id = 1, name = AppDatabase.FIRST_SCHEDULE)) })
        insertBlocks(data.blocks)
        insertPeople(data.people)
        insertPersonProfiles(data.personProfiles)
        insertPersonItems(data.personItems)
        insertIdeas(data.ideas)
        insertLinks(data.links)
        insertTags(data.tags)
        insertItemTags(data.itemTags)
        insertReminders(data.reminders)
    }
}
