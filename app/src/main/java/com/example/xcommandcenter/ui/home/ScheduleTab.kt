package com.example.xcommandcenter.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.xcommandcenter.R
import com.example.xcommandcenter.data.entity.BlockEntity
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.TagEntity
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.ScheduleAxis
import com.example.xcommandcenter.domain.ScheduleLayout
import com.example.xcommandcenter.domain.durationText
import com.example.xcommandcenter.ui.common.LinkLines
import com.example.xcommandcenter.ui.common.blockTimeText
import com.example.xcommandcenter.ui.common.itemTextStyle
import com.example.xcommandcenter.ui.common.label
import com.example.xcommandcenter.ui.common.minuteText
import com.example.xcommandcenter.ui.common.tabAccent
import kotlinx.coroutines.delay
import android.text.format.DateFormat
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HourHeight = 64.dp
private val Gutter = 56.dp
private val BarHeight = 32.dp
private val MinBlockHeight = 24.dp
/** How many minutes [MinBlockHeight] covers, rounded up. */
private val MinBlockMinutes = Math.ceil((MinBlockHeight + 2.dp).value / HourHeight.value * 60.0).toInt()
/** Room above the first hour's label. */
private val Top = 8.dp
private const val DAY = ScheduleAxis.DAY

/**
 * The Schedule tab: the active schedule's plan for the day (switched with [ScheduleSwitcher]),
 * hours down the left as in a calendar's day view, each block placed by its start and sized by its
 * length, overlapping ones side by side. Free stretches of two hours or more fold into a bar that
 * opens on a tap ([ScheduleAxis]). A red line marks the time now, and the tab opens scrolled to
 * it. Tapping an empty time adds a block there ([onCreateAt]); tapping a block opens it.
 */
@Composable
fun ScheduleTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit, onCreateAt: (Int) -> Unit) {
    val blocks by viewModel.blocks.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val reminders by viewModel.reminderLines.collectAsStateWithLifecycle()
    val active by viewModel.activeSchedule.collectAsStateWithLifecycle()
    val openGaps by viewModel.openGaps.collectAsStateWithLifecycle()
    var now by remember { mutableIntStateOf(minuteNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay((60 - LocalTime.now().second) * 1000L)
            now = minuteNow()
        }
    }
    fun keyOf(gap: Pair<Int, Int>) = "${active?.id}:${gap.first}-${gap.second}"
    val gaps = ScheduleAxis.gaps(blocks.map { it.startMinute to it.endMinute })
    val folded = gaps.filterNot { keyOf(it) in openGaps }
    val axis = ScheduleAxis(folded, perMinute = HourHeight.value / 60f, bar = BarHeight.value)
    fun at(minute: Int): Dp = Top + axis.y(minute).dp

    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // Again once the blocks have loaded, as folding moves everything.
    LaunchedEffect(active?.id, blocks.isEmpty()) {
        // An hour of context above the current time (or the folded bar holding it).
        val target = if (axis.hides(now)) axis.y(now) else axis.y(maxOf(now - 60, 0))
        scroll.scrollTo(with(density) { target.dp.roundToPx() })
    }

    Column(Modifier.fillMaxSize()) {
        ScheduleSwitcher(viewModel)
        Text(
            if (blocks.isEmpty()) "Nothing planned yet. Tap a time to add a block." else "Tap an empty time to add a block",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        BoxWithConstraints(Modifier.fillMaxSize().verticalScroll(scroll)) {
            val width = maxWidth
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(at(DAY) + Top * 2)
                    .pointerInput(folded) {
                        detectTapGestures { offset ->
                            val minute = axis.minuteAt(offset.y.toDp().value - Top.value) ?: return@detectTapGestures
                            onCreateAt((minute / 30 * 30).coerceIn(0, DAY - 30))
                        }
                    },
            ) {
                (0 until 24).filterNot { axis.hides(it * 60) }.forEach { hour -> HourRow(hour, at(hour * 60)) }
                gaps.forEach { gap ->
                    val isFolded = gap in folded
                    GapBar(
                        gap = gap,
                        folded = isFolded,
                        holdsNow = isFolded && now >= gap.first && now < gap.second,
                        onToggle = { viewModel.toggleGap(keyOf(gap)) },
                        modifier = Modifier.offset(y = at(gap.first)).padding(start = Gutter - 4.dp, end = 8.dp),
                    )
                }
                val columnWidth = width - Gutter - 8.dp
                // A short block is drawn at least MinBlockHeight tall; laid out by that drawn length, a
                // block right after it goes beside it instead of underneath.
                ScheduleLayout.place(blocks, { it.startMinute }, { maxOf(it.endMinute, it.startMinute + MinBlockMinutes) }).forEach { placed ->
                    val block = placed.item
                    val ref = ItemRef(ItemType.BLOCK, block.id)
                    val slot = columnWidth / placed.columns
                    BlockCard(
                        block = block,
                        tags = tagFilter.tagsOf(ref),
                        links = links[ref].orEmpty(),
                        hasReminder = ref in reminders,
                        onClick = { onOpen(ref) },
                        modifier = Modifier
                            .offset(x = Gutter + slot * placed.column, y = at(block.startMinute) + 1.dp)
                            .width(slot - 2.dp)
                            .height(maxOf(at(block.endMinute) - at(block.startMinute) - 2.dp, MinBlockHeight)),
                    )
                }
                if (!axis.hides(now)) NowLine(at(now))
            }
        }
    }
}

