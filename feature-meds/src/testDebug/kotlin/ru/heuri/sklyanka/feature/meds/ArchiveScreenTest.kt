package ru.heuri.sklyanka.feature.meds

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.heuri.sklyanka.domain.medication.ArchiveCaption
import ru.heuri.sklyanka.domain.medication.ArchiveCard
import ru.heuri.sklyanka.domain.medication.ArchiveContent
import ru.heuri.sklyanka.domain.model.MedicationForm
import ru.heuri.sklyanka.feature.meds.archive.ArchiveScreen
import ru.heuri.sklyanka.feature.meds.archive.ArchiveUiState
import ru.heuri.sklyanka.feature.meds.archive.TAG_ARCHIVE_CARD
import ru.heuri.sklyanka.feature.meds.archive.TAG_ARCHIVE_EMPTY
import ru.heuri.sklyanka.feature.meds.archive.TAG_ARCHIVE_RESTORE
import ru.heuri.sklyanka.feature.meds.archive.TAG_GROUP_FINISHED
import ru.heuri.sklyanka.feature.meds.archive.TAG_GROUP_MANUAL
import ru.heuri.sklyanka.feature.meds.archive.TAG_HISTORY_BANNER
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalDate

/**
 * Экран «Архив»: две группы, подписи карточек, плашка о сохранности истории, пустой архив.
 *
 * ПРОГОН НЕ ВЫПОЛНЯЛСЯ: эмулятора и устройства в среде задачи нет. Тест обязан
 * компилироваться (`assembleDebugAndroidTest`), прогон — на эталонном устройстве.
 */
@RunWith(AndroidJUnit4::class)
class ArchiveScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theHeaderStaysTheMedicationsScreenAndTheArchiveSegmentIsSelected() {
        show(filledArchive())

        composeRule.onNodeWithText(context.getString(R.string.meds_title)).assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.meds_tab_archive, ARCHIVE_COUNT), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.meds_tab_active, ACTIVE_COUNT), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun bothGroupsCarryATextHeading() {
        show(filledArchive())

        composeRule.onNodeWithTag(TAG_GROUP_FINISHED, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_GROUP_MANUAL, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aFinishedCourseIsCaptionedWithItsOutcome() {
        show(filledArchive())

        composeRule
            .onNodeWithText(
                context.resources.getQuantityString(R.plurals.archive_outcome, DAYS, DAYS, TAKEN, PLANNED),
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
    }

    @Test
    fun aManuallyRemovedCourseIsCaptionedWithItsDate() {
        show(filledArchive())

        composeRule.onAllNodesWithTag(TAG_ARCHIVE_CARD).assertCountEquals(2)
    }

    @Test
    fun everyCardCarriesTheRestoreAction() {
        show(filledArchive())

        composeRule.onAllNodesWithTag(TAG_ARCHIVE_RESTORE, useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun theArchivePromisesThatTheHistorySurvives() {
        show(filledArchive())

        composeRule.onNodeWithTag(TAG_HISTORY_BANNER, useUnmergedTree = true).assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.archive_history_banner), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun anEmptyGroupHidesItsHeadingCompletely() {
        show(
            ArchiveUiState(
                activeCount = ACTIVE_COUNT,
                archiveCount = 1,
                content = ArchiveContent(finished = listOf(finishedCard()), manual = emptyList(), total = 1),
                loading = false,
            ),
        )

        composeRule.onNodeWithTag(TAG_GROUP_FINISHED, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onAllNodesWithTag(TAG_GROUP_MANUAL, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun anEmptyArchiveKeepsTheSegmentAndExplainsItself() {
        show(ArchiveUiState(activeCount = ACTIVE_COUNT, archiveCount = 0, content = null, loading = false))

        composeRule.onNodeWithTag(TAG_ARCHIVE_EMPTY, useUnmergedTree = true).assertIsDisplayed()
        composeRule
            .onNodeWithText(context.getString(R.string.meds_tab_archive, 0), useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag(TAG_ARCHIVE_CARD).assertCountEquals(0)
    }

    private fun show(state: ArchiveUiState) {
        composeRule.setContent {
            SklyankaTheme { ArchiveScreen(state = state, onIntent = {}) }
        }
    }

    private fun filledArchive() = ArchiveUiState(
        activeCount = ACTIVE_COUNT,
        archiveCount = ARCHIVE_COUNT,
        content = ArchiveContent(
            finished = listOf(finishedCard()),
            manual = listOf(manualCard()),
            total = ARCHIVE_COUNT,
        ),
        loading = false,
    )

    private fun finishedCard() = ArchiveCard(
        medicationId = "finished-1",
        title = "med-a",
        form = MedicationForm.TABLET,
        caption = ArchiveCaption.Outcome(days = DAYS, taken = TAKEN, planned = PLANNED),
    )

    private fun manualCard() = ArchiveCard(
        medicationId = "manual-1",
        title = "med-b",
        form = MedicationForm.SPRAY,
        caption = ArchiveCaption.ArchivedOn(LocalDate.of(2026, 8, 3)),
    )

    private companion object {
        const val ACTIVE_COUNT = 5
        const val ARCHIVE_COUNT = 12
        const val DAYS = 7
        const val TAKEN = 13
        const val PLANNED = 14
    }
}
