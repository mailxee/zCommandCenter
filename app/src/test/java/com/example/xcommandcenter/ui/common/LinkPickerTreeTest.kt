package com.example.xcommandcenter.ui.common

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.xcommandcenter.data.entity.ItemIndexEntry
import com.example.xcommandcenter.data.entity.TreeNode
import com.example.xcommandcenter.domain.ItemRef
import com.example.xcommandcenter.domain.ItemType
import com.example.xcommandcenter.ui.theme.AppTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The link picker shows plans as a tree that opens on demand, while a search looks through all. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LinkPickerTreeTest {
    @get:Rule val compose = createComposeRule()

    private val plans = listOf(
        ItemIndexEntry(ItemType.PLAN, 1, "Career", null),
        ItemIndexEntry(ItemType.PLAN, 2, "Learn Kotlin", null),
        ItemIndexEntry(ItemType.PLAN, 3, "Finish course", null),
        ItemIndexEntry(ItemType.PLAN, 4, "Health", null),
    )
    private val tree = listOf(TreeNode(1, null, 0), TreeNode(2, 1, 0), TreeNode(3, 2, 0), TreeNode(4, null, 1))

    private fun show(onPick: (ItemIndexEntry) -> Unit = {}) = compose.setContent {
        AppTheme {
            LinkPickerDialog(
                self = ItemRef(ItemType.TASK, 99),
                alreadyLinked = emptySet(),
                search = { type, _ -> flowOf(if (type == ItemType.PLAN) plans else emptyList()) },
                chapters = { flowOf(emptyList()) },
                tree = { type -> flowOf(if (type == ItemType.PLAN) tree else emptyList()) },
                onPick = onPick,
                onDismiss = {},
            )
        }
    }

    private fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun showsTopLevelFirstAndOpensOnDemand() {
        show()
        compose.onNodeWithText("Plans").performClick()
        assertEquals(1, count("Career"))
        assertEquals(1, count("Health"))
        assertEquals(0, count("Learn Kotlin"))

        compose.onNodeWithContentDescription("Show 1 below").performClick()
        assertEquals(1, count("Learn Kotlin"))
        assertEquals(0, count("Finish course"))
        compose.onNodeWithContentDescription("Show 1 below").performClick()
        assertEquals(1, count("Finish course"))

    }

    @Test
    fun searchFindsDeepItemsAndPicksThem() {
        var picked: ItemIndexEntry? = null
        show { picked = it }
        compose.onNodeWithText("Plans").performClick()
        compose.onNodeWithText("Search all plans").performTextInput("finish")
        assertEquals(1, count("Finish course"))
        assertEquals(1, count("Career")) // Shown above the hit, so the tree still reads.
        assertEquals(0, count("Health"))
        compose.onNodeWithText("Finish course").performClick()
        assertEquals(3L, picked?.id)
    }
}
