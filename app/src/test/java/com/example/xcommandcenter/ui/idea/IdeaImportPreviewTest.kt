package com.example.xcommandcenter.ui.idea

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.example.xcommandcenter.data.backup.BackupData
import com.example.xcommandcenter.data.backup.IdeaImportPreview
import com.example.xcommandcenter.data.entity.IdeaEntity
import com.example.xcommandcenter.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The import preview: counts, the tree read only, the new idea above it, and Import with the choices made (§2.80). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class IdeaImportPreviewTest {
    @get:Rule val compose = createComposeRule()

    private val preview = IdeaImportPreview(
        fileName = "Messy Jobs.docx",
        network = BackupData(
            ideas = listOf(
                IdeaEntity(id = 1, title = "Part I", body = "How AI replaces tasks."),
                IdeaEntity(id = 2, title = "Tier One", parentId = 1, body = "Clean, modular jobs face rapid substitution."),
                IdeaEntity(id = 3, title = "Part II", sortOrder = 1),
            ),
        ),
        rootTitle = "Messy Jobs",
        wrap = true,
        notes = listOf("1 paragraph that isn't a point was added to the explanation of the point above."),
    )

    @Test
    fun showsTheTreeAndImportsWithTheChoices() {
        var imported: Pair<String?, Long?>? = null
        compose.setContent {
            AppTheme { IdeaImportPreviewDialog(preview, existing = emptyList(), onImport = { t, p -> imported = t to p }, onDismiss = {}) }
        }
        compose.onNodeWithText("4 ideas in 3 levels from Messy Jobs.docx").assertExists()
        compose.onNodeWithText("1 paragraph that isn't a point", substring = true).assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Tier One"))
        compose.onNodeWithText("Clean, modular jobs face rapid substitution.").assertHasClickAction().performClick()
        compose.onNode(hasScrollAction()).performScrollToIndex(0)

        // A name is needed for the new idea.
        compose.onNode(hasSetTextAction()).performTextReplacement(" ")
        compose.onNodeWithText("Import").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("Messy Jobs (book)")
        compose.onNodeWithText("Import").performClick()
        assertEquals("Messy Jobs (book)" to null, imported)

        // Without the new idea: the document's own top level.
        compose.onNodeWithText("Put everything under a new idea").performClick()
        compose.onNodeWithText("3 ideas in 2 levels from Messy Jobs.docx").assertExists()
        compose.onNodeWithText("Import").performClick()
        assertEquals(null to null, imported)
    }
}
