package ru.stohastika.sklyanka.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.MainActivity
import ru.stohastika.sklyanka.feature.onboarding.TAG_PERMISSIONS_SCREEN
import ru.stohastika.sklyanka.feature.onboarding.TAG_SKIP
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN
import ru.stohastika.sklyanka.screen.support.DeviceActivities
import ru.stohastika.sklyanka.screen.support.DeviceTestSeed
import ru.stohastika.sklyanka.startup.TAG_PRIVACY_CONSENT
import ru.stohastika.sklyanka.startup.TAG_PRIVACY_CONSENT_AGREE
import ru.stohastika.sklyanka.startup.TAG_PRIVACY_CONSENT_DECLINE

/**
 * N — первый запуск на настоящем устройстве (SKL-97, TDD §8.2; ревизия SKL-100).
 *
 * Состояние пусто: онбординг не отмечен, политика не принята, отметка запуска снята.
 * Цепочка проходится продуктом целиком — диалог политики, экран разрешений, «Сегодня»;
 * каждый шаг оставляет кадр. Аналитического согласия в сборке нет (Р-4, SKL-106).
 */
@HiltAndroidTest
class FirstLaunchDeviceTest : DeviceScreenTest() {

    /** Первый запуск: ничего не сеем — диалог и онбординг должны пройтись по-настоящему. */
    override fun seedDefaultState() = Unit

    @Test
    fun theFirstLaunchGoesThroughThePolicyAndOnboarding() {
        launchApp()

        // Политика — до всякого действия: диалог закрывается только кнопкой (Р-89).
        // Системная «Назад» не снимает его: это свойство окна диалога, и проверяется оно
        // настоящим нажатием на устройстве, а не диспетчером возврата активности —
        // тот обходит окно диалога и попадает в экран под ним.
        waitForTag(TAG_PRIVACY_CONSENT, ROOT_TIMEOUT_MS).assertIsDisplayed()
        capture("privacy-first-launch", compose.onNode(isDialog()))

        click(TAG_PRIVACY_CONSENT_AGREE)
        waitUntil(ROOT_TIMEOUT_MS, "исчезновения диалога политики") { !exists(TAG_PRIVACY_CONSENT) }
        assertTrue("the acknowledgement must be stored", DeviceTestSeed.privacyAcknowledgedFlag(targetContext))

        waitForTag(TAG_PERMISSIONS_SCREEN, ROOT_TIMEOUT_MS).assertIsDisplayed()
        capture("permissions")

        click(TAG_SKIP)
        waitForTag(TAG_TODAY_SCREEN, ROOT_TIMEOUT_MS).assertIsDisplayed()
        capture("today-first-launch")

        // Повторный запуск: принятие пережило перезапуск, диалог не показывается.
        DeviceActivities.closeAll(compose)
        launchApp()
        waitForTag(TAG_TODAY_SCREEN, ROOT_TIMEOUT_MS).assertIsDisplayed()
        assertTrue("the policy dialog must not return", !exists(TAG_PRIVACY_CONSENT))
    }

    /** Отказ закрывает приложение и ничего не запоминает (Р-90). */
    @Test
    fun thePersonCanDeclineAndTheAppCloses() {
        launchApp()

        waitForTag(TAG_PRIVACY_CONSENT, ROOT_TIMEOUT_MS).assertIsDisplayed()
        click(TAG_PRIVACY_CONSENT_DECLINE)
        awaitScreenClosed(MainActivity::class.java)

        assertFalse(
            "decline must not be stored",
            DeviceTestSeed.privacyAcknowledgedFlag(targetContext),
        )
    }
}
