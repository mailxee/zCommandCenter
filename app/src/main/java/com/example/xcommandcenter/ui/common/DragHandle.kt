package com.example.xcommandcenter.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
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
 * services, which can't drag; null at either end of the list.
 */
@Composable
fun DragHandle(modifier: Modifier, onMoveUp: (() -> Unit)?, onMoveDown: (() -> Unit)?) {
    Icon(
        painterResource(R.drawable.ic_drag_handle),
        contentDescription = "Drag to reorder",
        modifier = modifier
            .semantics {
                customActions = listOfNotNull(
                    onMoveUp?.let { move -> CustomAccessibilityAction("Move up") { move(); true } },
                    onMoveDown?.let { move -> CustomAccessibilityAction("Move down") { move(); true } },
                )
            }
            .padding(12.dp),
    )
}
