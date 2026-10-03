package ru.stohastika.sklyanka.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import ru.stohastika.sklyanka.feature.onboarding.TAG_PERMISSIONS_SCREEN
import ru.stohastika.sklyanka.feature.onboarding.TAG_SKIP
import ru.stohastika.sklyanka.feature.onboarding.consent.TAG_CONSENT_SCREEN
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN
import ru.stohastika.sklyanka.ui.component.TAG_SECONDARY_BUTTON

/**
 * N — первый запуск на настоящем устройстве (SKL-97, TDD §8.2).
 *
 * Состояние пусто: согласие не спрошено, онбординг не отмечен, отметка запуска снята.
 * Цепочка проходится продуктом целиком — согласие, экран разрешений, «Сегодня»; каждый
 * шаг оставляет кадр. Согласие закрывается отказом: аналитика выключена, и цепочка
 * не зависит от неё.
 */
@HiltAndroidTest
class FirstLaunchDeviceTest : DeviceScreenTest() {

    /** Первый запуск: ничего не сеем — и согласие, и онбординг должны пройтись по-настоящему. */
    override fun seedDefaultState() = Unit

    @Test
    fun theFirstLaunchGoesThroughConsentAndOnboarding() {
        launchApp()

        waitForTag(TAG_CONSENT_SCREEN, ROOT_TIMEOUT_MS).assertIsDisplayed()
        capture("consent")

        compose.onNode(
            hasTestTag(TAG_SECONDARY_BUTTON) and hasAnyAncestor(hasTestTag(TAG_CONSENT_SCREEN)),
            useUnmergedTree = true,
        ).performClick()

        waitForTag(TAG_PERMISSIONS_SCREEN, ROOT_TIMEOUT_MS).assertIsDisplayed()
        capture("permissions")

        click(TAG_SKIP)
        waitForTag(TAG_TODAY_SCREEN, ROOT_TIMEOUT_MS).assertIsDisplayed()
        capture("today-first-launch")
    }
}
