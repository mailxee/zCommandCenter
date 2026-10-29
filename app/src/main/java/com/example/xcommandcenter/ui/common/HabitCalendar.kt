package com.example.xcommandcenter.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.xcommandcenter.domain.ItemType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * A habit's marked days, a month at a time: swipe between months, from the habit's first month
 * (its start, or an earlier mark) to the current one, which it opens on. Marked days are filled,
 * in the Habits tab's color for a habit to build and in red for one to avoid; today is outlined;
 * days before the start and after today are dimmed. Only shows; days are marked elsewhere.
 */
@Composable
fun HabitCalendar(marks: Set<LocalDate>, startDate: LocalDate, positive: Boolean, today: LocalDate = LocalDate.now()) {
    val current = YearMonth.from(today)
    val first = listOfNotNull(YearMonth.from(startDate), marks.minOrNull()?.let(YearMonth::from), current).min()
    val count = ChronoUnit.MONTHS.between(first, current).toInt() + 1
    val pager = rememberPagerState(initialPage = count - 1) { count }
    val fill = if (positive) itemSpanStyle(ItemType.HABIT).color.takeOrElse { MaterialTheme.colorScheme.primary } else MaterialTheme.colorScheme.error
    HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth()) { page ->
        MonthGrid(first.plusMonths(page.toLong()), marks, startDate, today, fill, positive)
    }
}

private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable
private fun MonthGrid(month: YearMonth, marks: Set<LocalDate>, startDate: LocalDate, today: LocalDate, fill: Color, positive: Boolean) {
    val done = marks.count { YearMonth.from(it) == month }
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "${month.format(monthTitle)}: ${if (positive) "done" else "missed"} on $done " +
                if (done == 1) "day" else "days"
        },
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
        val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date -> DayCell(date, date in marks, date == today, date != null && (date > today || date < startDate), fill) }
                repeat(7 - week.size) { DayCell(null, marked = false, today = false, dimmed = false, fill = fill) }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(date: LocalDate?, marked: Boolean, today: Boolean, dimmed: Boolean, fill: Color) {
    Box(Modifier.weight(1f).height(28.dp), contentAlignment = Alignment.Center) {
        if (date == null) return@Box
        val colors = MaterialTheme.colorScheme
        var circle = Modifier.size(26.dp)
        if (marked) circle = circle.background(fill, CircleShape)
        if (today) circle = circle.border(1.5.dp, colors.onSurface, CircleShape)
        Box(circle, contentAlignment = Alignment.Center) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    marked -> if (fill.luminance() > 0.5f) Color.Black else Color.White
                    dimmed -> colors.onSurface.copy(alpha = 0.38f)
                    else -> colors.onSurface
                },
            )
        }
    }
}
