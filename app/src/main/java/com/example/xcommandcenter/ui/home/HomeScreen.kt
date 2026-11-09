package com.example.xcommandcenter.ui.home

import com.example.xcommandcenter.domain.AppMode
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.remember
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.xcommandcenter.R
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ChipState
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.TaskStatusFilter
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.common.itemSpanStyle
import com.example.xcommandcenter.ui.common.itemTextStyle
import com.example.xcommandcenter.ui.common.label
import com.example.xcommandcenter.ui.common.onTabAccent
import com.example.xcommandcenter.ui.common.tabAccent
import com.example.xcommandcenter.ui.common.tabIcon
import com.example.xcommandcenter.ui.common.kindIcon
import com.example.xcommandcenter.ui.common.kindColor
import com.example.xcommandcenter.domain.IdeaKind
import com.example.xcommandcenter.domain.IdeaKindFilter

@Composable
fun HomeScreen(
    onOpen: (ItemRef) -> Unit,
    onCreate: (ItemType) -> Unit,
    onCreateBlock: (minute: Int) -> Unit,
    onSettings: () -> Unit,
    /** Opens the map of links: the shown network's, or every network (null). */
    onMap: (ItemRef?) -> Unit = {},
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val order by viewModel.shownTabs.collectAsStateWithLifecycle()
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    // Remember the tab itself, not its index, so reordering in Settings keeps the selection; one
    // per mode, so switching back returns to the tab left there.
    // Null until the user picks one: the first tab of the stored order is then shown, even though
    // that order loads a moment after the first frame.
    var selectedDo by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedThink by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedName = if (mode == AppMode.DO) selectedDo else selectedThink
    val selected = order.firstOrNull { it.name == selectedName } ?: order.first()
    val reorder by viewModel.reorderMode.collectAsStateWithLifecycle()
    val counts by viewModel.tabCounts.collectAsStateWithLifecycle()
    val open: (ItemRef) -> Unit = onOpen

    // The title bar slides away while the list scrolls down and back on the way up; the add button
    // shrinks to its icon meanwhile, so it covers less of the cards.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val fabExpanded = scrollBehavior.state.collapsedFraction < 0.5f
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val accent = tabAccent(selected)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                // The mode switch stands where the app's name was; the name stays for screen readers.
                title = { if (reorder) Text("Reorder") else ModeSwitch(mode, viewModel::setMode) },
                scrollBehavior = scrollBehavior,
                actions = {
                    if (reorder) {
                        IconButton(onClick = { viewModel.setReorderMode(false) }) {
                            Icon(painterResource(R.drawable.ic_check), contentDescription = "Done reordering")
                        }
                    } else if (selected != ItemType.BLOCK) {
                        // Blocks are ordered by their times, so the Schedule tab has nothing to reorder.
                        IconButton(
                            onClick = {
                                viewModel.setReorderMode(true)
                                // Items can only be moved in their tab's custom order.
                                if (selected == ItemType.TASK) viewModel.setTaskSort(TaskSort.CUSTOM)
                                if (selected in priorityTabs) viewModel.setPrioritySort(selected, PrioritySort.CUSTOM)
                            },
                        ) {
                            Icon(painterResource(R.drawable.ic_reorder), contentDescription = "Reorder")
                        }
                    }
                    if (!reorder) {
                        IconButton(onClick = { onMap(viewModel.linkRoot.value?.ref) }) {
                            Icon(painterResource(R.drawable.ic_map), contentDescription = "Map of links")
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            val label = "New ${selected.singular.lowercase()}"
            ExtendedFloatingActionButton(
                onClick = { onCreate(selected) },
                expanded = fabExpanded,
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(label) },
                containerColor = accent,
                contentColor = onTabAccent(),
                modifier = Modifier.semantics { contentDescription = label },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Narrow tabs, so the first five fit on a phone without scrolling.
            PrimaryScrollableTabRow(
                selectedTabIndex = order.indexOf(selected),
                edgePadding = 4.dp,
                minTabWidth = 0.dp,
                indicator = {
                    TabRowDefaults.PrimaryIndicator(
                        Modifier.tabIndicatorOffset(order.indexOf(selected), matchContentSize = true),
                        width = Dp.Unspecified,
                        color = accent,
                    )
                },
            ) {
                order.forEach { tab ->
                    Tab(
                        selected = tab == selected,
                        onClick = {
                            if (mode == AppMode.DO) selectedDo = tab.name else selectedThink = tab.name
                            if (reorder && tab == ItemType.TASK) viewModel.setTaskSort(TaskSort.CUSTOM)
                            if (reorder && tab in priorityTabs) viewModel.setPrioritySort(tab, PrioritySort.CUSTOM)
                        },
                    ) {
                        TabLabel(tab, counts[tab], tab == selected)
                    }
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp),
            ) {
                SearchBar(viewModel, selected, Modifier.weight(1f))
                FilterButton(viewModel, selected, showFilters) { showFilters = !showFilters }
            }
            LinkModeStatus(viewModel, onMap)
            AnimatedVisibility(visible = showFilters) {
                TagFilterBar(viewModel, selected)
            }
            val terms by viewModel.terms.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalCreateItem provides onCreate, LocalTerms provides terms) {
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(90)) },
                    label = "tab",
                ) { tab ->
                    when (tab) {
                        ItemType.TASK -> TasksTab(viewModel, open, showFilters)
                        ItemType.GOAL -> GoalsTab(viewModel, open, showFilters)
                        ItemType.HABIT -> HabitsTab(viewModel, open, showFilters)
                        ItemType.PLAN -> PlansTab(viewModel, open, showFilters)
                        ItemType.NOTE -> NotesTab(viewModel, open, showFilters)
                        ItemType.BOOK -> BooksTab(viewModel, open, showFilters)
                        ItemType.QUOTE -> QuotesTab(viewModel, open, showFilters)
                        ItemType.PERSON -> PeopleTab(viewModel, open, showFilters)
                        ItemType.IDEA -> IdeasTab(viewModel, open, showFilters)
                        ItemType.BLOCK -> ScheduleTab(viewModel, open, onCreateBlock)
                        ItemType.CHAPTER -> Unit // Not a tab.
                    }
                }
            }
        }
    }
}

