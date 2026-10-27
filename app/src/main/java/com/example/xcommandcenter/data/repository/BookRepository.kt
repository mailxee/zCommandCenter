package com.example.xcommandcenter.data.repository

import com.example.xcommandcenter.data.dao.BookDao
import com.example.xcommandcenter.data.entity.BookEntity
import com.example.xcommandcenter.data.entity.BookWithProgress
import com.example.xcommandcenter.data.entity.ChapterEntity
import kotlinx.coroutines.flow.Flow

/** Books and their table of contents. */
class BookRepository(private val dao: BookDao) {
    fun observeAllWithProgress(): Flow<List<BookWithProgress>> = dao.observeAllWithProgress()

    fun observe(id: Long): Flow<BookEntity?> = dao.observe(id)

    suspend fun get(id: Long): BookEntity? = dao.get(id)

    /** A new book gets a place in the list; an edited one keeps its place. */
    suspend fun save(book: BookEntity): Long =
        if (book.id == 0L) {
            dao.insert(book.copy(sortOrder = dao.newSortOrder()))
        } else {
            book.id.also { dao.update(book.copy(sortOrder = dao.get(book.id)?.sortOrder ?: book.sortOrder)) }
        }

    suspend fun reorder(ids: List<Long>) = dao.reorder(ids)

    suspend fun delete(id: Long) = dao.delete(id)

    fun observeChapters(bookId: Long): Flow<List<ChapterEntity>> = dao.observeChapters(bookId)

    fun observeChapter(id: Long): Flow<ChapterEntity?> = dao.observeChapter(id)

    suspend fun addChapter(bookId: Long, title: String): Long = dao.addChapter(bookId, title)

    suspend fun renameChapter(chapter: ChapterEntity, title: String) = dao.updateChapter(chapter.copy(title = title))

    suspend fun addReadCount(chapterId: Long, delta: Int) = dao.addReadCount(chapterId, delta)

    suspend fun setInternalized(chapterId: Long, internalized: Boolean) = dao.setInternalized(chapterId, internalized)

    suspend fun moveChapter(chapterId: Long, offset: Int) = dao.moveChapter(chapterId, offset)

    suspend fun deleteChapter(chapterId: Long) = dao.deleteChapter(chapterId)
}
