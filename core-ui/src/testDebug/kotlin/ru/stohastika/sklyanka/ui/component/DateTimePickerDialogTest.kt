package ru.stohastika.sklyanka.ui.component

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalDate
import java.time.LocalTime

/**
 * Диалог выбора времени и даты дизайн-системы (SKL-69, §4.14, ОВ-89).
 *
 * Проверяется оболочка приложения: заголовок, действия «Отмена»/«Готово», возврат
 * выбранного значения. Механика M3 (циферблат, клавиатура, лента дней) — чужая и
 * в объём не входит; проверяется, что флаг 12/24 доходит до механизма.
 */
@RunWith(AndroidJUnit4::class)
class DateTimePickerDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun theTimeDialogShowsItsTitleAndBothActions() {
        show {
            DateTimePickerDialog(
                title = TIME_TITLE,
                initial = LocalTime.of(8, 0),
                is24Hour = true,
                onDismiss = {},
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithTag(TAG_DATE_TIME_PICKER).assertExists()
        composeTestRule.onNodeWithText(TIME_TITLE).assertExists()
        composeTestRule.onNodeWithText(context.getString(R.string.action_cancel)).assertExists()
        composeTestRule.onNodeWithText(context.getString(R.string.action_done)).assertExists()
    }

    @Test
    fun doneReturnsTheChosenTimeAndCancelReturnsNothing() {
        var confirmed: LocalTime? = null
        var dismissed = false
        show {
            DateTimePickerDialog(
                title = TIME_TITLE,
                initial = LocalTime.of(8, 0),
                is24Hour = true,
                onDismiss = { dismissed = true },
                onConfirm = { confirmed = it },
            )
        }

        composeTestRule.onNodeWithText(context.getString(R.string.action_done)).performClick()

        assertEquals(LocalTime.of(8, 0), confirmed)
        assertTrue("the dialog must close after confirming", !dismissed)
    }

    @Test
    fun cancelDismissesWithoutChangingTheTime() {
        var confirmed: LocalTime? = null
        var dismissed = false
        show {
            DateTimePickerDialog(
                title = TIME_TITLE,
                initial = LocalTime.of(8, 0),
                is24Hour = true,
                onDismiss = { dismissed = true },
                onConfirm = { confirmed = it },
            )
        }

        composeTestRule.onNodeWithText(context.getString(R.string.action_cancel)).performClick()

        assertTrue("cancel must dismiss the dialog", dismissed)
        assertEquals(null, confirmed)
    }

    @Test
    fun theDateDialogReturnsTheChosenDay() {
        var confirmed: LocalDate? = null
        show {
            DateTimePickerDialog(
                title = DATE_TITLE,
                initial = LocalDate.of(2026, 9, 25),
                onDismiss = {},
                onConfirm = { confirmed = it },
            )
        }

        composeTestRule.onNodeWithText(DATE_TITLE).assertExists()
        composeTestRule.onNodeWithText(context.getString(R.string.action_done)).performClick()

        assertEquals(LocalDate.of(2026, 9, 25), confirmed)
    }

    /**
     * Переключатель называет режим-назначение (ОВ-120): на циферблате — клавиатуру,
     * в клавиатурном режиме — возврат на циферблат. Диалог открывается циферблатом,
     * и после возврата показывает выбранное время.
     */
    @Test
    fun theSwitchNamesTheModeItLeadsTo() {
        show {
            DateTimePickerDialog(
                title = TIME_TITLE,
                initial = LocalTime.of(8, 0),
                is24Hour = true,
                onDismiss = {},
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithTag(TAG_TIME_PICKER).assertExists()
        composeTestRule.onNodeWithText(context.getString(R.string.cd_switch_time_input)).performClick()

        composeTestRule.onNodeWithTag(TAG_TIME_INPUT).assertExists()
        composeTestRule.onNodeWithText(context.getString(R.string.cd_switch_time_dial)).assertExists()
        composeTestRule.onNodeWithText(context.getString(R.string.cd_switch_time_input)).assertDoesNotExist()

        composeTestRule.onNodeWithText(context.getString(R.string.cd_switch_time_dial)).performClick()

        composeTestRule.onNodeWithTag(TAG_TIME_PICKER).assertExists()
        composeTestRule.onNodeWithText(context.getString(R.string.cd_switch_time_input)).assertExists()
    }

    @Test
    fun switchingTheModeAndConfirmingReturnsTheTime() {
        var confirmed: LocalTime? = null
        show {
            DateTimePickerDialog(
                title = TIME_TITLE,
                initial = LocalTime.of(8, 30),
                is24Hour = true,
                onDismiss = {},
                onConfirm = { confirmed = it },
            )
        }

        composeTestRule.onNodeWithText(context.getString(R.string.cd_switch_time_input)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.action_done)).performClick()

        assertEquals(LocalTime.of(8, 30), confirmed)
    }

    @Test
    fun theTwelveHourDialShowsThePeriodToggle() {
        show {
            DateTimePickerDialog(
                title = TIME_TITLE,
                initial = LocalTime.of(8, 0),
                is24Hour = false,
                onDismiss = {},
                onConfirm = {},
            )
        }

        assertTrue(
            "the 12-hour dial must offer the period toggle",
            composeTestRule.onAllNodesWithText(context.getString(M3_AM_STRING)).fetchSemanticsNodes().isNotEmpty(),
        )
    }

    @Test
    fun theTwentyFourHourDialHasNoPeriodToggle() {
        show {
            DateTimePickerDialog(
                title = TIME_TITLE,
                initial = LocalTime.of(8, 0),
                is24Hour = true,
                onDismiss = {},
                onConfirm = {},
            )
        }

        assertTrue(
            "the 24-hour dial must not offer the period toggle",
            composeTestRule.onAllNodesWithText(context.getString(M3_AM_STRING)).fetchSemanticsNodes().isEmpty(),
        )
    }

    /**
     * V-4, §2.3: затемнение модального слоя рисует приложение — ролью `scrim`, а не чёрной
     * подложкой прошивки; слой закрывает окно целиком и перехватывает нажатия.
     */
    @Test
    fun theDialogDrawsItsOwnScrimAndItClosesTheLayer() {
        var dismissed = false
        show {
            DateTimePickerDialog(
                title = TIME_TITLE,
                initial = LocalTime.of(8, 0),
                is24Hour = true,
                onDismiss = { dismissed = true },
                onConfirm = {},
            )
        }

        composeTestRule.onNodeWithTag(TAG_DIALOG_SCRIM).assertIsDisplayed().assertHasClickAction()
        composeTestRule.onNodeWithTag(TAG_DATE_TIME_PICKER).assertIsDisplayed()
        // Тап по слою вне оболочки: центр слоя закрыт ею же, и нажатие досталось бы
        // оболочке — в углу до слоя доходит именно то, что должен получить фон.
        composeTestRule.onNodeWithTag(TAG_DIALOG_SCRIM).performTouchInput { click(Offset(CLICK_X, CLICK_Y)) }

        assertTrue("тап по затемнению закрывает слой", dismissed)
    }

    private fun show(content: @Composable () -> Unit) {
        composeTestRule.setContent {
            SklyankaTheme { content() }
        }
    }

    private companion object {
        const val TIME_TITLE = "Time"
        const val DATE_TITLE = "Date"

        /** Точка тапа по слою вне оболочки диалога, в пикселях от его верхнего левого угла. */
        const val CLICK_X = 4f
        const val CLICK_Y = 4f

        // Подпись полудня приходит из ресурсов Material3, а не из строк проекта:
        // она показывает, что флаг 12/24 дошёл до механизма пикера.
        val M3_AM_STRING = androidx.compose.material3.R.string.m3c_time_picker_am
    }
}
