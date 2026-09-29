package ru.stohastika.sklyanka.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
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

    private fun show(state: HistoryUiState, onIntent: (HistoryIntent) -> Unit = {}) {
        composeRule.setContent {
            SklyankaTheme { HistoryScreen(state = state, onIntent = onIntent) }
        }
    }

    private companion object {
        const val SUMMARY = "report-composition"
    }
}
