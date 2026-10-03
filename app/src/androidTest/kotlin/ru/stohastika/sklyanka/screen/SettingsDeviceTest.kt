package ru.stohastika.sklyanka.screen

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentDecision
import ru.stohastika.sklyanka.feature.onboarding.consent.TAG_CONSENT_SCREEN
import ru.stohastika.sklyanka.feature.settings.TAG_ANALYTICS_ROW
import ru.stohastika.sklyanka.feature.settings.TAG_SETTINGS_SCREEN
import ru.stohastika.sklyanka.feature.settings.TAG_SNOOZE_DIALOG
import ru.stohastika.sklyanka.feature.settings.TAG_SNOOZE_ROW
import ru.stohastika.sklyanka.screen.support.DeviceTestSeed
import ru.stohastika.sklyanka.ui.component.TAG_PRIMARY_BUTTON
import ru.stohastika.sklyanka.feature.onboarding.R as OnboardingR
import ru.stohastika.sklyanka.feature.settings.R as SettingsR

/**
 * H — настройки: «Отложить на» и согласие из настроек (SKL-97, TDD §8.2).
 *
 * Строка откладывания меняет интервал через диалог и показывает выбранное значение.
 * Переключатель аналитики при отказе открывает экран согласия источником «настройки»,
 * а после разрешения возвращает в настройки с включённым переключателем.
 *
 * Отказ сеется до запуска: включение аналитики обязано спросить согласие заново.
 */
@HiltAndroidTest
class SettingsDeviceTest : DeviceScreenTest() {

    override fun seedDefaultState() {
        DeviceTestSeed.onboardingSeen(targetContext)
        DeviceTestSeed.consent(targetContext, AnalyticsConsentDecision.DECLINED)
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

    @Test
    fun analyticsTurnsOnThroughConsentFromSettings() {
        launchApp()
        awaitRoot()
        openSettings()

        scrollTo(TAG_SETTINGS_SCREEN, TAG_ANALYTICS_ROW)
        click(TAG_ANALYTICS_ROW)

        waitForTag(TAG_CONSENT_SCREEN)
        val settingsDecline = targetContext.getString(OnboardingR.string.consent_decline_settings)
        waitUntil(SCREEN_TIMEOUT_MS, "отказной подписи настроек") { textExists(settingsDecline) }
        capture("settings-consent")

        compose.onNode(
            hasTestTag(TAG_PRIMARY_BUTTON) and hasAnyAncestor(hasTestTag(TAG_CONSENT_SCREEN)),
            useUnmergedTree = true,
        ).performClick()

        waitForTag(TAG_SETTINGS_SCREEN)
        val analytics = compose.onNodeWithTag(TAG_ANALYTICS_ROW, useUnmergedTree = true)
        waitUntil(SCREEN_TIMEOUT_MS, "включённого переключателя аналитики") {
            analytics.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On
        }
        capture("settings-analytics-on")
    }

    private fun snoozeLabel(minutes: Int): String =
        targetContext.resources.getQuantityString(SettingsR.plurals.settings_snooze_minutes, minutes, minutes)

    private companion object {
        const val THIRTY_MINUTES = 30
    }
}
