package ru.stohastika.sklyanka.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.ui.component.TAG_SNACKBAR
import ru.stohastika.sklyanka.feature.today.R as TodayR
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * B — отметка приёма и живое окно отмены (SKL-97; разделено SKL-98, находка 38).
 *
 * Автоскрытие снэкбара идёт реальным `delay` от управляемого момента, поэтому кадр
 * отмеченного состояния и отмена — разные проверки: между отметкой и отменой съёмки
 * нет, и гонка «кадр против автоскрытия окна» невозможна.
 */
@HiltAndroidTest
class IntakeMarkDeviceTest : DeviceScreenTest() {

    private val name: String get() = testContext.getString(R.string.device_test_medication_due)

    @Test
    fun theTakenIntakeShowsTheUndoWindow() {
        seedDueIntake()
        launchApp()
        awaitRoot()
        waitUntil(SCREEN_TIMEOUT_MS, "строки приёма «$name»") { textExists(name) }

        markTaken()

        waitForTag(TAG_SNACKBAR).assertIsDisplayed()
        waitUntil(SCREEN_TIMEOUT_MS, "действия отмены") { textExists(undoLabel()) }
        capture("intake-marked")
    }

    @Test
    fun theUndoReturnsTheIntakeToPending() {
        seedDueIntake()
        launchApp()
        awaitRoot()
        waitUntil(SCREEN_TIMEOUT_MS, "строки приёма «$name»") { textExists(name) }

        markTaken()

        waitForTag(TAG_SNACKBAR)
        compose.onNodeWithText(undoLabel(), useUnmergedTree = true).performClick()

        waitUntil(SCREEN_TIMEOUT_MS, "исчезновения окна отмены") { !exists(TAG_SNACKBAR) }
        waitUntil(SCREEN_TIMEOUT_MS, "возврата приёма в ожидание") { textExists(takenLabel()) }
        capture("intake-undone")
    }

    private fun seedDueIntake() {
        seedMedication(id = "dev-med-mark", name = name, times = listOf(DeviceTestClock.scheduledTime))
        planIntakes()
    }

    private fun markTaken() {
        compose.onNodeWithText(takenLabel(), useUnmergedTree = true).performClick()
    }

    private fun takenLabel(): String = targetContext.getString(UiR.string.action_taken)

    private fun undoLabel(): String = targetContext.getString(TodayR.string.today_undo_action)
}