/** Do or Think: which side of the app the tabs, link mode and the map show. */
@Composable
private fun ModeSwitch(mode: AppMode, onChange: (AppMode) -> Unit) {
    val name = stringResource(R.string.app_name)
    SingleChoiceSegmentedButtonRow(Modifier.width(184.dp).semantics { contentDescription = "$name: ${mode.label} mode" }) {
        AppMode.entries.forEachIndexed { i, option ->
            SegmentedButton(
                selected = mode == option,
                onClick = { onChange(option) },
                shape = SegmentedButtonDefaults.itemShape(i, AppMode.entries.size),
            ) { Text(option.label, maxLines = 1) }
        }
    }
}

/** The tab's icon and name, the icon in the tab's color when selected, with "shown/total" below. */
@Composable
private fun TabLabel(tab: ItemType, count: TabCount?, selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp).semantics(mergeDescendants = true) {
            if (count != null) contentDescription = "${tab.tabLabel}, ${count.shown} of ${count.total} shown"
        },
    ) {
        Icon(
            painterResource(tabIcon(tab)),
            contentDescription = null,
            tint = if (selected) tabAccent(tab) else colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            tab.tabLabel,
            style = itemTextStyle(tab, MaterialTheme.typography.titleSmall),
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
        )
        Text(
            count?.let { "${it.shown}/${it.total}" } ?: " ",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
        )
    }
}

/**
 * Shows or hides the filters: the tag chips and, at the top of the list, the tab's sort and filter
 * rows. A dot on the button says a filter is hiding items, even while they are folded away.
 */
