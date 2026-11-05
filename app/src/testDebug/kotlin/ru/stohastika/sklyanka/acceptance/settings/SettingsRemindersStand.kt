package ru.stohastika.sklyanka.acceptance.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Стенд настроек для приёмки напоминаний (SKL-78).
 *
 * Модель **одна на сценарий**, а не собирается заново на каждый вопрос, как у
 * `SettingsAppProbe`: видимость выбора интервала живёт в состоянии экрана, и пересборка
 * между «открыл» и «выбрал» потеряла бы открытый диалог. Строки ведут наружу настоящим
 * `SettingsRoute`; строка перехода считается переходом, а не исполняется.
 */
@OptIn(ExperimentalTestApi::class)
internal object SettingsRemindersStand {

    private var lastWorld: AcceptanceWorld? = null
    private var viewModel: SettingsViewModel? = null

    /** Сколько раз строка перехода попросила системные настройки уведомлений (ОВ-98). */
    var soundOpened: Int = 0
        private set

    fun reset(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            lastWorld = world
            viewModel = null
            soundOpened = 0
            Dispatchers.setMain(UnconfinedTestDispatcher())
        }
    }

    fun model(world: AcceptanceWorld): SettingsViewModel {
        reset(world)
        return viewModel ?: build(world).also { viewModel = it }
    }

    fun state(world: AcceptanceWorld) = model(world).state.value

    fun ask(world: AcceptanceWorld, question: ComposeUiTest.() -> Unit) {
        reset(world)
        runComposeUiTest {
            setContent { Content(model(world)) }
            question()
            waitForIdle()
        }
    }

    fun act(world: AcceptanceWorld, action: ComposeUiTest.() -> Unit) {
        reset(world)
        runComposeUiTest {
            setContent { Content(model(world)) }
            action(this)
            waitForIdle()
        }
    }

    @Composable
    private fun Content(viewModel: SettingsViewModel) {
        SklyankaTheme {
            SettingsRoute(
                viewModel = viewModel,
                navigation = SettingsNavigation(openSound = { soundOpened += 1 }),
            )
        }
    }

    private fun build(world: AcceptanceWorld): SettingsViewModel = SettingsViewModel(
        profiles = world.repositories.profileStore,
        context = world.profileSession.contextProvider,
        medications = world.repositories.medicationStore,
        switchProfile = world.profileSession.switch,
        // Внешние ответы — одним узлом (НФТ-52): стенд собирает ту же модель, что продукт.
        environment = SettingsEnvironment(
            system = SettingsSystemStateReader(
                reminders = world.reminders.settingsStore,
                permissions = world.system,
            ),
            stamp = AppStamp(versionName = "1.0", versionCode = 1),
        ),
        // Оформление (SKL-120, §5.17): стенд собирает ту же модель, что продукт.
        appearance = SettingsAppearance(
            observeTheme = ObserveAccentThemeUseCase(appearance),
            observeIcon = ObserveIconVariantUseCase(appearance),
            setTheme = SetAccentThemeUseCase(appearance),
            setIcon = SetIconVariantUseCase(appearance),
        ),
    )

    /** Настройки оформления стенда: базовые «Олива» и «Ледяная» (Р-20, Р-56). */
    private val appearance = FakeAppearancePreferences()

    /** Забыть подменённый главный диспетчер: стенд не должен влиять на соседние тесты. */
    fun dropMainDispatcher() {
        Dispatchers.resetMain()
    }
}
