package ru.stohastika.sklyanka.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.history.ReportPreset
import ru.stohastika.sklyanka.ui.component.TAG_DATE_TIME_PICKER
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaType
import ru.stohastika.sklyanka.ui.token.lightColors
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Шторка отчёта врачу: выбор периода, состав, пояснение, исход и два действия (ОВ-125).
 *
 * Здесь проверяется разметка шторки: чипы-пресеты, карточка «подпись — значение»,
 * тихое пояснение о медицинских данных и сообщение исхода. Состав документа проверяет
 * `ReportPdfComposerTest`; тексты шторки собирает та же разметка, что и в продукте.
 *
 * Успех — отдельное состояние содержимого: чипы, карточка, пояснение и действия
 * убираются, остаются фраза, исход словом и декоративная галочка.
 */
@RunWith(AndroidJUnit4::class)
class DoctorReportSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theReportOpensOnTheCurrentMonthWithThePeriodChips() {
        show(HistoryStates.withReport())

        composeRule.onNodeWithTag(TAG_REPORT_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REPORT_CONTENT).assertIsDisplayed()
        REPORT_PRESETS.forEach { label ->
            composeRule.onNodeWithText(context.getString(label), useUnmergedTree = true).assertIsDisplayed()
        }
    }

    /** Состав, а не текст документа: человек видит, что отдаёт, до нажатия «Передать». */
    @Test
    fun theReportShowsTheCompositionOfTheDocument() {
        show(HistoryStates.withReport())

        composeRule.onNodeWithTag(TAG_REPORT_PROFILE, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REPORT_PERIOD, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REPORT_TAKEN, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REPORT_FORMAT, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_report_label_profile), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_report_label_period), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_report_label_taken), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_report_label_format), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithText("PDF", useUnmergedTree = true).assertIsDisplayed()
    }

    /** Пустой период: вместо строки «Принято» — та же фраза, что печатает документ. */
    @Test
    fun theEmptyPeriodSaysThePhraseInsteadOfTheCount() {
        show(
            HistoryStates.withReport(
                composition = HistoryStates.compositionOfEmptyPeriod(),
            ),
        )

        composeRule.onNodeWithTag(TAG_REPORT_EMPTY, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.report_document_empty), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /** Пояснение о данных — тихая подпись под карточкой, а не тревожная плашка. */
    @Test
    fun theDataNoteIsAQuietCaptionBelowTheCard() {
        show(HistoryStates.withReport())

        val note = composeRule.onNodeWithTag(TAG_REPORT_DATA_NOTE, useUnmergedTree = true).fetchSemanticsNode()
        val card = composeRule.onNodeWithTag(TAG_REPORT_CONTENT, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("the note must stand below the card", note.boundsInRoot.top >= card.boundsInRoot.bottom)
        composeRule.onNodeWithText(context.getString(R.string.history_report_data_note), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /** «Другой период» заменяет строку «Период» строками «С» и «По» с шевронами. */
    @Test
    fun theCustomPeriodShowsTheStartAndEndRows() {
        show(HistoryStates.withReport(preset = ReportPreset.CUSTOM))

        composeRule.onNodeWithTag(TAG_REPORT_START, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REPORT_END, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REPORT_PERIOD, useUnmergedTree = true).assertDoesNotExist()
    }

    /**
     * Чип «Другой период» включает режим и показывает строки «С»/«По», а строка открывает
     * диалог даты. Нажатие идёт на сам чип, а не подстановкой состояния: иначе проверка
     * не заметила бы, что чип перестал переключать режим (SKL-101, дефект 2).
     */
    @Test
    fun theCustomChipShowsTheRowsAndARowOpensTheDateDialog() {
        var state by mutableStateOf(HistoryStates.withReport())
        composeRule.setContent {
            SklyankaTheme {
                HistoryScreen(
                    state = state,
                    onIntent = { intent ->
                        if (intent is HistoryIntent.ReportPresetChosen) {
                            state = state.copy(report = requireNotNull(state.report).copy(preset = intent.preset))
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.history_report_period_custom)).performClick()

        composeRule.onNodeWithTag(TAG_REPORT_START, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REPORT_END, useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag(TAG_REPORT_START, useUnmergedTree = true).performClick()
        composeRule.onNodeWithTag(TAG_DATE_TIME_PICKER, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theReportButtonsShareOneFormAndEqualHalves() {
        show(HistoryStates.withReport())

        val share = composeRule.onNodeWithTag(TAG_REPORT_SHARE).fetchSemanticsNode()
        val save = composeRule.onNodeWithTag(TAG_REPORT_SAVE).fetchSemanticsNode()
        val shareHeight = with(composeRule.density) { share.size.height.toDp() }
        val saveHeight = with(composeRule.density) { save.size.height.toDp() }
        val shareWidth = with(composeRule.density) { share.size.width.toDp() }
        val saveWidth = with(composeRule.density) { save.size.width.toDp() }
        assertTrue("the buttons must be one form", shareHeight == saveHeight)
        assertTrue("the buttons must keep the screen height", shareHeight >= SklyankaSizes.PrimaryButtonHeight)
        assertTrue("the buttons must split the row equally: $shareWidth vs $saveWidth", shareWidth == saveWidth)
    }

    @Test
    fun theSaveActionIsCalledSave() {
        show(HistoryStates.withReport())

        assertEquals("Сохранить", context.getString(R.string.history_report_save))
        composeRule.onNodeWithText(context.getString(R.string.history_report_save), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun atTwoHundredPercentTheButtonsStayOneForm() {
        composeRule.setContent {
            Scaled(SCALE_200) {
                SklyankaTheme { HistoryScreen(state = HistoryStates.withReport(), onIntent = {}) }
            }
        }

        val share = composeRule.onNodeWithTag(TAG_REPORT_SHARE).fetchSemanticsNode()
        val save = composeRule.onNodeWithTag(TAG_REPORT_SAVE).fetchSemanticsNode()
        val shareHeight = with(composeRule.density) { share.size.height.toDp() }
        val saveHeight = with(composeRule.density) { save.size.height.toDp() }
        assertTrue("the buttons must stay one form: $shareHeight vs $saveHeight", shareHeight == saveHeight)
        assertTrue("the buttons must not shrink: $shareHeight", shareHeight >= SklyankaSizes.PrimaryButtonHeight)
    }

    @Test
    fun theReportOffersTheSystemExchangeAndAFile() {
        val intents = mutableListOf<HistoryIntent>()
        show(HistoryStates.withReport()) { intents += it }

        composeRule.onNodeWithTag(TAG_REPORT_SHARE).performClick()
        composeRule.onNodeWithTag(TAG_REPORT_SAVE).performClick()

        assertEquals(listOf(HistoryIntent.ShareReport, HistoryIntent.SaveRequested), intents)
    }

    @Test
    fun theReportCanBeClosedWithoutSendingIt() {
        val intents = mutableListOf<HistoryIntent>()
        show(HistoryStates.withReport()) { intents += it }

        composeRule.onNodeWithText(context.getString(UiR.string.action_cancel)).performClick()

        assertEquals(listOf(HistoryIntent.ReportDismissed), intents)
    }

    /** Отмена — нейтральный вид, в самой шторке, а не баннером на «Истории» (ОВ-125). */
    @Test
    fun theCancelledOutcomeIsSaidInline() {
        show(HistoryStates.withReport(outcome = ReportOutcome.CANCELLED))

        composeRule.onNodeWithText(context.getString(R.string.history_report_cancelled), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REPORT_CONTENT).assertIsDisplayed()
    }

    @Test
    fun theFailedShareIsSaidInline() {
        show(HistoryStates.withReport(outcome = ReportOutcome.FAILED_SHARE))

        composeRule.onNodeWithText(context.getString(R.string.history_report_share_failed), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REPORT_CONTENT).assertIsDisplayed()
    }

    /** Успех: содержимое убирается целиком, остаются фраза, исход словом и галочка. */
    @Test
    fun theSuccessReplacesTheContentWithAPhraseAndACheck() {
        show(HistoryStates.withReport(outcome = ReportOutcome.SHARED))

        composeRule.onNodeWithTag(TAG_REPORT_SUCCESS).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.history_report_success_shared), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REPORT_SUCCESS_MARK, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REPORT_CONTENT).assertDoesNotExist()
        composeRule.onNodeWithTag(TAG_REPORT_SHARE).assertDoesNotExist()
        // После успеха «отменять» нечего: тихая кнопка шапки называется «Готово».
        composeRule.onNodeWithText(context.getString(UiR.string.action_done)).assertIsDisplayed()
    }

    /**
     * Выход из успеха — подтверждающее действие шапки, а не отмена: «Готово» набрано
     * `BodyDefaultStrong` 15/600 `Accent` (§4.9, ОВ-146). Иначе серое «Готово» читается
     * как «Отмена» и иерархия действий теряется (дефект визревью 04.10.2026).
     */
    @Test
    fun theSuccessExitIsTheConfirmingRoleOfTheHeader() {
        show(HistoryStates.withReport(outcome = ReportOutcome.SHARED))

        val style = textStyleOf(UiR.string.action_done)
        assertEquals(SklyankaType.BodyDefaultStrong.fontWeight, style.fontWeight)
        assertEquals(SklyankaType.BodyDefaultStrong.fontSize.value, style.fontSize.value, 0.01f)
        assertEquals(lightColors().accent, style.color)
    }

    /** «Отмена» — ведущее приглушённое действие шапки: 15/400 `TextMuted` (§4.9, ОВ-146). */
    @Test
    fun theCancelExitKeepsTheMutedRoleOfTheHeader() {
        show(HistoryStates.withReport())

        val style = textStyleOf(UiR.string.action_cancel)
        assertEquals(SklyankaType.BodyDefault.fontWeight, style.fontWeight)
        assertEquals(SklyankaType.BodyDefault.fontSize.value, style.fontSize.value, 0.01f)
        assertEquals(lightColors().textMuted, style.color)
    }

    @Test
    fun theSavedSuccessNamesTheSave() {
        show(HistoryStates.withReport(outcome = ReportOutcome.SAVED))

        composeRule.onNodeWithText(context.getString(R.string.history_report_success_saved), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /** «Период» — тоже показатель: его числа набраны Literata, как в строке «Принято» (ОВ-125). */
    @Test
    fun thePeriodValueSetsItsNumbersInLiterata() {
        val period = requireNotNull(HistoryStates.withReport().report).composition.period
        show(HistoryStates.withReport())

        assertNumbersUseLiterata(period)
    }

    /** «Другой период»: числа дат «С» и «По» — тем же приёмом, что и «Период». */
    @Test
    fun theCustomPeriodDatesSetTheirNumbersInLiterata() {
        val state = HistoryStates.withReport(preset = ReportPreset.CUSTOM)
        show(state)

        assertNumbersUseLiterata(requireNotNull(state.report).composition.from)
        assertNumbersUseLiterata(requireNotNull(state.report).composition.to)
    }

    private fun assertNumbersUseLiterata(value: String) {
        val text = composeRule.onNodeWithText(value, useUnmergedTree = true)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .single()
        val span = text.spanStyles.firstOrNull { it.item.fontFamily == SklyankaType.NumberSpan.fontFamily }
        assertNotNull("the value \"$value\" must carry a Literata number span", span)
        assertTrue("the number span must cover digits", requireNotNull(span).start < span.end)
    }

    /** Разрешённый стиль подписи: им проверяются роли действия шапки (§4.9, ОВ-146). */
    private fun textStyleOf(text: Int): TextStyle {
        val node = composeRule.onNodeWithText(context.getString(text)).fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        fun collect(current: SemanticsNode) {
            current.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
            current.children.forEach(::collect)
        }
        collect(node)
        return requireNotNull(layouts.firstOrNull()) { "no text layout under the node" }.layoutInput.style
    }

    @Composable
    private fun Scaled(scale: Float, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = scale),
            content = content,
        )
    }

    private fun show(state: HistoryUiState, onIntent: (HistoryIntent) -> Unit = {}) {
        composeRule.setContent {
            SklyankaTheme { HistoryScreen(state = state, onIntent = onIntent) }
        }
    }

    private companion object {
        const val SCALE_200 = 2f

        val REPORT_PRESETS = listOf(
            R.string.history_report_period_this_month,
            R.string.history_report_period_previous_month,
            R.string.history_report_period_last_three_months,
            R.string.history_report_period_custom,
        )
    }
}
