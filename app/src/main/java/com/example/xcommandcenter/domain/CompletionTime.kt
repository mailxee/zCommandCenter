package com.example.xcommandcenter.domain

/**
 * When a task counts as completed, for `tasks.completedAt`: the moment it is ticked, kept while it
 * stays ticked (saving its form again, or a notification's Done pressed twice, doesn't move it), and
 * forgotten when it is unticked. A task ticked before the column existed keeps null until it is
 * unticked and ticked again. A repeating task's next occurrence starts open, so with null.
 */
object CompletionTime {
    /** `completedAt` after `completed` goes from [wasCompleted] to [completed], given the stored [previous] and the time [now]. */
    fun after(wasCompleted: Boolean, completed: Boolean, previous: Long?, now: Long): Long? = when {
        !completed -> null
        wasCompleted -> previous
        else -> now
    }
}