@Composable
private fun FilterButton(viewModel: HomeViewModel, tab: ItemType, shown: Boolean, onToggle: () -> Unit) {
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val priorityFilters by viewModel.priorityFilters.collectAsStateWithLifecycle()
    val repeat by viewModel.repeatFilter.collectAsStateWithLifecycle()
    val status by viewModel.taskStatus.collectAsStateWithLifecycle()
    val kinds by viewModel.ideaKinds.collectAsStateWithLifecycle()
    val filtering = tagFilter.active || priorityFilters[tab].orEmpty().isNotEmpty() ||
        (tab == ItemType.TASK && (repeat != RepeatFilter.ALL || status.active)) || (tab == ItemType.IDEA && kinds.active)
    FilledTonalIconToggleButton(
        checked = shown,
        onCheckedChange = { onToggle() },
        modifier = Modifier.padding(start = 4.dp).semantics {
            stateDescription = (if (shown) "Shown" else "Hidden") + if (filtering) ", filters on" else ""
        },
    ) {
        BadgedBox(badge = { if (filtering) Badge(containerColor = MaterialTheme.colorScheme.primary) }) {
            Icon(painterResource(R.drawable.ic_filter), contentDescription = "Filters")
        }
    }
}

/**
 * Searches every tab: each shows only the items whose text (title, description, notes, body,
 * author and so on) contains every word typed. The text stays when switching tabs.
 */
