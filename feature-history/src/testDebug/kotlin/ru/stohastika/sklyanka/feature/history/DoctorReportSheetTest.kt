package ru.stohastika.sklyanka.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Отчёт врачу в шторке: предупреждение, короткий состав и способы передать.
 *
 * Главное здесь — предупреждение (НФТ-18). Файл содержит медицинские данные в открытом
 * виде, и человек обязан прочитать об этом **до** того, как отдаст его: проверка стоит
 * на том, что предупреждение показано вместе с действиями, а не после них.
 *
 * Полного текста документа в шторке нет: документ один — PDF (решение дизайна по ОВ-15),
 * и двух версий одного содержания не остаётся. Человек видит состав.
 */
@RunWith(AndroidJUnit4::class)
class DoctorReportSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theReportOpensWithTheWarningAboutMedicalData() {
        show(HistoryStates.withReport())

        composeRule.onNodeWithTag(TAG_REPORT_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REPORT_WARNING).assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.history_report_warning), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    /** Состав, а не текст документа: человек видит, что отдаёт, до нажатия «Передать». */
    @Test
    fun theReportShowsTheCompositionOfTheDocument() {
        show(HistoryStates.withReport(summary = SUMMARY))

        composeRule.onNodeWithText(SUMMARY, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theCompositionIsACardWithTheProfilePeriodCountAndFormat() {
        show(HistoryStates.withReport())

        composeRule.onNodeWithTag(TAG_REPORT_CONTENT).assertIsDisplayed()
        composeRule.onNodeWithText("Профиль: Я", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Период: 1 — 30 сентября 2026", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Принято: 2 из 3 приёмов", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Формат: PDF", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theWarningStandsAboveTheCompositionCard() {
        show(HistoryStates.withReport())

        val warning = composeRule.onNodeWithTag(TAG_REPORT_WARNING).fetchSemanticsNode()
        val card = composeRule.onNodeWithTag(TAG_REPORT_CONTENT).fetchSemanticsNode()
        assertTrue(
            "the warning must stand above the composition card",
            warning.boundsInRoot.top < card.boundsInRoot.top,
        )
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
        val shareWidth = with(composeRule.density) { share.size.width.toDp() }
        val saveWidth = with(composeRule.density) { save.size.width.toDp() }
        assertTrue("the buttons must stay one form: $shareHeight vs $saveHeight", shareHeight == saveHeight)
        assertTrue("the buttons must not shrink: $shareHeight", shareHeight >= SklyankaSizes.PrimaryButtonHeight)
        assertTrue("the buttons must stay equal: $shareWidth vs $saveWidth", shareWidth == saveWidth)
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

    /** Исход передачи сказан человеку: отмена системного диалога — не молчание. */
    @Test
    fun theOutcomeOfTheTransferIsSaidOutLoud() {
        show(HistoryStates.september().copy(notice = HistoryNotice.REPORT_CANCELLED))

        composeRule.onNodeWithTag(TAG_NOTICE).assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.history_report_cancelled), useUnmergedTree = true)
            .assertIsDisplayed()
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
        const val SUMMARY = "report-composition"
        const val SCALE_200 = 2f
    }
}
