package ru.stohastika.sklyanka.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN
import ru.stohastika.sklyanka.screen.support.DeviceTestClock
import ru.stohastika.sklyanka.test.R
import ru.stohastika.sklyanka.ui.component.TAG_BOTTOM_BAR

/**
 * «Сегодня» на настоящем устройстве (SKL-96, переработано SKL-97, TDD §9).
 *
 * Проверка сеет собственный приём на управляемом времени: наполнение дня не зависит
 * от данных образа, а «сейчас» — от часов эмулятора.
 */
@HiltAndroidTest
class TodayScreenDeviceTest : DeviceScreenTest() {

    @Test
    fun theTodayScreenOpensOnDevice() {
        val name = testContext.getString(R.string.device_test_medication_name, "today")
        seedMedication(id = "dev-med-today", name = name, times = listOf(DeviceTestClock.scheduledTime))
        planIntakes()

        launchApp()
        awaitRoot()

        compose.onNodeWithTag(TAG_TODAY_SCREEN, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(TAG_BOTTOM_BAR, useUnmergedTree = true).assertIsDisplayed()
        waitUntil(SCREEN_TIMEOUT_MS, "приёма «$name» в дне") { textExists(name) }

        capture("today")
    }
}
