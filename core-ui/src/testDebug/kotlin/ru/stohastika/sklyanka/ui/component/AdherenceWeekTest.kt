package ru.stohastika.sklyanka.ui.component

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Недельная полоса приёмов: семь столбиков, легенда, различимость состояний без цвета.
 *
 * ПРОГОН НЕ ВЫПОЛНЯЛСЯ: эмулятора и устройства в среде задачи нет. Тест обязан
 * компилироваться (`assembleDebugAndroidTest`), прогон — на эталонном устройстве.
 */
@RunWith(AndroidJUnit4::class)
class AdherenceWeekTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theStripDrawsSevenDaysAndTheLegend() {
        show()

        composeRule.onNodeWithTag(TAG_ADHERENCE_WEEK).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TAG_ADHERENCE_DAY, useUnmergedTree = true).assertCountEquals(7)
        composeRule.onNodeWithTag(TAG_ADHERENCE_LEGEND, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theLegendNamesEveryStateInWords() {
        show()

        listOf(
            R.string.adherence_legend_taken,
            R.string.adherence_legend_missed,
            R.string.adherence_legend_today,
        ).forEach { label ->
            composeRule.onNodeWithText(context.getString(label), useUnmergedTree = true).assertIsDisplayed()
        }
    }

    /** НФТ-33: принятый и пропущенный день различимы без восприятия цвета. */
    @Test
    fun everyDayCarriesItsStateInText() {
        show()

        listOf(TAKEN_DESCRIPTION, MISSED_DESCRIPTION, TODAY_DESCRIPTION).forEach { description ->
            composeRule.onNodeWithContentDescription(description, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    /**
     * V-1: неделя без завершённых дней с исходом сжимается — ряда, кружка «сегодня»
     * и легенды нет вовсе.
     */
    @Test
    fun anEmptyWeekCollapsesToNothing() {
        composeRule.setContent {
            SklyankaTheme {
                AdherenceWeek(
                    days = listOf(
                        AdherenceDay("1", AdherenceDayState.Empty, "day-1"),
                        AdherenceDay("2", AdherenceDayState.Empty, "day-2"),
                        AdherenceDay("3", AdherenceDayState.Today, "day-3"),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(TAG_ADHERENCE_WEEK).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_ADHERENCE_LEGEND, useUnmergedTree = true).assertDoesNotExist()
    }

    /** День без приёмов отмечен точкой: размер — третий носитель смысла помимо цвета (§5.2). */
    @Test
    fun theDayWithoutIntakesCarriesADot() {
        composeRule.setContent {
            SklyankaTheme {
                AdherenceWeek(days = week())
            }
        }

        composeRule.onNodeWithTag(TAG_ADHERENCE_EMPTY_DOT, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun show() {
        composeRule.setContent {
            SklyankaTheme { AdherenceWeek(days = week()) }
        }
    }

    /**
     * Неделя из всех состояний сразу.
     *
     * Подписи латиницей: компонент их не переводит и не сочиняет — он получает готовый
     * текст от экрана (НФТ-42), и тест обязан проверять именно это.
     */
    private fun week(): List<AdherenceDay> = listOf(
        AdherenceDay(label = "1", state = AdherenceDayState.Taken, description = TAKEN_DESCRIPTION),
        AdherenceDay(label = "2", state = AdherenceDayState.Taken, description = "day-2"),
        AdherenceDay(label = "3", state = AdherenceDayState.Missed, description = MISSED_DESCRIPTION),
        AdherenceDay(label = "4", state = AdherenceDayState.Partial, description = "day-4"),
        AdherenceDay(label = "5", state = AdherenceDayState.Empty, description = "day-5"),
        AdherenceDay(label = "6", state = AdherenceDayState.Taken, description = "day-6"),
        AdherenceDay(label = "7", state = AdherenceDayState.Today, description = TODAY_DESCRIPTION),
    )

    private companion object {
        const val TAKEN_DESCRIPTION = "day-1-taken"
        const val MISSED_DESCRIPTION = "day-3-missed"
        const val TODAY_DESCRIPTION = "day-7-today"
    }
}
