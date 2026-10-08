package ru.stohastika.sklyanka.screen

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import ru.stohastika.sklyanka.feature.settings.TAG_SETTINGS_SCREEN
import ru.stohastika.sklyanka.feature.settings.TAG_SNOOZE_DIALOG
import ru.stohastika.sklyanka.feature.settings.TAG_SNOOZE_ROW
import ru.stohastika.sklyanka.screen.support.DeviceTestSeed
import ru.stohastika.sklyanka.feature.settings.R as SettingsR

/**
 * H — настройки: «Отложить на» (SKL-97, TDD §8.2; ревизия SKL-100).
 *
 * Строка откладывания меняет интервал через диалог и показывает выбранное значение.
 * Строки «Аналитика» в сборке нет (Р-4, SKL-106).
 */
@HiltAndroidTest
class SettingsDeviceTest : DeviceScreenTest() {

    override fun seedDefaultState() {
        DeviceTestSeed.onboardingSeen(targetContext)
    }

    @Test
    fun theSnoozeIntervalIsChangedInSettings() {
        launchApp()
        awaitRoot()
        openSettings()

        scrollTo(TAG_SETTINGS_SCREEN, TAG_SNOOZE_ROW)
        click(TAG_SNOOZE_ROW)
        waitForTag(TAG_SNOOZE_DIALOG)

        val thirtyMinutes = snoozeLabel(THIRTY_MINUTES)
        compose.onNodeWithText(thirtyMinutes, useUnmergedTree = true).performClick()

        waitUntil(ROOT_TIMEOUT_MS, "строки с выбранным интервалом") {
            !exists(TAG_SNOOZE_DIALOG) && textExists(thirtyMinutes)
        }
        capture("settings-snooze")
    }

    private fun snoozeLabel(minutes: Int): String =
        targetContext.resources.getQuantityString(SettingsR.plurals.settings_snooze_minutes, minutes, minutes)

    private companion object {
        const val THIRTY_MINUTES = 30
    }
}
