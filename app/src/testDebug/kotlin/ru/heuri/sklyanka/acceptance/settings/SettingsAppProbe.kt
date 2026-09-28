package ru.heuri.sklyanka.acceptance.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.heuri.sklyanka.acceptance.profile.TodayFromWorld
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.transfer.AppStamp
import ru.heuri.sklyanka.domain.watch.WatchLinkState
import ru.heuri.sklyanka.domain.watch.WatchLinkStateReader
import ru.heuri.sklyanka.feature.settings.SettingsNavigation
import ru.heuri.sklyanka.feature.settings.SettingsRoute
import ru.heuri.sklyanka.feature.settings.SettingsSystemStateReader
import ru.heuri.sklyanka.feature.settings.SettingsViewModel
import ru.heuri.sklyanka.feature.today.TodayScreen
import ru.heuri.sklyanka.feature.today.TodayUiState
import ru.heuri.sklyanka.ui.theme.SklyankaTheme

/**
 * Стенд композиции настроек (SKL-20): настоящий `SettingsRoute` и настоящий экран
 * «Сегодня» на мире сценария.
 *
 * Настройки собираются маршрутом продукта: строка профиля отдаёт намерение, маршрут
 * зовёт `SwitchProfileUseCase` (SKL-15), и активный профиль меняется в мире — второй
 * реализации переключения в приёмке нет. «Сегодня» собирается тем же путём, что в прогоне
 * SKL-08 ([TodayFromWorld]).
 *
 * Переходы наружу считает [navigation]: этим проверяется, что до переноса данных ведёт
 * ровно одна строка и что других способов у приложения нет.
 */
@OptIn(ExperimentalTestApi::class)
internal object SettingsAppProbe {

    private var lastWorld: AcceptanceWorld? = null

    val navigation = NavigationRecorder()

    /** Что экран попросил открыть: переходы считаются, а не исполняются. */
    class NavigationRecorder {
        var diagnostics: Int = 0
        var history: Int = 0
        var transfer: Int = 0
    }

    fun reset(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            lastWorld = world
            navigation.diagnostics = 0
            navigation.history = 0
            navigation.transfer = 0
            Dispatchers.setMain(UnconfinedTestDispatcher())
        }
    }

    /**
     * Модель собирается заново на каждый вопрос.
     *
     * Потоки мира сценария разовые, а не наблюдаемые: после переключения профиля
     * подписка, собранная раньше, нового значения не получит. Продукт собирает состояние
     * из потока, который живой; стенд спрашивает мир заново — тем же `SettingsViewModel`,
     * и правил в нём не повторяет.
     */
    fun model(world: AcceptanceWorld): SettingsViewModel {
        reset(world)
        return build(world)
    }

    fun settingsState(world: AcceptanceWorld) = model(world).state.value

    fun askSettings(world: AcceptanceWorld, question: ComposeUiTest.() -> Unit) {
        reset(world)
        runComposeUiTest {
            setContent { Content(model(world)) }
            question()
            waitForIdle()
        }
    }

    fun actSettings(world: AcceptanceWorld, action: ComposeUiTest.() -> Unit) {
        reset(world)
        runComposeUiTest {
            setContent { Content(model(world)) }
            action(this)
            waitForIdle()
        }
    }

    /** Экран «Сегодня» тем же путём, что в прогоне SKL-08: повестка дня из мира. */
    fun askToday(world: AcceptanceWorld, question: (ComposeUiTest, TodayUiState) -> Unit) {
        reset(world)
        val state = TodayFromWorld.state(world)
        runComposeUiTest {
            setContent { SklyankaTheme { TodayScreen(state = state, onIntent = {}) } }
            question(this, state)
            waitForIdle()
        }
    }

    @Composable
    private fun Content(viewModel: SettingsViewModel) {
        SklyankaTheme {
            SettingsRoute(
                viewModel = viewModel,
                navigation = SettingsNavigation(
                    // Экран добавления профиля — `ОВ-3`, подключение часов — SKL-17:
                    // ни того, ни другого в продукте нет, и сценарии их не трогают.
                    openDiagnostics = { navigation.diagnostics += 1 },
                    openHistory = { navigation.history += 1 },
                    openDataTransfer = { navigation.transfer += 1 },
                ),
            )
        }
    }

    private fun build(world: AcceptanceWorld): SettingsViewModel = SettingsViewModel(
        profiles = world.repositories.profileStore,
        context = world.profileSession.contextProvider,
        medications = world.repositories.medicationStore,
        switchProfile = world.profileSession.switch,
        system = SettingsSystemStateReader(
            reminders = world.reminders.settingsStore,
            permissions = world.system,
            watch = WatchReader(world),
        ),
        stamp = AppStamp(versionName = "1.0", versionCode = 1),
    )

    private class WatchReader(private val world: AcceptanceWorld) : WatchLinkStateReader {
        override suspend fun read(): WatchLinkState = world.system.watch
    }

    /** Забыть подменённый главный диспетчер: стенд не должен влиять на соседние тесты. */
    fun dropMainDispatcher() {
        Dispatchers.resetMain()
    }
}
