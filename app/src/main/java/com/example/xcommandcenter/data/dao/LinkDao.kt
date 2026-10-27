package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.xcommandcenter.data.entity.ChapterBook
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.LinkEntity
import com.example.xcommandcenter.data.entity.OwnedLink
import com.example.xcommandcenter.domain.ItemType
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {
    /** Linking an already-linked pair is a no-op thanks to the unique index. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(link: LinkEntity): Long

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

    @Query(
        """
        SELECT * FROM item_index
        WHERE type IN (:typeNames) AND title LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE
        """,
    )
    fun search(typeNames: List<String>, query: String): Flow<List<ItemIndexEntry>>
}
