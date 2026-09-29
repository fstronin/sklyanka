package ru.stohastika.sklyanka.feature.settings.transfer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.transfer.MatchDecision
import ru.stohastika.sklyanka.domain.transfer.TransferMode
import ru.stohastika.sklyanka.domain.transfer.TransferReject
import ru.stohastika.sklyanka.domain.transfer.TransferRejectReason
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.ui.component.TAG_TEXT_FIELD_ERROR
import ru.stohastika.sklyanka.ui.component.TAG_TEXT_FIELD_INPUT
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Экран «Перенос данных» и экраны, которых нет в макетах и которые собраны из компонентов
 * дизайн-системы (ОВ-67): сводка файла, разбор совпадений, подтверждение замены, отчёт.
 *
 * ПРОГОН НЕ ВЫПОЛНЯЛСЯ: эмулятора и устройства в среде задачи нет. Тест обязан
 * компилироваться (`assembleDebugAndroidTest`), прогон — на эталонном устройстве.
 */
@RunWith(AndroidJUnit4::class)
// Экран длиннее окна телефона: снимок стенда режет его по нижней кромке, и проверка
// состава видела бы не экран, а его верх. Высота окна поднята для прогона (SKL-24).
@Config(qualifiers = "w390dp-h2400dp-mdpi")
class DataTransferScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theScreenStatesThePrincipleAndWarnsAboutTheContentOfTheFile() {
        showOverview(TransferScreenFixtures.overview())

