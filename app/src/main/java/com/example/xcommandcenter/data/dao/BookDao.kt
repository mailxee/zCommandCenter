package com.example.xcommandcenter.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.xcommandcenter.data.entity.BookEntity
import com.example.xcommandcenter.data.entity.BookWithProgress
import com.example.xcommandcenter.data.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

/** Books and their chapters; chapters never exist without their book. */
@Dao
interface BookDao {
    @Query(
        """
        SELECT b.*,
            (SELECT COUNT(*) FROM chapters c WHERE c.bookId = b.id) AS chapterCount,
            (SELECT COUNT(*) FROM chapters c WHERE c.bookId = b.id AND c.internalized) AS internalizedCount
        FROM books b ORDER BY b.sortOrder, b.title COLLATE NOCASE
        """,
    )
    fun observeAllWithProgress(): Flow<List<BookWithProgress>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: Long): Flow<BookEntity?>

    /** The sortOrder for a new item: after every other one. */
    @Query("SELECT COALESCE(MAX(sortOrder) + 1, 0) FROM books")
    suspend fun newSortOrder(): Int

    @Query("UPDATE books SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    /** Stores [ids] as the new order, first to last. */
    @Transaction
    suspend fun reorder(ids: List<Long>) {
        ids.forEachIndexed { index, id -> setSortOrder(id, index) }
    }

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteRow(id: Long)

    @Query("DELETE FROM links WHERE (aType = 'BOOK' AND aId = :id) OR (bType = 'BOOK' AND bId = :id)")
    suspend fun deleteLinks(id: Long)

    @Query(
        """
        DELETE FROM links WHERE
            (aType = 'CHAPTER' AND aId IN (SELECT id FROM chapters WHERE bookId = :bookId))
            OR (bType = 'CHAPTER' AND bId IN (SELECT id FROM chapters WHERE bookId = :bookId))
        """,
    )
    suspend fun deleteChapterLinksOfBook(bookId: Long)

    @Query("DELETE FROM item_tags WHERE type = 'BOOK' AND itemId = :id")
    suspend fun deleteTags(id: Long)

    @Query("DELETE FROM item_tags WHERE type = 'CHAPTER' AND itemId IN (SELECT id FROM chapters WHERE bookId = :bookId)")
    suspend fun deleteChapterTagsOfBook(bookId: Long)

    /** Chapters go with the book through ON DELETE CASCADE; their links and tags are removed first. */
    @Transaction
    suspend fun delete(id: Long) {
        deleteChapterLinksOfBook(id)
        deleteChapterTagsOfBook(id)
        deleteLinks(id)
        deleteTags(id)
        deleteRow(id)
    }

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY position")
    fun observeChapters(bookId: Long): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY position")
    suspend fun chapters(bookId: Long): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE id = :id")
    fun observeChapter(id: Long): Flow<ChapterEntity?>

    @Query("SELECT * FROM chapters WHERE id = :id")
    suspend fun getChapter(id: Long): ChapterEntity?

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM chapters WHERE bookId = :bookId")
    suspend fun nextChapterPosition(bookId: Long): Int

    @Insert
    suspend fun insertChapter(chapter: ChapterEntity): Long

    @Update
    suspend fun updateChapter(chapter: ChapterEntity)

    @Update
    suspend fun updateChapters(chapters: List<ChapterEntity>)

    @Query("UPDATE chapters SET readCount = MAX(readCount + :delta, 0) WHERE id = :id")
    suspend fun addReadCount(id: Long, delta: Int)

    @Query("UPDATE chapters SET readCount = 0 WHERE id = :id")
    suspend fun clearReadCount(id: Long)

    @Query("UPDATE books SET readCount = MAX(readCount + :delta, 0) WHERE id = :id")
    suspend fun addBookReadCount(id: Long, delta: Int)

    @Query("UPDATE books SET readCount = 0 WHERE id = :id")
    suspend fun clearBookReadCount(id: Long)

    @Query("UPDATE books SET internalized = :internalized WHERE id = :id")
    suspend fun setBookInternalized(id: Long, internalized: Boolean)

    @Query("UPDATE chapters SET internalized = :internalized WHERE id = :id")
    suspend fun setInternalized(id: Long, internalized: Boolean)

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun deleteChapterRow(id: Long)

    @Query("DELETE FROM links WHERE (aType = 'CHAPTER' AND aId = :id) OR (bType = 'CHAPTER' AND bId = :id)")
    suspend fun deleteChapterLinks(id: Long)

    @Query("DELETE FROM item_tags WHERE type = 'CHAPTER' AND itemId = :id")
    suspend fun deleteChapterTags(id: Long)

    @Transaction
    suspend fun addChapter(bookId: Long, title: String): Long =
        insertChapter(ChapterEntity(bookId = bookId, position = nextChapterPosition(bookId), title = title))

    @Transaction
    suspend fun deleteChapter(id: Long) {
        val chapter = getChapter(id) ?: return
        deleteChapterLinks(id)
        deleteChapterTags(id)
        deleteChapterRow(id)
        renumber(chapters(chapter.bookId))
    }

    /** Moves a chapter one place up or down and renumbers the book's chapters 0..n-1. */
    @Transaction
    suspend fun moveChapter(id: Long, offset: Int) {
        val chapter = getChapter(id) ?: return
        val ordered = chapters(chapter.bookId).toMutableList()
        val from = ordered.indexOfFirst { it.id == id }
        val to = from + offset
        if (to !in ordered.indices) return
        ordered.add(to, ordered.removeAt(from))
        renumber(ordered)
    }

    suspend fun renumber(ordered: List<ChapterEntity>) {
        updateChapters(ordered.mapIndexedNotNull { index, c -> if (c.position != index) c.copy(position = index) else null })
    }
}
