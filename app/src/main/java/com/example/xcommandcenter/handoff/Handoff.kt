package com.example.xcommandcenter.handoff

import android.content.Intent

/**
 * The hand-off contract: actions another app (zNotifMonitor) starts with startActivityForResult to
 * ask for a habit to be added or marked. The user confirms on these screens, which do the writing.
 * The action strings are fixed, so a debug build answers them too; callers name the package.
 */
object Handoff {
    const val ACTION_ADD_HABIT = "com.xeeshan.manage_everything.action.ADD_HABIT"
    const val ACTION_MARK_HABIT = "com.xeeshan.manage_everything.action.MARK_HABIT"

    /** ADD_HABIT: String, required. */
    const val EXTRA_NAME = "name"

    /** ADD_HABIT: String, optional. */
    const val EXTRA_NOTES = "notes"

    /** ADD_HABIT: Long epoch day, optional (today). */
    const val EXTRA_START_DATE = "start_date"

    /** MARK_HABIT: Long, required; also ADD_HABIT's result. */
    const val EXTRA_HABIT_ID = "habit_id"

    /** MARK_HABIT: Long epoch day, required. */
    const val EXTRA_DATE = "date"

    /** A whole-number extra, whether sent as a Long or an Int (an easy slip for an epoch day). */
    fun Intent.longExtra(name: String): Long? {
        @Suppress("DEPRECATION")
        return (extras?.get(name) as? Number)?.toLong()
    }
}