        composeRule.onNodeWithText(context.getString(R.string.transfer_principle)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.transfer_privacy_content)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.transfer_privacy_place)).assertIsDisplayed()
    }

    @Test
    fun theCompositionChipsAreShownAndCarryTheSettingsChip() {
        showOverview(TransferScreenFixtures.overview())

        composeRule.onNodeWithTag(TAG_COMPOSITION).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.transfer_export_chip_settings)).assertIsDisplayed()
    }

    @Test
    fun theDateOfTheLastExportIsShownAsAPlainFact() {
        showOverview(TransferScreenFixtures.overview().copy(lastExportDate = TransferScreenFixtures.LAST_EXPORT))

        composeRule.onNodeWithTag(TAG_LAST_EXPORT).assertIsDisplayed()
    }

    @Test
    fun bothImportModesAreOfferedAndMergeIsTheOneInForce() {
        showOverview(TransferScreenFixtures.overview())

        composeRule.onNodeWithText(context.getString(R.string.transfer_mode_merge)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.transfer_mode_replace)).assertIsDisplayed()
        assertEquals(TransferMode.MERGE, TransferUiState().mode)
    }

    @Test
    fun theNoteAboutPermissionsAndAutostartIsOnTheScreen() {
        showOverview(TransferScreenFixtures.overview())

        composeRule.onNodeWithText(context.getString(R.string.transfer_permissions_note)).assertIsDisplayed()
    }

    @Test
    fun aBrokenFileIsRefusedWithOneLineOfExplanation() {
        showOverview(
            TransferScreenFixtures.overview().copy(
                failure = TransferFailure.NotAnExport(
                    TransferReject(TransferRejectReason.BROKEN_LINK, "intakes.medication_id"),
                ),
            ),
        )

        composeRule.onNodeWithText(context.getString(R.string.transfer_error_not_an_export)).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_FAILURE_REASON).assertIsDisplayed()
    }

    @Test
    fun aFileFromANewerVersionSaysExactlyThat() {
        showOverview(TransferScreenFixtures.overview().copy(failure = TransferFailure.FromFuture))

        composeRule.onNodeWithText(context.getString(R.string.transfer_error_from_future)).assertIsDisplayed()
    }

    @Test
    fun anInterruptedImportIsReportedAtTheNextStart() {
        showOverview(TransferScreenFixtures.overview().copy(importDidNotHappen = true))

        composeRule
            .onNodeWithText(context.getString(R.string.transfer_error_import_interrupted))
            .assertIsDisplayed()
    }

    @Test
    fun theMatchListOffersFourDecisionsPerMatchAndNoBulkAction() {
        val match = TransferScreenFixtures.match()
        showMatches(TransferScreenFixtures.overview().copy(step = TransferStep.MATCHES, matches = listOf(match)))

        composeRule.onNodeWithTag(matchTag(match.file.id)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.transfer_decision_keep_mine)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.transfer_decision_take_from_file)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.transfer_decision_add_separately)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.transfer_decision_do_not_load)).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_DROP_ALL).assertIsDisplayed()
    }

    @Test
    fun aDecisionIsTakenPerMatch() {
        val match = TransferScreenFixtures.match()
        val decisions = mutableListOf<MatchDecision>()
        val state = TransferScreenFixtures.overview().copy(step = TransferStep.MATCHES, matches = listOf(match))

        composeRule.setContent {
            SklyankaTheme {
                MatchReviewScreen(
                    state = state,
                    onIntent = { intent -> if (intent is TransferIntent.Decide) decisions += intent.decision },
                    onCancel = {},
                )
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.transfer_decision_take_from_file)).performClick()

        assertEquals(listOf(MatchDecision.TAKE_FROM_FILE), decisions)
    }

    @Test
    fun theReplacementConfirmationListsTheNumbersOnBothSidesAndTheSafetyCopy() {
        showReplacementConfirmation()

        composeRule.onNodeWithTag(TAG_REPLACE_WARNING).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_COUNTS_NOW).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_COUNTS_AFTER).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SAFETY_COPY).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REPLACE_CONFIRM_ACTION).assertIsDisplayed()
    }

    @Test
    fun theReplacementConfirmationShowsTheWordSample() {
        showReplacementConfirmation()

        val word = context.getString(R.string.transfer_replace_word)
        composeRule
            .onNodeWithText(context.getString(R.string.transfer_replace_word_label, word))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REPLACE_WORD_LABEL).assertIsDisplayed()
    }

    @Test
    fun theReplacementActionStaysUnavailableUntilTheWordMatches() {
        showReplacementConfirmationTyping()

        composeRule.onNodeWithTag(TAG_REPLACE_CONFIRM_ACTION).assertIsNotEnabled()

        composeRule.onNodeWithTag(TAG_TEXT_FIELD_INPUT).performTextReplacement("Udalit")
        composeRule.onNodeWithTag(TAG_REPLACE_CONFIRM_ACTION).assertIsNotEnabled()
        composeRule.onNodeWithTag(TAG_TEXT_FIELD_ERROR).assertDoesNotExist()

        val word = context.getString(R.string.transfer_replace_word)
        composeRule.onNodeWithTag(TAG_TEXT_FIELD_INPUT).performTextReplacement("  ${word.uppercase()}  ")
        composeRule.onNodeWithTag(TAG_REPLACE_CONFIRM_ACTION).assertIsEnabled()
        composeRule.onNodeWithTag(TAG_TEXT_FIELD_ERROR).assertDoesNotExist()
    }

    @Test
    fun theReportNamesTheNumbersAndWhatCouldNotBeTransferred() {
        composeRule.setContent {
            SklyankaTheme {
                TransferReportScreen(
                    report = TransferScreenFixtures.report(),
                    medicationNames = mapOf(TransferScreenFixtures.FILE_MEDICATION_ID to "Eutiroks"),
                    safetyCopyName = TransferScreenFixtures.SAFETY_COPY,
                    onContinue = {},
                )
            }
        }

        composeRule.onNodeWithTag(TAG_REPORT).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_NOT_TRANSFERRED).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_REPORT_CONTINUE).assertIsDisplayed()
    }

    @Test
    fun theSummaryWarnsAboutAFileFromAnOlderFormat() {
        val state = TransferScreenFixtures.overview().copy(
            step = TransferStep.SUMMARY,
            fileCounts = TransferScreenFixtures.FILE_COUNTS,
            fileExportedAt = TransferScreenFixtures.EXPORTED_AT,
            oldFormatVersion = 1,
        )

        composeRule.setContent {
            SklyankaTheme {
                TransferSummaryScreen(
                    counts = TransferScreenFixtures.FILE_COUNTS,
                    state = state,
                    zone = TransferScreenFixtures.ZONE,
                    onContinue = {},
                    onCancel = {},
                )
            }
        }

        composeRule.onNodeWithTag(TAG_OLD_FORMAT).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SUMMARY_CONTINUE).assertIsDisplayed()
    }

    /** Экран подтверждения замены без ввода: кнопка недоступна (ОВ-67). */
    private fun showReplacementConfirmation() {
        composeRule.setContent {
            SklyankaTheme {
                ReplaceConfirmScreen(
                    device = TransferScreenFixtures.DEVICE_COUNTS,
                    file = TransferScreenFixtures.FILE_COUNTS,
                    safetyCopyName = TransferScreenFixtures.SAFETY_COPY,
                    confirmationWord = "",
                    onConfirmationWordChange = {},
                    onConfirm = {},
                    onCancel = {},
                )
            }
        }
    }

    /** Экран подтверждения замены с живым полем: ввод двигает доступность кнопки (ОВ-67). */
    private fun showReplacementConfirmationTyping() {
        composeRule.setContent {
            var word by remember { mutableStateOf("") }
            SklyankaTheme {
                ReplaceConfirmScreen(
                    device = TransferScreenFixtures.DEVICE_COUNTS,
                    file = TransferScreenFixtures.FILE_COUNTS,
                    safetyCopyName = TransferScreenFixtures.SAFETY_COPY,
                    confirmationWord = word,
                    onConfirmationWordChange = { word = it },
                    onConfirm = {},
                    onCancel = {},
                )
            }
        }
    }

    private fun showOverview(state: TransferUiState) {
        composeRule.setContent {
            SklyankaTheme {
                DataTransferScreen(
                    state = state,
                    onIntent = {},
                    onSave = {},
                    onPickFile = {},
                    onBack = {},
                )
            }
        }
    }

    private fun showMatches(state: TransferUiState) {
        composeRule.setContent {
            SklyankaTheme {
                MatchReviewScreen(state = state, onIntent = {}, onCancel = {})
            }
        }
    }
}
