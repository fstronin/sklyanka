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
 * B — отметка приёма с отменой (SKL-97, TDD §8.2).
 *
 * Проверка сеет ожидающийся приём на управляемом времени и повторяет путь человека:
 * «Принято» → живое окно отмены → отмена возвращает приём в ожидание. Часы внутри
 * проверки не переводятся, окно отмены живёт реальные секунды — нажатие сразу после
 * утверждения снэкбара.
 */
@HiltAndroidTest
class IntakeMarkDeviceTest : DeviceScreenTest() {

    @Test
    fun theTakenIntakeIsUndoneFromTheSnackbar() {
        val name = testContext.getString(R.string.device_test_medication_due)
        seedMedication(id = "dev-med-mark", name = name, times = listOf(DeviceTestClock.scheduledTime))
        planIntakes()

        launchApp()
        awaitRoot()
        waitUntil(SCREEN_TIMEOUT_MS, "строки приёма «$name»") { textExists(name) }

        val taken = targetContext.getString(UiR.string.action_taken)
        compose.onNodeWithText(taken, useUnmergedTree = true).performClick()

        waitForTag(TAG_SNACKBAR).assertIsDisplayed()
        capture("intake-marked")

        compose.onNodeWithText(targetContext.getString(TodayR.string.today_undo_action), useUnmergedTree = true)
            .performClick()

        waitUntil(SCREEN_TIMEOUT_MS, "исчезновения окна отмены") { !exists(TAG_SNACKBAR) }
        waitUntil(SCREEN_TIMEOUT_MS, "возврата приёма в ожидание") { textExists(taken) }
        capture("intake-undone")
    }
}
