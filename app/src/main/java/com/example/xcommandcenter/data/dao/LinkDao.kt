package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.xcommandcenter.data.entity.ChapterBook
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.KeptFindingEntity
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.data.entity.LinkLabelEntity
import com.example.xcommandcenter.data.entity.LabelUse
import com.example.xcommandcenter.data.entity.OwnedLink
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.ThinkAnalysis
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {
    /** Every item, for the map's labels. */
    @Query("SELECT * FROM item_index")
    fun observeIndex(): Flow<List<ItemIndexEntry>>

    @Query("SELECT `key` FROM kept_findings")
    fun observeKept(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun keep(finding: KeptFindingEntity)

    @Query("DELETE FROM kept_findings WHERE `key` = :key")
    suspend fun unkeep(key: String)

    /** Linking an already-linked pair is a no-op thanks to the unique index. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(link: LinkEntity): Long

    @Query("DELETE FROM links WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM links WHERE aType = :aType AND aId = :aId AND bType = :bType AND bId = :bId")
    suspend fun delete(aType: ItemType, aId: Long, bType: ItemType, bId: Long)

    /** Items linked to the given item, whichever side of the link it is stored on. */
    @Query(
        """
        SELECT i.type, i.id, i.title, i.subtitle FROM links l
        JOIN item_index i ON
            (l.aType = :type AND l.aId = :id AND i.type = l.bType AND i.id = l.bId)
            OR (l.bType = :type AND l.bId = :id AND i.type = l.aType AND i.id = l.aId)
        ORDER BY i.title COLLATE NOCASE
        """,
    )
    fun observeLinked(type: ItemType, id: Long): Flow<List<ItemIndexEntry>>

    /** Every link seen from both ends, so each list row can show what it is linked to. */
    @Query(
        """
        SELECT l.aType AS ownerType, l.aId AS ownerId, i.type, i.id, i.title, i.subtitle
        FROM links l JOIN item_index i ON i.type = l.bType AND i.id = l.bId
        UNION ALL
        SELECT l.bType, l.bId, i.type, i.id, i.title, i.subtitle
        FROM links l JOIN item_index i ON i.type = l.aType AND i.id = l.aId
        """,
    )
    fun observeAllOwned(): Flow<List<OwnedLink>>

    /** The links of every chapter, owned by the chapter's book (ownerType is BOOK, ownerId the book's id). */
    @Query(
        """
        SELECT 'BOOK' AS ownerType, c.bookId AS ownerId, i.type, i.id, i.title, i.subtitle
        FROM links l JOIN chapters c ON l.aType = 'CHAPTER' AND l.aId = c.id
        JOIN item_index i ON i.type = l.bType AND i.id = l.bId
        UNION ALL
        SELECT 'BOOK', c.bookId, i.type, i.id, i.title, i.subtitle
        FROM links l JOIN chapters c ON l.bType = 'CHAPTER' AND l.bId = c.id
        JOIN item_index i ON i.type = l.aType AND i.id = l.aId
        """,
    )
    fun observeChapterLinksByBook(): Flow<List<OwnedLink>>

    /** Each chapter's book, as an index entry, keyed by the chapter's id. */
    @Query("SELECT c.id AS chapterId, b.id, b.title, NULLIF(b.author, '') AS subtitle FROM chapters c JOIN books b ON b.id = c.bookId")
    fun observeChapterBooks(): Flow<List<ChapterBook>>

    /** A book's chapters, in the book's order, as index entries. */
    @Query(
        """
        SELECT 'CHAPTER' AS type, c.id, c.title, b.title AS subtitle FROM chapters c JOIN books b ON b.id = c.bookId
        WHERE c.bookId = :bookId ORDER BY c.position
        """,
    )
    fun observeChapters(bookId: Long): Flow<List<ItemIndexEntry>>

    @Query("SELECT * FROM links")
    fun observeAll(): Flow<List<LinkEntity>>

    /** Every idea's parent and kind: the mind map's tree and Think mode's checks. */
    @Query("SELECT id, parentId, kind FROM ideas")
    fun observeIdeas(): Flow<List<ThinkAnalysis.Idea>>

    /** One item's links, as stored, for their labels. */
    @Query("SELECT * FROM links WHERE (aType = :type AND aId = :id) OR (bType = :type AND bId = :id)")
    fun observeLinksOf(type: ItemType, id: Long): Flow<List<LinkEntity>>

    @Query("UPDATE links SET labelId = :labelId, labelFromA = :fromA WHERE aType = :aType AND aId = :aId AND bType = :bType AND bId = :bId")
    suspend fun setLabel(aType: ItemType, aId: Long, bType: ItemType, bId: Long, labelId: Long?, fromA: Boolean)

    @Query("SELECT * FROM link_labels ORDER BY sortOrder, id")
    fun observeLabels(): Flow<List<LinkLabelEntity>>

    @Query("SELECT labelId AS id, COUNT(*) AS uses FROM links WHERE labelId IS NOT NULL GROUP BY labelId")
    fun observeLabelUses(): Flow<List<LabelUse>>

    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM link_labels")
    suspend fun newLabelOrder(): Int

    @Insert
    suspend fun insertLabel(label: LinkLabelEntity): Long

    @Query("UPDATE link_labels SET forward = :forward, backward = :backward WHERE id = :id")
    suspend fun renameLabel(id: Long, forward: String, backward: String?)

    /** Its links lose the label through the foreign key (SET NULL). */
    @Query("DELETE FROM link_labels WHERE id = :id")
    suspend fun deleteLabel(id: Long)

    /** One item's index entry; emits null once the item is deleted. */
    @Query("SELECT * FROM item_index WHERE type = :type AND id = :id")
    fun observeEntry(type: ItemType, id: Long): Flow<ItemIndexEntry?>

    @Query(
        """
        SELECT * FROM item_index
        WHERE type IN (:typeNames) AND title LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE
        """,
    )
    fun search(typeNames: List<String>, query: String): Flow<List<ItemIndexEntry>>

    /**
     * An item's explanation for the map's panel: a task's or plan's description, a goal's
     * strategy, a habit's or block's notes, a note's text, a book's description, a quote's author
     * and source, a person's bio, an idea's explanation. Null or blank when there is none.
     */
    @Query(
        """
        SELECT text FROM (
            SELECT description AS text FROM tasks WHERE :typeName = 'TASK' AND id = :id
            UNION ALL SELECT strategy FROM goals WHERE :typeName = 'GOAL' AND id = :id
            UNION ALL SELECT notes FROM habits WHERE :typeName = 'HABIT' AND id = :id
            UNION ALL SELECT description FROM plans WHERE :typeName = 'PLAN' AND id = :id
            UNION ALL SELECT body FROM notes WHERE :typeName = 'NOTE' AND id = :id
            UNION ALL SELECT description FROM books WHERE :typeName = 'BOOK' AND id = :id
            UNION ALL SELECT TRIM(author || CASE WHEN TRIM(author) <> '' AND TRIM(source) <> '' THEN ', ' ELSE '' END || source)
                FROM quotes WHERE :typeName = 'QUOTE' AND id = :id
            UNION ALL SELECT notes FROM blocks WHERE :typeName = 'BLOCK' AND id = :id
            UNION ALL SELECT bio FROM people WHERE :typeName = 'PERSON' AND id = :id
            UNION ALL SELECT body FROM ideas WHERE :typeName = 'IDEA' AND id = :id
        ) LIMIT 1
        """,
    )
    fun observeExplanation(typeName: String, id: Long): Flow<String?>

    /** An item's list in order: a task's checklist, a note's or an idea's list, a person's items. */
    @Query(
        """
        SELECT text FROM (
            SELECT text, position FROM checklist_items WHERE :typeName = 'TASK' AND taskId = :id
            UNION ALL SELECT text, position FROM note_list_items WHERE :typeName = 'NOTE' AND noteId = :id
            UNION ALL SELECT text, position FROM idea_list_items WHERE :typeName = 'IDEA' AND ideaId = :id
            UNION ALL SELECT text, position FROM person_items WHERE :typeName = 'PERSON' AND personId = :id
        ) ORDER BY position
        """,
    )
    fun observeList(typeName: String, id: Long): Flow<List<String>>
}