@Composable
private fun SearchBar(viewModel: HomeViewModel, tab: ItemType, modifier: Modifier) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    // Shorter than a standard text field (44 dp, not 56), with the same look.
    val interaction = remember { MutableInteractionSource() }
    val colors = OutlinedTextFieldDefaults.colors()
    BasicTextField(
        value = query,
        onValueChange = viewModel::setSearchQuery,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = interaction,
        modifier = modifier.height(44.dp),
    ) { field ->
        OutlinedTextFieldDefaults.DecorationBox(
            value = query,
            innerTextField = field,
            enabled = true,
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            interactionSource = interaction,
            placeholder = { Text("Search ${tab.tabLabel.lowercase()}") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            trailingIcon = if (query.isEmpty()) null else {
                { IconButton(onClick = { viewModel.setSearchQuery("") }) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Clear search") } }
            },
            colors = colors,
            contentPadding = PaddingValues(vertical = 0.dp),
            container = {
                OutlinedTextFieldDefaults.Container(
                    enabled = true,
                    isError = false,
                    interactionSource = interaction,
                    colors = colors,
                    shape = RoundedCornerShape(22.dp),
                )
            },
        )
    }
}

/**
 * Link mode, turned on by pressing and holding a card: a banner under the search bar naming whose
 * network every tab shows, with a cross that goes back to normal mode.
 */
@Composable
private fun LinkModeStatus(viewModel: HomeViewModel, onMap: (ItemRef?) -> Unit) {
    val root by viewModel.linkRoot.collectAsStateWithLifecycle()
    // Kept while the banner slides away, so its text doesn't vanish first.
    var shown by remember { mutableStateOf<ItemIndexEntry?>(null) }
    root?.let { shown = it }
    val current = shown
    AnimatedVisibility(visible = root != null) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
                Icon(painterResource(R.drawable.ic_link), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                val style = MaterialTheme.typography.bodyMedium
                if (current != null) {
                    Text(
                        buildAnnotatedString {
                            append("Linked to ")
                            withStyle(itemSpanStyle(current.type.tab).merge(SpanStyle(fontWeight = FontWeight.Bold))) { append(current.title) }
                        },
                        style = style,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).semantics { contentDescription = "Link mode: showing items linked to ${current.title}" },
                    )
                }
                if (current != null) {
                    TextButton(onClick = { onMap(current.ref) }) { Text("Map") }
                }
                IconButton(onClick = viewModel::exitLinkMode) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Leave link mode", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/**
 * One filter for every tab: tap a tag to show only items with it, press and hold it to hide items
 * with it; both stay on when switching tabs. Shows "Untagged" (items without tags) and the tags
 * used on [tab]'s items, plus any tag in use by the filter, so it can always be turned off. The chips wrap onto more lines, so every tag stays on screen.
 * Every tab has "Reminder" (items with a reminder going off today, see [TagFilter.reminder]).
 * The Tasks tab also has "Past due" and "Completed" (see [TaskStatusFilter]), and the Ideas tab a
 * chip per kind (see [IdeaKindFilter]), which work the same way.
 */
@Composable
private fun TagFilterBar(viewModel: HomeViewModel, tab: ItemType) {
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val filter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val tabTagIds by viewModel.tabTagIds.collectAsStateWithLifecycle()
    val used = tabTagIds[tab].orEmpty()
    val tags = allTags.filter { it.id in used || it.id in filter.selected || it.id in filter.excluded }
    val status by viewModel.taskStatus.collectAsStateWithLifecycle()
    val untagged = TagFilter.UNTAGGED
    val kinds by viewModel.ideaKinds.collectAsStateWithLifecycle()
    val tasks = tab == ItemType.TASK
    val ideas = tab == ItemType.IDEA
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (ideas) {
            IdeaKind.entries.forEach { kind ->
                TagChip(
                    label = kind.label,
                    icon = kindIcon(kind),
                    iconTint = kindColor(kind),
                    selected = kinds.state(kind) == ChipState.ONLY,
                    excluded = kinds.state(kind) == ChipState.HIDDEN,
                    onClick = { viewModel.tapIdeaKind(kind) },
                    onLongClick = { viewModel.holdIdeaKind(kind) },
                    holdLabel = kind.plural.lowercase(),
                )
            }
        }
        if (filter.active || (tasks && status.active) || (ideas && kinds.active)) {
            AssistChip(onClick = viewModel::clearTags, label = { Text("Clear") })
        }
        TagChip(
            label = "Untagged",
            selected = untagged in filter.selected,
            excluded = untagged in filter.excluded,
            onClick = { viewModel.toggleTag(untagged) },
            onLongClick = { viewModel.toggleExcludedTag(untagged) },
        )
        TagChip(
            label = "Reminder",
            icon = R.drawable.ic_notifications,
            selected = filter.reminder == ChipState.ONLY,
            excluded = filter.reminder == ChipState.HIDDEN,
            onClick = viewModel::tapReminderChip,
            onLongClick = viewModel::holdReminderChip,
            holdLabel = "items with a reminder today",
        )
        if (tasks) {
            listOf(
                Triple(TaskStatusFilter.Chip.PAST_DUE, "Past due", R.drawable.ic_past_due),
                Triple(TaskStatusFilter.Chip.COMPLETED, "Completed", R.drawable.ic_completed),
            ).forEach { (chip, label, icon) ->
                TagChip(
                    label = label,
                    icon = icon,
                    selected = status.state(chip) == ChipState.ONLY,
                    excluded = status.state(chip) == ChipState.HIDDEN,
                    onClick = { viewModel.tapTaskStatus(chip) },
                    onLongClick = { viewModel.holdTaskStatus(chip) },
                    holdLabel = "tasks",
                )
            }
        }
        tags.forEach { tag ->
            TagChip(
                label = tag.label(),
                selected = tag.id in filter.selected,
                excluded = tag.id in filter.excluded,
                onClick = { viewModel.toggleTag(tag.id) },
                onLongClick = { viewModel.toggleExcludedTag(tag.id) },
            )
        }
    }
}

/**
 * A tag in the filter bar, drawn like a filter chip (Material's chips take no long press). Selected
 * shows a check; excluded is greyed out and struck through.
 */
@Composable
private fun TagChip(
    label: String,
    selected: Boolean,
    excluded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    icon: Int? = null,
    iconTint: Color? = null,
    /** What the chip picks out, for accessibility: "items with this tag", or "tasks" for a built-in chip. */
    holdLabel: String? = null,
) {
    val what = holdLabel?.let { "these $it" } ?: "items with this tag"
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(8.dp)
    Surface(
        shape = shape,
        color = if (selected) colors.secondaryContainer else Color.Transparent,
        border = if (selected) null else BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongClick()
                    },
                    onLongClickLabel = if (excluded) "Show $what" else "Hide $what",
                )
                .semantics {
                    if (selected) stateDescription = "Showing only $what"
                    if (excluded) stateDescription = "${what.replaceFirstChar { it.uppercase() }} are hidden"
                }
                .heightIn(min = 32.dp)
                .padding(horizontal = 12.dp),
        ) {
            if (selected) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = colors.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
            } else if (icon != null) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = if (excluded) colors.onSurface.copy(alpha = 0.38f) else iconTint ?: colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    selected -> colors.onSecondaryContainer
                    excluded -> colors.onSurface.copy(alpha = 0.38f)
                    else -> colors.onSurfaceVariant
                },
                textDecoration = if (excluded) TextDecoration.LineThrough else null,
            )
        }
    }
}
