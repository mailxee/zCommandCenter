package com.example.xcommandcenter.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.xcommandcenter.R
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.domain.Priority
import com.example.xcommandcenter.domain.StyleColor

/** Each tab's own color, unless the user gave the tab's items a color in Settings: then that one. */
private fun defaultAccent(tab: ItemType): StyleColor = when (tab) {
    ItemType.BLOCK -> StyleColor.TEAL
    ItemType.GOAL -> StyleColor.PURPLE
    ItemType.PLAN -> StyleColor.INDIGO
    ItemType.TASK -> StyleColor.BLUE
    ItemType.HABIT -> StyleColor.GREEN
    ItemType.NOTE -> StyleColor.AMBER
    ItemType.BOOK, ItemType.CHAPTER -> StyleColor.BROWN
    ItemType.QUOTE -> StyleColor.PINK
}

/** [type]'s tab color, readable on the theme's surface: for its icon, the tab indicator and the add button. */
@Composable
fun tabAccent(type: ItemType): Color {
    val style = LocalItemStyles.current[type].color
    return (if (style == StyleColor.DEFAULT) defaultAccent(type.tab) else style).toColor(isDarkSurface())
}

/** Text and icons on [tabAccent]. */
@Composable
fun onTabAccent(): Color = if (isDarkSurface()) Color(0xFF15171C) else Color.White

@DrawableRes
fun tabIcon(type: ItemType): Int = when (type.tab) {
    ItemType.BLOCK -> R.drawable.ic_tab_schedule
    ItemType.GOAL -> R.drawable.ic_tab_goal
    ItemType.PLAN -> R.drawable.ic_tab_plan
    ItemType.TASK -> R.drawable.ic_tab_task
    ItemType.HABIT -> R.drawable.ic_tab_habit
    ItemType.NOTE -> R.drawable.ic_tab_note
    ItemType.BOOK, ItemType.CHAPTER -> R.drawable.ic_tab_book
    ItemType.QUOTE -> R.drawable.ic_tab_quote
}

/** The strip down a card's left edge and its priority label: red, orange, blue-grey, light grey. */
@Composable
fun priorityColor(priority: Priority): Color {
    val dark = isDarkSurface()
    return when (priority) {
        Priority.CRITICAL -> MaterialTheme.colorScheme.error
        Priority.HIGH -> Color(if (dark) 0xFFFFB06B else 0xFFD65A00)
        Priority.MEDIUM -> Color(if (dark) 0xFF8FA9C4 else 0xFF5A7896)
        Priority.LOW -> MaterialTheme.colorScheme.outline
    }
}
