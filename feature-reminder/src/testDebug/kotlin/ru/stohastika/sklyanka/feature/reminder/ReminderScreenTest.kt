package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Состав полноэкранного напоминания (НАП-29, НАП-30).
 *
 * Прогон требует устройства или эмулятора; в контейнере сборки тест компилируется,
 * но не выполняется — поэтому состав карточки остаётся непроверенным до прогона
 * на эталонном устройстве.
 */
@RunWith(AndroidJUnit4::class)
class ReminderScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val at: LocalDateTime = LocalDateTime.of(2026, 9, 25, 14, 0)

    private fun row(id: String = "intake-1", name: String = "Магний B6", strengthUnit: String = "мг") = ReminderRow(
        intakeId = id,
        name = name,
        form = MedicationForm.TABLET,
        doseAmount = BigDecimal("2"),
        strength = BigDecimal("48"),
        strengthUnit = strengthUnit,
    )

    private fun state(rows: List<ReminderRow> = listOf(row()), snoozeMinutes: Int = 15, mirrored: Boolean = false) =
        ReminderUiState.Card(
            scheduledAt = at,
            items = rows,
            snoozeMinutes = snoozeMinutes,
            mirroredToWatch = mirrored,
            foreignProfileName = null,
        )

    private fun show(state: ReminderUiState.Card, fontScale: Float = 1f, onSnooze: () -> Unit = {}) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                SklyankaTheme(dark = true) {
                    ReminderScreen(
                        state = state,
                        actions = ReminderActions(
                            onTaken = {},
                            onSkipped = {},
                            onTakenAll = {},
                            onSkippedAll = {},
                            onSnooze = onSnooze,
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun theCardCarriesTheOverlineTimeNameAndDose() {
        show(state())

        compose.onNodeWithTag(TAG_OVERLINE).assertIsDisplayed()
        compose.onNodeWithText("14:00").assertIsDisplayed()
        compose.onNodeWithText("Магний B6").assertIsDisplayed()
        compose.onNodeWithText("2 таблетки · 48 мг").assertIsDisplayed()
    }

    /**
     * Канонический код «mg» показывается словом языка, как в списке и форме
     * (находка визревью SKL-91): латиница в русской карточке читалась чужим форматом.
     */
    @Test
    fun theMilligramCodeIsShownInTheInterfaceLanguage() {
        show(state(rows = listOf(row(strengthUnit = "mg"))))

        compose.onNodeWithText("2 таблетки · 48 мг").assertIsDisplayed()
    }

    /**
     * Плашки «Продублировано на часы» на экране нет — отдельной проверкой.
     *
     * Это решение `ADR-0009` (интеграция с Wear Engine отложена до пост-MVP), а не
     * упущение: состояние намеренно говорит «карточка ушла на часы», и всё равно
     * плашки быть не должно. Без теста она однажды вернётся сама.
     */
    @Test
    fun theWatchChipIsNotPartOfTheScreen() {
        show(state(mirrored = true))

        compose.onAllNodesWithTag(TAG_WATCH_CHIP).assertCountEquals(0)
        compose.onNodeWithText("Магний B6").assertIsDisplayed()
    }

    @Test
    fun thereAreExactlyThreeActionsInTheOrderOfTheMockup() {
        show(state())

        compose.onNodeWithText("Принято").assertIsDisplayed()
        compose.onNodeWithText("Отложить 15 мин").assertIsDisplayed()
        compose.onNodeWithText("Пропустить").assertIsDisplayed()
    }

    @Test
    fun theSnoozeLabelFollowsTheSetting() {
        show(state(snoozeMinutes = 30))

        compose.onNodeWithText("Отложить 30 мин").assertIsDisplayed()
    }

    @Test
    fun theSnoozeButtonReportsItsClick() {
        var clicks = 0
        show(state()) { clicks++ }

        compose.onNodeWithTag(TAG_ACTION_SNOOZE).performClick()

        assertEquals(1, clicks)
    }

    /**
     * Подписи действий не переносятся и не перекрываются независимо от раскладки:
     * помещаются рядом — стоят рядом, не помещаются — встают друг под друга.
     *
     * На ширине 390 dp «Отложить 15 мин» не помещается в половину строки и до задачи
     * переносилась на две строки — это и был дефект пилота.
     */
    @Test
    fun theActionsNeverWrapTheirLabelsAtTheNormalFont() {
        show(state())

        val snooze = compose.onNodeWithTag(TAG_ACTION_SNOOZE).fetchSemanticsNode().boundsInRoot
        val skip = compose.onNodeWithTag(TAG_ACTION_SKIP).fetchSemanticsNode().boundsInRoot
        assertTrue("подпись откладывания переносится: $snooze", snooze.height <= MAX_ONE_LINE_HEIGHT)
        assertTrue("подпись пропуска переносится: $skip", skip.height <= MAX_ONE_LINE_HEIGHT)
        assertTrue(
            "кнопки перекрываются: $snooze, $skip",
            skip.top >= snooze.bottom || skip.left >= snooze.right,
        )
    }

    /**
     * При крупном шрифте кнопки встают друг под друга во всю ширину, и подпись
     * не переносится (SKL-61, §6.3). Ряд, сжатый до половин, разрывал «Отложить 15 мин».
     */
    @Test
    fun atOneHundredThirtyPercentTheActionsStackInsteadOfWrapping() {
        show(state(), fontScale = SCALE_130)

        assertStackedWithoutWrappedLabels()
    }

    @Test
    fun atTwoHundredPercentTheActionsStackInsteadOfWrapping() {
        show(state(), fontScale = SCALE_200)

        assertStackedWithoutWrappedLabels()
    }

    private fun assertStackedWithoutWrappedLabels() {
        val snooze = compose.onNodeWithTag(TAG_ACTION_SNOOZE).fetchSemanticsNode().boundsInRoot
        val skip = compose.onNodeWithTag(TAG_ACTION_SKIP).fetchSemanticsNode().boundsInRoot
        assertTrue("кнопка пропуска не под откладыванием: $snooze, $skip", skip.top >= snooze.bottom)
        assertTrue("подпись откладывания переносится: $snooze", snooze.height <= MAX_ONE_LINE_HEIGHT)
        assertTrue("подпись пропуска переносится: $skip", skip.height <= MAX_ONE_LINE_HEIGHT)
    }

    @Test
    fun twoIntakesOnOneTimeAreShownAsOneCardWithTwoRows() {
        show(state(rows = listOf(row(), row(id = "intake-2", name = "Витамин D3"))))

        compose.onAllNodesWithTag(TAG_ROW).assertCountEquals(2)
        compose.onNodeWithText("Магний B6").assertIsDisplayed()
        compose.onNodeWithText("Витамин D3").assertIsDisplayed()
    }

    private companion object {

        /** Плашки часов на экране нет: тега тоже нет ни в одном компоненте (`ADR-0009`). */
        const val TAG_WATCH_CHIP = "reminder_watch_chip"

        const val SCALE_130 = 1.3f
        const val SCALE_200 = 2.0f

        /** Кнопка с подписью в одну строку: вдвое выше минимальной цели нажатия не бывает. */
        const val MAX_ONE_LINE_HEIGHT = 88f
    }
}
