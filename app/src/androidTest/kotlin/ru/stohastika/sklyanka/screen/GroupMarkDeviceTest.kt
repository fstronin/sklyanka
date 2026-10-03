package ru.stohastika.sklyanka.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import ru.stohastika.sklyanka.feature.today.TAG_GROUP_MARK_ALL
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.ui.component.TAG_SNACKBAR
import ru.stohastika.sklyanka.feature.today.R as TodayR

/**
 * C — групповая отметка с подтверждением (SKL-97, TDD §8.2).
 *
 * Три лекарства на одно время: батч-кнопка сначала спрашивает подтверждение словом,
 * и только второе нажатие отмечает группу. До и после — кадры состояния.
 */
@HiltAndroidTest
class GroupMarkDeviceTest : DeviceScreenTest() {

    @Test
    fun theGroupMarkRequiresConfirmation() {
        repeat(GROUP_SIZE) { index ->
            seedMedication(
                id = "dev-med-group-$index",
                name = testContext.getString(R.string.device_test_group_medication, index + 1),
                times = listOf(DeviceTestClock.scheduledTime),
            )
        }
        planIntakes()

        launchApp()
        awaitRoot()

        val group = waitForTag(TAG_GROUP_MARK_ALL)
        group.performScrollTo()
        group.performClick()

        val confirmation = targetContext.getString(TodayR.string.group_confirm_all_three)
        waitUntil(SCREEN_TIMEOUT_MS, "подтверждения групповой отметки") { textExists(confirmation) }
        capture("group-armed")

        waitForTag(TAG_GROUP_MARK_ALL).performClick()
        waitForTag(TAG_SNACKBAR).assertIsDisplayed()
        capture("group-marked")
    }

    private companion object {
        const val GROUP_SIZE = 3
    }
}
