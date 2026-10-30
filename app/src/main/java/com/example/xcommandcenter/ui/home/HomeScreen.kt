package com.example.xcommandcenter.ui.home

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
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.AppViewModelProvider
import com.example.xcommandcenter.ui.common.itemSpanStyle
import com.example.xcommandcenter.ui.common.itemTextStyle
import com.example.xcommandcenter.ui.common.label
import com.example.xcommandcenter.ui.common.onTabAccent
import com.example.xcommandcenter.ui.common.tabAccent
import com.example.xcommandcenter.ui.common.tabIcon

@Composable
fun HomeScreen(
    onOpen: (ItemRef) -> Unit,
    onCreate: (ItemType) -> Unit,
    onCreateBlock: (minute: Int) -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val order by viewModel.tabOrder.collectAsStateWithLifecycle()
    // Remember the tab itself, not its index, so reordering in Settings keeps the selection.
    // Null until the user picks one: the first tab of the stored order is then shown, even though
    // that order loads a moment after the first frame.
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = order.firstOrNull { it.name == selectedName } ?: order.first()
    val reorder by viewModel.reorderMode.collectAsStateWithLifecycle()
    val counts by viewModel.tabCounts.collectAsStateWithLifecycle()
    val picking by viewModel.linkPicking.collectAsStateWithLifecycle()
    // While picking a root for link mode, a tapped card becomes the root instead of opening.
    val open: (ItemRef) -> Unit = { ref -> if (picking) viewModel.pickLinkRoot(ref) else onOpen(ref) }

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
                title = { Text(if (reorder) "Reorder" else stringResource(R.string.app_name)) },
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
                            selectedName = tab.name
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
                LinkModeButton(viewModel, picking)
            }
            LinkModeStatus(viewModel, picking)
            AnimatedVisibility(visible = showFilters) {
                TagFilterBar(viewModel, selected)
            }
            CompositionLocalProvider(LocalCreateItem provides onCreate) {
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
                        ItemType.BLOCK -> ScheduleTab(viewModel, open, onCreateBlock)
                        ItemType.CHAPTER -> Unit // Not a tab.
                    }
                }
            }
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
    val filtering = tagFilter.active || priorityFilters[tab].orEmpty().isNotEmpty() || (tab == ItemType.TASK && repeat != RepeatFilter.ALL)
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
 * Link mode: pressed, the next card tapped becomes the root, and every tab then shows only the
 * items linked to it, directly or through other items. Pressed again, back to normal mode.
 */
@Composable
private fun LinkModeButton(viewModel: HomeViewModel, picking: Boolean) {
    val root by viewModel.linkRoot.collectAsStateWithLifecycle()
    val on = picking || root != null
    FilledTonalIconToggleButton(
        checked = on,
        onCheckedChange = { viewModel.toggleLinkMode() },
        modifier = Modifier.padding(start = 4.dp).semantics {
            stateDescription = when {
                picking -> "Choosing an item"
                root != null -> "On"
                else -> "Off"
            }
        },
    ) {
        Icon(painterResource(R.drawable.ic_link), contentDescription = "Link mode")
    }
}

/**
 * A banner under the search bar while link mode is picking or on: what to do, or whose network is
 * shown, with a cross that leaves link mode as the link button does.
 */
@Composable
private fun LinkModeStatus(viewModel: HomeViewModel, picking: Boolean) {
    val root by viewModel.linkRoot.collectAsStateWithLifecycle()
    val current = root
    AnimatedVisibility(visible = picking || current != null) {
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
                if (current == null) {
                    Text("Tap a card to show only the items linked to it", style = style, modifier = Modifier.weight(1f))
                } else {
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
                IconButton(onClick = viewModel::toggleLinkMode) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Leave link mode", modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/**
 * One filter for every tab: tap a tag to show only items with it, press and hold it to hide items
 * with it; both stay on when switching tabs. Shows "Untagged" (items without tags) and the tags
 * used on [tab]'s items, plus any tag in use by the filter, so it can always be turned off; hidden
 * when there are no tags to offer. The chips wrap onto more lines, so every tag stays on screen.
 */
@Composable
private fun TagFilterBar(viewModel: HomeViewModel, tab: ItemType) {
    val allTags by viewModel.allTags.collectAsStateWithLifecycle()
    val filter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val tabTagIds by viewModel.tabTagIds.collectAsStateWithLifecycle()
    val used = tabTagIds[tab].orEmpty()
    val tags = allTags.filter { it.id in used || it.id in filter.selected || it.id in filter.excluded }
    val untagged = TagFilter.UNTAGGED
    if (tags.isEmpty() && untagged !in filter.selected && untagged !in filter.excluded) return
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (filter.active) {
            AssistChip(onClick = viewModel::clearTags, label = { Text("Clear") })
        }
        TagChip(
            label = "Untagged",
            selected = untagged in filter.selected,
            excluded = untagged in filter.excluded,
            onClick = { viewModel.toggleTag(untagged) },
            onLongClick = { viewModel.toggleExcludedTag(untagged) },
        )
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
private fun TagChip(label: String, selected: Boolean, excluded: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
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
                    onLongClickLabel = if (excluded) "Show items with this tag" else "Hide items with this tag",
                )
                .semantics {
                    if (selected) stateDescription = "Showing only items with this tag"
                    if (excluded) stateDescription = "Items with this tag are hidden"
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
