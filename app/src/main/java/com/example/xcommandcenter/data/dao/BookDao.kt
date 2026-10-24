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
        FROM books b ORDER BY b.title COLLATE NOCASE
        """,
    )
    fun observeAllWithProgress(): Flow<List<BookWithProgress>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun get(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE id = :id")
    fun observe(id: Long): Flow<BookEntity?>

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

    /** Chapters go with the book through ON DELETE CASCADE; their links are removed first. */
    @Transaction
    suspend fun delete(id: Long) {
        deleteChapterLinksOfBook(id)
        deleteLinks(id)
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

    @Query("UPDATE chapters SET internalized = :internalized WHERE id = :id")
    suspend fun setInternalized(id: Long, internalized: Boolean)

    @Query("DELETE FROM chapters WHERE id = :id")
    suspend fun deleteChapterRow(id: Long)

    @Query("DELETE FROM links WHERE (aType = 'CHAPTER' AND aId = :id) OR (bType = 'CHAPTER' AND bId = :id)")
    suspend fun deleteChapterLinks(id: Long)

    @Transaction
    suspend fun addChapter(bookId: Long, title: String): Long =
        insertChapter(ChapterEntity(bookId = bookId, position = nextChapterPosition(bookId), title = title))

    @Transaction
    suspend fun deleteChapter(id: Long) {
        val chapter = getChapter(id) ?: return
        deleteChapterLinks(id)
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
