package ru.stohastika.sklyanka.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Подтверждение разрушительного действия словом (§5.7).
 *
 * Проверяется компонент, а не экран: подпись-образец, поле и подтверждающая кнопка —
 * одно место на три потребителя, и «совпадение потребителей» проверяется здесь.
 */
@RunWith(AndroidJUnit4::class)
class ConfirmWordFieldTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theFieldWaitsForTheWordAndCarriesItsOwnName() {
        show()

        composeRule.onNodeWithTag(TAG_CONFIRM_WORD_LABEL).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.cd_confirm_word_field))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_CONFIRM_WORD_BUTTON).assertIsNotEnabled()
    }

    /** Регистр и краевые пробелы не считаются, внутренние значимы (§5.7). */
    @Test
    fun theWordMatchesWithoutCaseAndEdgeSpaces() {
        show()

        composeRule.onNodeWithTag(TAG_TEXT_FIELD_INPUT).performTextReplacement("  ${WORD.uppercase()}  ")

        composeRule.onNodeWithTag(TAG_CONFIRM_WORD_BUTTON).assertIsEnabled()
    }

    /** Неверный ввод виден недоступной кнопкой; второго объяснения нет (§5.7). */
    @Test
    fun aWrongWordLeavesTheButtonDisabledWithoutAnError() {
        show()

        composeRule.onNodeWithTag(TAG_TEXT_FIELD_INPUT).performTextReplacement("Delet")

        composeRule.onNodeWithTag(TAG_CONFIRM_WORD_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(TAG_TEXT_FIELD_ERROR).assertDoesNotExist()
    }

    /** Подпись-образец называет то же слово, что сверяется: второго литерала нет (§5.7). */
    @Test
    fun theSampleLabelNamesTheSameWordAsTheComparison() {
        show()

        composeRule.onNodeWithText(context.getString(R.string.confirm_word_label, WORD)).assertIsDisplayed()
    }

    private fun show() {
        composeRule.setContent { Sample() }
    }

    @Composable
    private fun Sample() {
        SklyankaTheme {
            var value by remember { mutableStateOf("") }
            ConfirmWordField(
                word = WORD,
                value = value,
                onValueChange = { value = it },
                confirmLabel = context.getString(CONFIRM_LABEL),
                onConfirm = {},
                confirmModifier = Modifier.testTag(TAG_CONFIRM_WORD_BUTTON),
            )
        }
    }

    private companion object {
        const val WORD = "Delete"

        /** Подпись кнопки — своя у каждого потребителя; в тесте она не переводится. */
        val CONFIRM_LABEL = ru.stohastika.sklyanka.ui.R.string.action_done

        const val TAG_CONFIRM_WORD_BUTTON = "confirm_word_button"
    }
}
