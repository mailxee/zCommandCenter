package com.example.xcommandcenter.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.xcommandcenter.R
import com.example.xcommandcenter.data.entity.HabitEntity
import com.example.xcommandcenter.domain.HabitCategory
import com.example.xcommandcenter.domain.HabitStats
import com.example.xcommandcenter.domain.ItemType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** How a calendar day shows: [PENDING] is today on a positive habit, not marked yet. */
enum class DayStatus { DONE, MISSED, PENDING, NONE }

/**
 * The same rule for every habit: a due day, from the start through today, is done or missed. A
 * positive habit stores its done days, so an unmarked past day is missed (today stays pending);
 * a negative habit stores its missed days, so an unmarked day is done. Any other day is [NONE],
 * unless it carries a mark from an earlier schedule.
 */
fun dayStatus(date: LocalDate, marked: Boolean, due: Boolean, positive: Boolean, start: LocalDate, today: LocalDate): DayStatus = when {
    marked -> if (positive) DayStatus.DONE else DayStatus.MISSED
    !due || date < start || date > today -> DayStatus.NONE
    !positive -> DayStatus.DONE
    date == today -> DayStatus.PENDING
    else -> DayStatus.MISSED
}

/**
 * A habit's done and missed days, a month at a time: swipe between months, from the habit's first
 * month (its start, or an earlier mark) to the current one, which it opens on. Done days are
 * filled in the Habits tab's color, missed ones outlined in red; today is outlined. With
 * [onToggle], tapping a due day from the start through today switches it between done and missed.
 */
@Composable
fun HabitCalendar(habit: HabitEntity, marks: Set<LocalDate>, onToggle: ((LocalDate) -> Unit)? = null, today: LocalDate = LocalDate.now()) {
    val current = YearMonth.from(today)
    val first = listOfNotNull(YearMonth.from(habit.startDate), marks.minOrNull()?.let(YearMonth::from), current).min()
    val count = ChronoUnit.MONTHS.between(first, current).toInt() + 1
    val pager = rememberPagerState(initialPage = count - 1) { count }
    val due = remember(habit, marks, today) {
        HabitStats.dueDates(habit.category, habit.recurrence, habit.startDate, marks, today, until = today).toSet()
    }
    val positive = habit.category == HabitCategory.POSITIVE
    val status = { date: LocalDate -> dayStatus(date, date in marks, date in due, positive, habit.startDate, today) }
    HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
        MonthGrid(first.plusMonths(page.toLong()), status, today, onToggle)
    }
}

private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy")
private val dayName = DateTimeFormatter.ofPattern("EEEE d MMMM")

@Composable
private fun MonthGrid(month: YearMonth, status: (LocalDate) -> DayStatus, today: LocalDate, onToggle: ((LocalDate) -> Unit)?) {
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val done = days.count { status(it) == DayStatus.DONE }
    val missed = days.count { status(it) == DayStatus.MISSED }
    // Read only, the month is one summary; tappable, each day speaks for itself.
    val summary = Modifier.semantics(mergeDescendants = true) {
        contentDescription = "${month.format(monthTitle)}: done $done, missed $missed"
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth().then(if (onToggle == null) summary else Modifier),
    ) {
        Text(
            month.format(monthTitle),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            DayOfWeek.entries.forEach { day ->
                Text(
                    day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Monday first: blank cells before the 1st, then the days, in rows of seven.
        val lead = month.atDay(1).dayOfWeek.value - 1
        (List(lead) { null } + days).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date -> DayCell(date, date?.let(status) ?: DayStatus.NONE, today, onToggle) }
                repeat(7 - week.size) { DayCell(null, DayStatus.NONE, today, null) }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(date: LocalDate?, status: DayStatus, today: LocalDate, onToggle: ((LocalDate) -> Unit)?) {
    Box(Modifier.weight(1f).height(32.dp), contentAlignment = Alignment.Center) {
        if (date == null) return@Box
        val colors = MaterialTheme.colorScheme
        val fill = itemSpanStyle(ItemType.HABIT).color.takeOrElse { colors.primary }
        val isToday = date == today
        var circle = Modifier.size(28.dp)
        when (status) {
            DayStatus.DONE -> circle = circle.background(fill, CircleShape)
            DayStatus.MISSED -> circle = circle.border(1.5.dp, colors.error, CircleShape)
            else -> Unit
        }
        if (isToday && status != DayStatus.MISSED) circle = circle.border(1.5.dp, colors.onSurface, CircleShape)
        val tappable = onToggle != null && status != DayStatus.NONE && !date.isAfter(today)
        if (tappable) {
            val state = when (status) {
                DayStatus.DONE -> "done"
                DayStatus.MISSED -> "missed"
                else -> "not marked yet"
            }
            circle = circle
                .clearAndSetSemantics { contentDescription = "${date.format(dayName)}, $state" }
                .clickable(role = Role.Button, onClickLabel = if (status == DayStatus.DONE) "Mark missed" else "Mark done") { onToggle(date) }
        }
        Box(circle, contentAlignment = Alignment.Center) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    status == DayStatus.DONE -> if (fill.luminance() > 0.5f) Color.Black else Color.White
                    status == DayStatus.MISSED -> colors.error
                    status == DayStatus.NONE && date.isAfter(today) -> colors.onSurface.copy(alpha = 0.38f)
                    status == DayStatus.NONE -> colors.onSurfaceVariant
                    else -> colors.onSurface
                },
            )
        }
    }
}

/**
 * The card's chip for marking today: "Done today" for a positive habit, "Missed today" for a
 * negative one, which shows red with a cross when selected, so a miss never looks like a success.
 */
@Composable
fun HabitTodayChip(positive: Boolean, marked: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = marked,
        onClick = onClick,
        label = { Text(if (positive) "Done today" else "Missed today") },
        leadingIcon = if (marked) {
            { Icon(painterResource(if (positive) R.drawable.ic_check else R.drawable.ic_close), contentDescription = null, Modifier.size(18.dp)) }
        } else null,
        colors = if (positive) FilterChipDefaults.filterChipColors() else FilterChipDefaults.filterChipColors(
            selectedContainerColor = colors.errorContainer,
            selectedLabelColor = colors.onErrorContainer,
            selectedLeadingIconColor = colors.onErrorContainer,
        ),
    )
}
