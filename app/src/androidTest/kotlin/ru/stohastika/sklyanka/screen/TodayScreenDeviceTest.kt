package ru.stohastika.sklyanka.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Test
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN
import ru.stohastika.sklyanka.ui.component.TAG_BOTTOM_BAR

/**
 * «Сегодня» на настоящем устройстве (SKL-96, TDD §4.4).
 *
 * Первая волна не утверждает наполнение дня: база образа может быть любой. Проверяется
 * то, что Robolectric рисует иначе, чем платформа: настоящий первый экран с реальным
 * окном, вставками и нижней панелью.
 */
class TodayScreenDeviceTest : DeviceScreenTest() {

    @Test
    fun theTodayScreenOpensOnDevice() {
        awaitRoot()

        compose.onNodeWithTag(TAG_TODAY_SCREEN, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(TAG_BOTTOM_BAR, useUnmergedTree = true).assertIsDisplayed()

        capture("today")
    }
}
