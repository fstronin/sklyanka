package ru.stohastika.sklyanka.ui.component

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Диалог выбора из списка дизайн-системы (SKL-78, §4.17, ОВ-98).
 *
 * Проверяется оболочка: заголовок, весь набор вариантов, отметка выбранного не одним
 * цветом (НФТ-30), немедленное применение выбора и «Отмена» без изменений.
 */
@RunWith(AndroidJUnit4::class)
class ListPickerDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theDialogShowsItsTitleAllOptionsAndTheCancelAction() {
        show()

        composeTestRule.onNodeWithTag(TAG_LIST_PICKER).assertIsDisplayed()
        composeTestRule.onNodeWithText(TITLE).assertIsDisplayed()
        OPTIONS.forEach { composeTestRule.onNodeWithText(it).assertIsDisplayed() }
        composeTestRule.onNodeWithText(context.getString(R.string.action_cancel)).assertIsDisplayed()
    }

    @Test
    fun theSelectedOptionIsMarkedWithACheckAndTheSelectedState() {
        show(selected = SECOND)

        composeTestRule.onNodeWithTag(TAG_LIST_PICKER_SELECTED, useUnmergedTree = true).assertExists()
        val marked = composeTestRule.onNodeWithTag(TAG_LIST_PICKER_SELECTED, useUnmergedTree = true)
            .fetchSemanticsNode()
        val option = composeTestRule.onAllNodesWithTag(TAG_LIST_PICKER_OPTION, useUnmergedTree = true)
            .fetchSemanticsNodes()
            .first { it.boundsInRoot.contains(marked.boundsInRoot.center) }
        assertEquals(true, option.config.getOrNull(SemanticsProperties.Selected))
    }

    @Test
    fun choosingAnOptionAppliesItAtOnce() {
        var chosen: String? = null
        show(selected = null, onSelect = { chosen = it })

        composeTestRule.onNodeWithText(SECOND).performClick()

        assertEquals(SECOND, chosen)
    }

    @Test
    fun cancelDismissesWithoutChoosing() {
        var chosen: String? = null
        var dismissed = false
        show(selected = null, onSelect = { chosen = it }, onDismiss = { dismissed = true })

        composeTestRule.onNodeWithText(context.getString(R.string.action_cancel)).performClick()

        assertTrue("cancel must dismiss the dialog", dismissed)
        assertFalse("cancel must not choose anything", chosen != null)
    }

    private fun show(selected: String? = SECOND, onSelect: (String) -> Unit = {}, onDismiss: () -> Unit = {}) {
        composeTestRule.setContent {
            SklyankaTheme {
                Picker(selected, onSelect, onDismiss)
            }
        }
    }

    @Composable
    private fun Picker(selected: String?, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
        ListPickerDialog(
            title = TITLE,
            options = OPTIONS,
            selected = selected,
            label = { it },
            onSelect = onSelect,
            onDismiss = onDismiss,
        )
    }

    private companion object {
        const val TITLE = "Title"
        const val SECOND = "second"
        val OPTIONS = listOf("first", SECOND, "third")
    }
}
