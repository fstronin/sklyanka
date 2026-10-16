package ru.stohastika.sklyanka.acceptance.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.stohastika.sklyanka.acceptance.profile.TodayFromWorld
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.appearance.ObserveAccentThemeUseCase
import ru.stohastika.sklyanka.domain.appearance.ObserveIconVariantUseCase
import ru.stohastika.sklyanka.domain.appearance.SetAccentThemeUseCase
import ru.stohastika.sklyanka.domain.appearance.SetIconVariantUseCase
import ru.stohastika.sklyanka.domain.transfer.AppStamp
import ru.stohastika.sklyanka.feature.settings.SettingsAppearance
import ru.stohastika.sklyanka.feature.settings.SettingsEnvironment
import ru.stohastika.sklyanka.feature.settings.SettingsNavigation
import ru.stohastika.sklyanka.feature.settings.SettingsRoute
import ru.stohastika.sklyanka.feature.settings.SettingsSystemStateReader
import ru.stohastika.sklyanka.feature.settings.SettingsViewModel
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.feature.today.TodayUiState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

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
                    // Экран добавления профиля — `ОВ-3`: в продукте его нет, и сценарии
                    // его не трогают.
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
        // Внешние ответы — одним узлом (НФТ-52, SKL-122): состояние системы, версия пакета
        // и кэш подписки тем же сценарием мира, что у продукта.
        environment = SettingsEnvironment(
            system = SettingsSystemStateReader(
                reminders = world.reminders.settingsStore,
                permissions = world.system,
            ),
            stamp = AppStamp(versionName = "1.0", versionCode = 1),
            proStatus = world.pro.statusUseCase,
        ),
        // Оформление (SKL-120, §5.17): те же доменные швы, что у продукта; носитель выбора
        // подменён памятью, правило выбора — нет.
        appearance = SettingsAppearance(
            observeTheme = ObserveAccentThemeUseCase(appearance),
            observeIcon = ObserveIconVariantUseCase(appearance),
            setTheme = SetAccentThemeUseCase(appearance, world.pro.statusUseCase),
            setIcon = SetIconVariantUseCase(appearance, world.pro.statusUseCase),
        ),
    )

    /** Настройки оформления стенда: базовые «Олива» и «Ледяная» (Р-20, Р-56). */
    private val appearance = FakeAppearancePreferences()

    /** Забыть подменённый главный диспетчер: стенд не должен влиять на соседние тесты. */
    fun dropMainDispatcher() {
        Dispatchers.resetMain()
    }
}
