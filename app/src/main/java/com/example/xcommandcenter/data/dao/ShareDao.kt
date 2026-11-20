package com.example.xcommandcenter.data.dao

import android.database.Cursor
import androidx.room.Dao
import androidx.room.Query

/**
 * The tables other apps may read, whole and with Room's column names, as cursors for
 * [com.example.xcommandcenter.share.ShareProvider]. Notes, people, ideas, links and reminders are
 * deliberately not here.
 */
@Dao
interface ShareDao {
    @Query("SELECT * FROM tasks ORDER BY id") fun tasks(): Cursor
    @Query("SELECT * FROM habits ORDER BY id") fun habits(): Cursor
    @Query("SELECT * FROM habit_marks ORDER BY habitId, date") fun habitMarks(): Cursor
    @Query("SELECT * FROM plans ORDER BY id") fun plans(): Cursor
    @Query("SELECT * FROM schedules ORDER BY id") fun schedules(): Cursor
    @Query("SELECT * FROM blocks ORDER BY id") fun blocks(): Cursor
    @Query("SELECT * FROM tags ORDER BY id") fun tags(): Cursor
    /** Only the tags of shared items: a note's, a person's or an idea's tags are left out with the item. */
    @Query(
        "SELECT * FROM item_tags WHERE type IN ('TASK', 'HABIT', 'PLAN', 'BLOCK', 'QUOTE', 'BOOK', 'CHAPTER') ORDER BY type, itemId",
    )
    fun itemTags(): Cursor
    @Query("SELECT * FROM quotes ORDER BY id") fun quotes(): Cursor
    @Query("SELECT * FROM books ORDER BY id") fun books(): Cursor
    @Query("SELECT * FROM chapters ORDER BY id") fun chapters(): Cursor
}
