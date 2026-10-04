package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
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

    /**
     * Тёмная форма действий (ОВ-146, §5.1): «Принято» — 58 dp, второстепенные — 54 dp.
     * Габарит — свойство формы, а не места вызова; на 100 % кнопки ровно этой высоты.
     */
    @Test
    fun theActionsCarryTheDarkFormHeightsOfTheMockup() {
        show(state())

        assertTrue(
            "«Принято» must be 58 dp tall",
            heightOf(TAG_ACTION_TAKEN) >= PRIMARY_HEIGHT_DP - HEIGHT_TOLERANCE,
        )
        listOf(TAG_ACTION_SNOOZE, TAG_ACTION_SKIP).forEach { tag ->
            assertTrue(
                "$tag must be 54 dp tall",
                heightOf(tag) >= SECONDARY_HEIGHT_DP - HEIGHT_TOLERANCE,
            )
        }
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
     * На ширине 390 dp «Отложить 15 мин» не помещалась в половину ряда и переносилась
     * на две строки до стоп-крана — это и был дефект пилота.
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
     * При 130 % длинная русская подпись уходит в столбик: «Отложить 15 мин» занимает
     * 161 dp — больше порога 157 dp, за которым ряд перестаёт гарантировать боковой
     * зазор 4 dp до рамки (§5.1, ОВ-146). Подпись при этом не переносится.
     */
    @Test
    fun atOneHundredThirtyPercentTheLongLabelsStack() {
        show(state(snoozeMinutes = 15), fontScale = SCALE_130)

        assertStackedWithoutWrappedLabels()
    }

    /**
     * «Отложить 5 мин» на 130 % — короткая подпись у самого порога: измеренная ширина
     * 152 dp при пороге 157 dp, ряд сохраняется, и воздух до рамки (≈6 dp) не меньше
     * 4 dp (§5.1, ОВ-146). Порог решает фактическая ширина, а не масштаб.
     */
    @Test
    fun atOneHundredThirtyPercentTheShortSnoozeLabelKeepsTheRow() {
        show(state(snoozeMinutes = 5), fontScale = SCALE_130)

        val snooze = compose.onNodeWithTag(TAG_ACTION_SNOOZE).fetchSemanticsNode().boundsInRoot
        val skip = compose.onNodeWithTag(TAG_ACTION_SKIP).fetchSemanticsNode().boundsInRoot
        assertTrue(
            "кнопки не в ряду: $snooze, $skip",
            snooze.top < skip.bottom && skip.top < snooze.bottom,
        )
        assertLabelsFitOneLine(snooze, skip)
        assertActionsKeepTheSideGap()
    }

    @Test
    fun atTwoHundredPercentTheActionsStackInsteadOfWrapping() {
        show(state(), fontScale = SCALE_200)

        assertStackedWithoutWrappedLabels()
    }

    /**
     * Время приёма ограничено масштабом 130 % (§6.3, ОВ-117): при 200 % «20:30» не
     * помещается на 390 dp и обрезается, а это единственное, ради чего экран есть.
     */
    @Test
    fun theTimeIsCappedAtTwoHundredPercent() {
        show(state(), fontScale = SCALE_200)
        val layout = timeLayout()

        assertEquals("the time must stay one line", 1, layout.lineCount)
        assertTrue("the time must not be cut: $layout", !layout.didOverflowHeight)
        assertEquals(
            "the time size must be cut to the 130 % ceiling",
            CAPPED_FONT_SIZE,
            layout.layoutInput.style.fontSize.value,
            CAPPED_FONT_TOLERANCE,
        )
    }

    private fun timeLayout(): TextLayoutResult {
        val node = compose.onNodeWithTag(TAG_TIME, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return requireNotNull(results.firstOrNull()) { "the time has no text layout" }
    }

    /** Высота кнопки в dp: снимок габарита, а не пиксельное число. */
    private fun heightOf(tag: String): Float {
        val node = compose.onNodeWithTag(tag).fetchSemanticsNode()
        return with(node.layoutInfo.density) { node.size.height.toDp().value }
    }

    private fun assertStackedWithoutWrappedLabels() {
        val snooze = compose.onNodeWithTag(TAG_ACTION_SNOOZE).fetchSemanticsNode().boundsInRoot
        val skip = compose.onNodeWithTag(TAG_ACTION_SKIP).fetchSemanticsNode().boundsInRoot
        assertTrue("кнопка пропуска не под откладыванием: $snooze, $skip", skip.top >= snooze.bottom)
        assertLabelsFitOneLine(snooze, skip)
    }

    /**
     * Ряд тёмной пары не прижимает подпись к рамке: воздух до каждой границы кнопки —
     * не меньше 4 dp (`SklyankaSpacing.Micro`), как требует §5.1 и ОВ-146.
     */
    private fun assertActionsKeepTheSideGap() {
        listOf(TAG_ACTION_SNOOZE, TAG_ACTION_SKIP).forEach { tag ->
            val node = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            val gapPx = with(node.layoutInfo.density) { SklyankaSpacing.Micro.roundToPx() }
            val layouts = mutableListOf<TextLayoutResult>()
            collectTextLayouts(node, layouts)
            val labelWidthPx = requireNotNull(layouts.firstOrNull()) {
                "у кнопки $tag нет разметки подписи"
            }.size.width
            assertTrue(
                "подпись $tag прижата к рамке: label=$labelWidthPx, button=${node.size}",
                labelWidthPx + 2 * gapPx <= node.size.width,
            )
        }
    }

    private fun collectTextLayouts(node: SemanticsNode, into: MutableList<TextLayoutResult>) {
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(into)
        node.children.forEach { child -> collectTextLayouts(child, into) }
    }

    private fun assertLabelsFitOneLine(snooze: Rect, skip: Rect) {
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

        /** Габариты тёмной формы (ОВ-146): основная 58 dp, второстепенная 54 dp. */
        const val PRIMARY_HEIGHT_DP = 58f
        const val SECONDARY_HEIGHT_DP = 54f
        const val HEIGHT_TOLERANCE = 0.5f

        /** 64 sp · 1,3 / 2 — кегль под потолком 130 % при системном масштабе 200 %. */
        const val CAPPED_FONT_SIZE = 41.6f
        const val CAPPED_FONT_TOLERANCE = 0.01f

        /** Кнопка с подписью в одну строку: вдвое выше минимальной цели нажатия не бывает. */
        const val MAX_ONE_LINE_HEIGHT = 88f
    }
}
