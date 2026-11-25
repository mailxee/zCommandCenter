package com.example.xcommandcenter.data.dao

import com.example.xcommandcenter.domain.LinkLabels
import com.example.xcommandcenter.data.entity.PersonProfileEntity
import com.example.xcommandcenter.data.entity.PersonItemEntity
import com.example.xcommandcenter.data.entity.PersonEntity
import com.example.xcommandcenter.data.entity.IdeaEntity
import com.example.xcommandcenter.data.entity.GlossaryEntity
import com.example.xcommandcenter.data.entity.LinkLabelEntity
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.xcommandcenter.data.backup.BackupData
import com.example.xcommandcenter.data.backup.IdeaNetwork
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.PlanTree
import com.example.xcommandcenter.data.entity.BlockEntity
import com.example.xcommandcenter.data.entity.BookEntity
import com.example.xcommandcenter.data.entity.ChapterEntity
import com.example.xcommandcenter.data.entity.ChecklistItemEntity
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.data.entity.HabitMarkEntity
import com.example.xcommandcenter.data.entity.ItemTagEntity
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.data.entity.NoteEntity
import com.example.xcommandcenter.data.entity.NoteListItemEntity
import com.example.xcommandcenter.data.entity.IdeaListItemEntity
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
    @Query("SELECT * FROM idea_list_items ORDER BY ideaId, position") suspend fun ideaListItems(): List<IdeaListItemEntity>
    @Insert suspend fun insertIdeaListItems(rows: List<IdeaListItemEntity>)
    @Query("DELETE FROM idea_list_items") suspend fun deleteIdeaListItems()
    @Query("SELECT * FROM link_labels ORDER BY sortOrder, id") suspend fun linkLabels(): List<LinkLabelEntity>
    @Query("SELECT * FROM glossary ORDER BY `key`") suspend fun glossary(): List<GlossaryEntity>
    @Insert suspend fun insertGlossary(rows: List<GlossaryEntity>)
    @Query("DELETE FROM glossary") suspend fun deleteGlossary()

    /** A consistent snapshot: all tables read inside one transaction. */
    @Transaction
    suspend fun snapshot(): BackupData = BackupData(
        tasks(), habits(), habitMarks(), plans(), notes(), books(), chapters(), quotes(), links(), tags(), itemTags(), checklistItems(), noteListItems(),
        reminders(), blocks(), schedules(), people(), personProfiles(), personItems(), ideas(), linkLabels(), glossary(), ideaListItems(),
    )

    @Insert suspend fun insertTasks(rows: List<TaskEntity>)
    @Insert suspend fun insertChecklistItems(rows: List<ChecklistItemEntity>)
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
    @Insert suspend fun insertLinkLabels(rows: List<LinkLabelEntity>)
    @Query("DELETE FROM link_labels") suspend fun deleteLinkLabels()

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
        deleteIdeaListItems()
        deleteIdeas()
        // Kept findings name links by id, which new data reuses for other links.
        deleteKeptFindings()
    }

    /** [deleteAll], leaving the one schedule there always is and the starting labels. */
    @Transaction
    suspend fun deleteEverything() {
        deleteAll()
        // The glossary and the labels are settings, which "Delete everything" resets too.
        deleteGlossary()
        deleteLinkLabels()
        insertLinkLabels(LinkLabels.defaults.mapIndexed { i, l -> LinkLabelEntity(key = l.key, forward = l.forward, backward = l.backward, sortOrder = i) })
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
        insertIdeaListItems(data.ideaListItems)
        // Labels are kept like settings unless the data brings its own (an older workbook has none).
        if (data.linkLabels.isNotEmpty()) {
            deleteLinkLabels()
            insertLinkLabels(data.linkLabels)
        }
        // Definitions are kept like settings too, unless the data brings its own.
        if (data.glossary.isNotEmpty()) {
            deleteGlossary()
            insertGlossary(data.glossary)
        }
        val labelIds = linkLabels().map { it.id }.toSet()
        insertLinks(data.links.map { if (it.labelId != null && it.labelId !in labelIds) it.copy(labelId = null, labelFromA = true) else it })
        insertTags(data.tags)
        insertItemTags(data.itemTags)
        insertReminders(data.reminders)
    }

    @Insert suspend fun insertIdea(row: IdeaEntity): Long
    @Insert suspend fun insertLinkLabel(row: LinkLabelEntity): Long
    @Insert suspend fun insertTag(row: TagEntity): Long
    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM ideas WHERE parentId IS :parentId") suspend fun nextIdeaOrder(parentId: Long?): Int
    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM link_labels") suspend fun nextLabelOrder(): Int

    /**
     * Adds a network of ideas (see [IdeaNetwork]) as copies with new ids, all or nothing, and
     * changes nothing already here. Its top-level ideas go under [parentId] (null: top level)
     * after the ideas already there, in their own order; the rest keep their places. Its labels
     * are matched to these by [IdeaNetwork.sameLabel] and added when new; tags are matched by name.
     */
    @Transaction
    suspend fun addIdeas(network: BackupData, parentId: Long?): IdeaNetwork.Added {
        val rows = PlanTree.flatten(network.ideas, { it.id }, { it.parentId }).map { it.node }
        val newId = HashMap<Long, Long>()
        var nextTop = nextIdeaOrder(parentId)
        val tops = rows.filter { it.parentId == null }.sortedBy { it.sortOrder }.map { it.id }
        val topOrder = tops.withIndex().associate { (i, id) -> id to nextTop + i }
        nextTop += tops.size
        rows.forEach { idea ->
            val parent = idea.parentId?.let(newId::get) ?: parentId
            newId[idea.id] = insertIdea(idea.copy(id = 0, parentId = parent, sortOrder = topOrder[idea.id] ?: idea.sortOrder))
        }
        insertIdeaListItems(network.ideaListItems.mapNotNull { item -> newId[item.ideaId]?.let { item.copy(id = 0, ideaId = it) } })

        val labels = linkLabels().toMutableList()
        var newLabels = 0
        var labelOrder = nextLabelOrder()
        val labelId = network.linkLabels.associate { label ->
            val same = IdeaNetwork.sameLabel(label, labels)
            label.id to (same ?: label.copy(id = 0, sortOrder = labelOrder++).let { it.copy(id = insertLinkLabel(it)) }.also { labels += it; newLabels++ }).id
        }
        val links = network.links
            .filter { it.aType == ItemType.IDEA && it.bType == ItemType.IDEA && it.aId in newId && it.bId in newId }
            .mapNotNull { link ->
                val label = link.labelId?.let(labelId::get)
                val relabelled = link.copy(labelId = label, labelFromA = label == null || link.labelFromA)
                LinkEntity.remap(relabelled) { ItemRef(ItemType.IDEA, newId.getValue(it.id)) }?.copy(id = 0)
            }
        insertLinks(links)

        val tagIds = tags().associate { it.name.lowercase() to it.id }.toMutableMap()
        val names = network.tags.associate { it.id to it.name }
        insertItemTags(
            network.itemTags.mapNotNull { t ->
                val idea = newId[t.itemId] ?: return@mapNotNull null
                val name = names[t.tagId] ?: return@mapNotNull null
                val tag = tagIds.getOrPut(name.lowercase()) { insertTag(TagEntity(name = name)) }
                ItemTagEntity(ItemType.IDEA, idea, tag)
            }.distinct(),
        )
        return IdeaNetwork.Added(ideas = rows.size, links = links.size, newLabels = newLabels)
    }
}