/**
 * A free stretch of [gap]: folded, a slim bar saying how long it is (with a red "now" when the
 * time now falls in it); open, a small tab at its top to fold it again.
 */
@Composable
private fun GapBar(gap: Pair<Int, Int>, folded: Boolean, holdsNow: Boolean, onToggle: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val range = "${minuteText(gap.first)} – ${minuteText(gap.second)}"
    val free = "${durationText(gap.second - gap.first)} free"
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(8.dp),
        color = if (folded) colors.surfaceVariant else Color.Transparent,
        modifier = modifier
            .fillMaxWidth()
            .height(if (folded) BarHeight - 2.dp else 24.dp)
            .semantics { contentDescription = if (folded) "$range, $free. Tap to show." else "$range, $free. Tap to fold." },
    ) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                painterResource(if (folded) R.drawable.ic_expand_more else R.drawable.ic_expand_less),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                if (folded) "$range · $free" else "Fold $free",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (holdsNow) {
                Box(Modifier.size(8.dp).background(colors.error, CircleShape))
                Text("now", style = MaterialTheme.typography.labelMedium, color = colors.error)
            }
        }
    }
}

private val hour12 = DateTimeFormatter.ofPattern("h a")
private val hour24 = DateTimeFormatter.ofPattern("HH:mm")

private fun minuteNow(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

/** The hour's label in the gutter and its line across the day. */
@Composable
private fun HourRow(hour: Int, y: Dp) {
    // "7 AM" or "07:00", as calendar apps label hours: a full "12:00 AM" doesn't fit the gutter.
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    Box(Modifier.offset(y = y).fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(start = Gutter - 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            LocalTime.of(hour, 0).format(if (is24Hour) hour24 else hour12),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.offset(y = (-7).dp).padding(start = 8.dp).width(Gutter - 12.dp),
        )
    }
}

@Composable
private fun NowLine(y: Dp) {
    val red = MaterialTheme.colorScheme.error
    Box(Modifier.offset(y = y - 1.dp).fillMaxWidth()) {
        Box(Modifier.padding(start = Gutter - 4.dp).fillMaxWidth().height(2.dp).background(red))
        Box(Modifier.offset(x = Gutter - 8.dp, y = (-4).dp).size(10.dp).background(red, CircleShape))
    }
}

/** "8:00 – 10:00 AM · 2 h". */
private fun timeAndLength(block: BlockEntity) = "${blockTimeText(block)} · ${durationText(block.endMinute - block.startMinute)}"

/** A block as the schedule shows it: title, time, notes, tags and links, as much as its height holds. */
@Composable
private fun BlockCard(
    block: BlockEntity,
    tags: List<TagEntity>,
    links: List<ItemIndexEntry>,
    hasReminder: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    // A light tint of the Schedule tab's color, with a solid edge on the left as calendars draw events.
    val accent = tabAccent(ItemType.BLOCK)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = lerp(MaterialTheme.colorScheme.surface, accent, 0.18f),
        modifier = modifier,
    ) {
        val short = block.endMinute - block.startMinute < 45
        Column(
            Modifier
                .drawBehind { drawRect(accent, size = Size(3.dp.toPx(), size.height)) }
                .padding(start = 11.dp, end = 8.dp, top = if (short) 2.dp else 4.dp, bottom = if (short) 2.dp else 4.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    block.title,
                    style = itemTextStyle(ItemType.BLOCK, MaterialTheme.typography.titleSmall),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // On one line with the time, the title gets the larger share; both shorten to fit.
                    modifier = Modifier.weight(if (short) 2f else 1f, fill = false),
                )
                if (short) {
                    Text(
                        timeAndLength(block),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                if (hasReminder) {
                    Icon(painterResource(R.drawable.ic_notifications), contentDescription = "Has reminders", Modifier.size(14.dp))
                }
            }
            if (!short) {
                Text(timeAndLength(block), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                if (block.notes.isNotBlank()) {
                    Text(block.notes, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (tags.isNotEmpty()) {
                    Text(
                        tags.joinToString("  ") { it.label() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LinkLines(links, maxLines = 1)
            }
        }
    }
}
