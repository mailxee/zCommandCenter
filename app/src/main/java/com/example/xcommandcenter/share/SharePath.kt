package com.example.xcommandcenter.share

import android.database.Cursor
import android.net.Uri
import com.example.xcommandcenter.data.dao.ShareDao

/** The paths [ShareProvider] answers, each reading one table through [ShareDao]; [META] is built by the provider. */
enum class SharePath(val path: String, val read: (ShareDao) -> Cursor) {
    TASKS("tasks", ShareDao::tasks),
    HABITS("habits", ShareDao::habits),
    HABIT_MARKS("habit_marks", ShareDao::habitMarks),
    GOALS("goals", ShareDao::goals),
    PLANS("plans", ShareDao::plans),
    SCHEDULES("schedules", ShareDao::schedules),
    BLOCKS("blocks", ShareDao::blocks),
    TAGS("tags", ShareDao::tags),
    ITEM_TAGS("item_tags", ShareDao::itemTags),
    QUOTES("quotes", ShareDao::quotes),
    BOOKS("books", ShareDao::books),
    CHAPTERS("chapters", ShareDao::chapters),
    META("meta", { error("built by the provider") }),
    ;

    companion object {
        /** The path of `content://<authority>/<path>`, ignoring a trailing slash; null for anything else. */
        fun of(uri: Uri): SharePath? = uri.pathSegments.singleOrNull()?.let { segment -> entries.firstOrNull { it.path == segment } }
    }
}
