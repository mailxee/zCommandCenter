package com.example.xcommandcenter.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.xcommandcenter.R

/**
 * The grip of a row that can be dragged to a new place. [modifier] carries the list's drag
 * gesture. [onMoveUp] and [onMoveDown] offer the same moves one step at a time to accessibility
 * services, which can't drag; null at either end of the list. [compact] draws a smaller, softer
 * grip for rows of a list being edited (a checklist), top-aligned with the row's first line.
 */
@Composable
fun DragHandle(modifier: Modifier, onMoveUp: (() -> Unit)?, onMoveDown: (() -> Unit)?, compact: Boolean = false) {
    Icon(
        painterResource(R.drawable.ic_drag_handle),
        contentDescription = "Drag to reorder",
        tint = if (compact) MaterialTheme.colorScheme.onSurfaceVariant else LocalContentColor.current,
        modifier = modifier
            .semantics {
                customActions = listOfNotNull(
                    onMoveUp?.let { move -> CustomAccessibilityAction("Move up") { move(); true } },
                    onMoveDown?.let { move -> CustomAccessibilityAction("Move down") { move(); true } },
                )
            }
            .then(if (compact) Modifier.padding(start = 4.dp, end = 10.dp, top = 10.dp, bottom = 10.dp).size(20.dp) else Modifier.padding(12.dp)),
    )
}
