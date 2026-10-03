package ru.stohastika.sklyanka.screen

import androidx.compose.ui.test.assertIsDisplayed
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Test
import ru.stohastika.sklyanka.feature.onboarding.TAG_PERMISSIONS_SCREEN
import ru.stohastika.sklyanka.feature.onboarding.TAG_SKIP
import ru.stohastika.sklyanka.feature.today.TAG_TODAY_SCREEN

/**
 * N — первый запуск на настоящем устройстве (SKL-97, TDD §8.2; ревизия SKL-100).
 *
 * Состояние пусто: онбординг не отмечен, отметка запуска снята. Цепочка проходится
 * продуктом целиком — экран разрешений, «Сегодня»; каждый шаг оставляет кадр.
 * Экрана согласия в релизе нет (ПМ-31).
 */
@HiltAndroidTest
class FirstLaunchDeviceTest : DeviceScreenTest() {

    /** Первый запуск: ничего не сеем — онбординг должен пройтись по-настоящему. */
    override fun seedDefaultState() = Unit

    @Test
    fun theFirstLaunchGoesThroughOnboarding() {
        launchApp()

        waitForTag(TAG_PERMISSIONS_SCREEN, ROOT_TIMEOUT_MS).assertIsDisplayed()
        capture("permissions")

        click(TAG_SKIP)
        waitForTag(TAG_TODAY_SCREEN, ROOT_TIMEOUT_MS).assertIsDisplayed()
        capture("today-first-launch")
    }
}
