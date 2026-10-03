package ru.stohastika.sklyanka.acceptance.rollback

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import ru.stohastika.sklyanka.feature.settings.SettingsScreen
import ru.stohastika.sklyanka.feature.settings.SettingsUiState
import ru.stohastika.sklyanka.feature.today.TodayUiState
import ru.stohastika.sklyanka.startup.SplashRoute
import ru.stohastika.sklyanka.startup.StartDestination
import ru.stohastika.sklyanka.startup.StartupConditions
import ru.stohastika.sklyanka.startup.StartupDecision
import ru.stohastika.sklyanka.startup.StartupFrame
import ru.stohastika.sklyanka.startup.startupFrame
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Стенд SKL-100: первый запуск без согласия и настройки без строки «Аналитика».
 *
 * Первый запуск считается настоящим `SplashRoute.decisionOf` и настоящим правилом кадра
 * `startupFrame`; настройки компонуются настоящим `SettingsScreen`. Согласие в сценарии
 * не участвует вовсе — его UX скрыт до подключения провайдера (ПМ-31).
 */
@OptIn(ExperimentalTestApi::class)
internal object AnalyticsRollbackStand {

    /** Решение запуска, посчитанное настоящим правилом. */
    var decision: StartupDecision? = null
        private set

    /** Кадр первого запуска: онбординг или заставка. */
    var frame: StartupFrame? = null
        private set

    /** Кадр после пройденного онбординга: корень приложения. */
    var afterOnboarding: StartupFrame? = null
        private set

    fun startFirstLaunch() {
        val conditions = StartupConditions(
            firstLaunch = true,
            notificationsAllowed = false,
            exactAlarmsAllowed = true,
        )
        val resolved = SplashRoute.decisionOf(conditions)
        decision = resolved
        frame = startupFrame(
            destination = resolved.destination,
            onboardingUnseen = true,
            onboardingDone = false,
            today = TodayUiState.Opening,
        )
        // Онбординг закрыт человеком: экран разрешений больше не первый; корень удерживает
        // заставку, пока «Сегодня» не дало содержимое, — это и есть обычный запуск.
        afterOnboarding = startupFrame(
            destination = StartDestination.Today,
            onboardingUnseen = true,
            onboardingDone = true,
            today = TodayUiState.Opening,
        )
    }

    /** Собрать настоящий экран настроек и выполнить проверку на нём. */
    fun settingsCheck(block: ComposeUiTest.() -> Unit) {
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    SettingsScreen(state = SettingsUiState(version = "test"), onIntent = {})
                }
            }
            block()
            waitForIdle()
        }
    }
}
