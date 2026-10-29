package com.example.xcommandcenter.domain

/**
 * The Schedule tab's vertical axis when some free time is folded away: each minute of the day sits
 * [perMinute] units below the previous one, except that a folded gap shrinks to a bar [bar] units
 * tall. Units are whatever the caller measures in (dp on screen).
 */
class ScheduleAxis(folded: List<Pair<Int, Int>>, private val perMinute: Float, private val bar: Float) {
    private val folded = folded.sortedBy { it.first }

    /** Where [minute] sits; inside a folded gap, that is the top of its bar. */
    fun y(minute: Int): Float {
        var y = minute * perMinute
        for ((start, end) in folded) {
            when {
                minute >= end -> y -= (end - start) * perMinute - bar
                minute > start -> return y(start)
                else -> break
            }
        }
        return y
    }

    val height: Float get() = y(DAY)

    /** The minute at [y], or null when [y] falls on a folded gap's bar. */
    fun minuteAt(y: Float): Int? {
        var top = 0f
        var minute = 0
        for ((start, end) in folded) {
            val spanEnd = top + (start - minute) * perMinute
            if (y < spanEnd) return (minute + ((y - top) / perMinute).toInt()).coerceIn(0, DAY - 1)
            if (y < spanEnd + bar) return null
            top = spanEnd + bar
            minute = end
        }
        return (minute + ((y - top) / perMinute).toInt()).coerceIn(0, DAY - 1)
    }

    /** True when [minute] is hidden inside a folded gap (its edges stay visible). */
    fun hides(minute: Int): Boolean = folded.any { (start, end) -> minute > start && minute < end }

    companion object {
        const val DAY = 24 * 60

        /** Free time this long or longer can be folded. */
        const val MIN_GAP = 120

        /**
         * The stretches of the day with no block, at least [min] minutes long, before, between and
         * after the [busy] intervals (start, end in minutes of the day). None when nothing is busy.
         */
        fun gaps(busy: List<Pair<Int, Int>>, min: Int = MIN_GAP): List<Pair<Int, Int>> {
            if (busy.isEmpty()) return emptyList()
            val result = mutableListOf<Pair<Int, Int>>()
            var free = 0
            busy.sortedBy { it.first }.forEach { (start, end) ->
                if (start - free >= min) result += free to start
                free = maxOf(free, end)
            }
            if (DAY - free >= min) result += free to DAY
            return result
        }
    }
}

/** "2 h", "45 min", "1 h 15 min". */
fun durationText(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}
