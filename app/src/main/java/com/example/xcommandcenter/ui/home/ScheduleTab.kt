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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import com.example.xcommandcenter.domain.ScheduleLayout
import com.example.xcommandcenter.ui.common.LinkLines
import com.example.xcommandcenter.ui.common.blockTimeText
import com.example.xcommandcenter.ui.common.itemTextStyle
import com.example.xcommandcenter.ui.common.label
import com.example.xcommandcenter.ui.common.minuteText
import kotlinx.coroutines.delay
import java.time.LocalTime

private val HourHeight = 64.dp
private val Gutter = 56.dp
private const val DAY = 24 * 60

/**
 * The Schedule tab: the plan for every day, hours down the left as in a calendar's day view, each
 * block placed by its start and sized by its length, overlapping ones side by side. A red line
 * marks the time now, and the tab opens scrolled to it. Tapping an empty half hour adds a block
 * there ([onCreateAt]); tapping a block opens it.
 */
@Composable
fun ScheduleTab(viewModel: HomeViewModel, onOpen: (ItemRef) -> Unit, onCreateAt: (Int) -> Unit) {
    val blocks by viewModel.blocks.collectAsStateWithLifecycle()
    val links by viewModel.linksByOwner.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val reminders by viewModel.reminderLines.collectAsStateWithLifecycle()
    var now by remember { mutableIntStateOf(minuteNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay((60 - LocalTime.now().second) * 1000L)
            now = minuteNow()
        }
    }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        // An hour of context above the current time.
        scroll.scrollTo(with(density) { (HourHeight * (maxOf(now - 60, 0) / 60f)).roundToPx() })
    }

    Column(Modifier.fillMaxSize()) {
        Text(
            if (blocks.isEmpty()) "Your plan for every day. Tap a time to add a block." else "Your plan for every day · tap an empty time to add a block",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        BoxWithConstraints(Modifier.fillMaxSize().verticalScroll(scroll)) {
            val width = maxWidth
            val hourPx = with(density) { HourHeight.toPx() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(HourHeight * 24 + 16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val minute = ((offset.y - 8.dp.toPx()) / hourPx * 60).toInt() / 30 * 30
                            onCreateAt(minute.coerceIn(0, DAY - 30))
                        }
                    },
            ) {
                (0 until 24).forEach { hour -> HourRow(hour) }
                val columnWidth = width - Gutter - 8.dp
                ScheduleLayout.place(blocks, { it.startMinute }, { it.endMinute }).forEach { placed ->
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
                            .offset(x = Gutter + slot * placed.column, y = 8.dp + minuteOffset(block.startMinute) + 1.dp)
                            .width(slot - 2.dp)
                            .height(maxOf(minuteOffset(block.endMinute - block.startMinute) - 2.dp, 24.dp)),
                    )
                }
                NowLine(now)
            }
        }
    }
}

private fun minuteNow(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

private fun minuteOffset(minutes: Int): Dp = HourHeight * (minutes / 60f)

/** The hour's label in the gutter and its line across the day. */
@Composable
private fun HourRow(hour: Int) {
    Box(Modifier.offset(y = 8.dp + HourHeight * hour).fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(start = Gutter - 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            minuteText(hour * 60),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.offset(y = (-7).dp).padding(start = 8.dp).width(Gutter - 12.dp),
        )
    }
}

@Composable
private fun NowLine(minute: Int) {
    val red = MaterialTheme.colorScheme.error
    Box(Modifier.offset(y = 8.dp + minuteOffset(minute) - 1.dp).fillMaxWidth()) {
        Box(Modifier.padding(start = Gutter - 4.dp).fillMaxWidth().height(2.dp).background(red))
        Box(Modifier.offset(x = Gutter - 8.dp, y = (-4).dp).size(10.dp).background(red, CircleShape))
    }
}

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
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        val short = block.endMinute - block.startMinute < 45
        Column(Modifier.padding(horizontal = 8.dp, vertical = if (short) 2.dp else 4.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    block.title,
                    style = itemTextStyle(ItemType.BLOCK, MaterialTheme.typography.titleSmall),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (short) {
                    Text(blockTimeText(block), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                if (hasReminder) {
                    Icon(painterResource(R.drawable.ic_notifications), contentDescription = "Has reminders", Modifier.size(14.dp))
                }
            }
            if (!short) {
                Text(blockTimeText(block), style = MaterialTheme.typography.labelSmall, maxLines = 1)
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
