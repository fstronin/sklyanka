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
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import ru.stohastika.sklyanka.domain.transfer.AppStamp
import ru.stohastika.sklyanka.feature.settings.SettingsIntent
import ru.stohastika.sklyanka.feature.settings.SettingsScreen
import ru.stohastika.sklyanka.feature.settings.SettingsSystemStateReader
import ru.stohastika.sklyanka.feature.settings.SettingsUiState
import ru.stohastika.sklyanka.feature.settings.SettingsViewModel
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Стенд экрана настроек: настоящий `SettingsViewModel` на мире сценария (SKL-20).
 *
 * Состояние собирает продукт, а не пересказ: список профилей читается теми же запросами,
 * что у экрана, значения настроек — тем же портом, разрешения — тем же разовым чтением
 * `SettingsSystemStateReader` (НФТ-52). Стенд только собирает композицию и передаёт
 * намерения; правил в нём нет.
 *
 * Подменённый главный диспетчер обязан сниматься прогоном (`dropMainDispatcher`):
 * иначе он утекает в соседние тесты.
 */
@OptIn(ExperimentalTestApi::class)
internal object SettingsScreenProbe {

    private var lastWorld: AcceptanceWorld? = null
    private var viewModel: SettingsViewModel? = null

    /** Сколько раз ViewModel собирали: этим проверяется «перезапуск не потребовался». */
    var builds: Int = 0
        private set

    fun reset(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            lastWorld = world
            viewModel = null
            builds = 0
            Dispatchers.setMain(UnconfinedTestDispatcher())
        }
    }

    fun model(world: AcceptanceWorld): SettingsViewModel {
        reset(world)
        return viewModel ?: build(world).also {
            viewModel = it
            builds += 1
        }
    }

    /** Перезапуск приложения: ViewModel собирается заново над тем же миром (ПЕР-31). */
    fun restart(world: AcceptanceWorld) {
        reset(world)
        viewModel = null
    }

    fun state(world: AcceptanceWorld): SettingsUiState = model(world).state.value

    fun onIntent(world: AcceptanceWorld, intent: SettingsIntent) {
        model(world).onIntent(intent)
    }

    /** Возвращение в приложение: то же, что делает `MainActivity.onResume` (НФТ-52). */
    fun refresh(world: AcceptanceWorld) {
        model(world).refresh()
    }

    // --- Композиция ----------------------------------------------------------------------------

    fun ask(world: AcceptanceWorld, question: ComposeUiTest.() -> Unit) {
        reset(world)
        runComposeUiTest {
            setContent { Content(state(world), onIntent = {}) }
            question()
            waitForIdle()
        }
    }

    /**
     * Действие человека на собранном экране.
     *
     * Нажатия копятся во время композиции и применяются после неё тем же ViewModel,
     * что у продукта.
     */
    fun act(world: AcceptanceWorld, action: ComposeUiTest.() -> Unit) {
        reset(world)
        val performed = mutableListOf<() -> Unit>()
        runComposeUiTest {
            setContent {
                Content(state(world), onIntent = { intent -> performed += { onIntent(world, intent) } })
            }
            action(this)
            waitForIdle()
        }
        performed.forEach { it() }
    }

    @Composable
    private fun Content(state: SettingsUiState, onIntent: (SettingsIntent) -> Unit) {
        SklyankaTheme { SettingsScreen(state = state, onIntent = onIntent) }
    }

    // --- Обвязка -------------------------------------------------------------------------------

    private fun build(world: AcceptanceWorld): SettingsViewModel = SettingsViewModel(
        profiles = world.repositories.profileStore,
        context = world.profileSession.contextProvider,
        medications = world.repositories.medicationStore,
        switchProfile = world.profileSession.switch,
        system = SettingsSystemStateReader(
            reminders = world.reminders.settingsStore,
            permissions = world.system,
        ),
        stamp = APP_STAMP,
        // Состояние подписки — тот же сценарий мира, что и у продукта (SKL-122).
        proStatus = world.pro.statusUseCase,
    )

    /** Забыть подменённый главный диспетчер: стенд не должен влиять на соседние тесты. */
    fun dropMainDispatcher() {
        Dispatchers.resetMain()
    }

    /**
     * Версия приложения из макета и приёмки: экран показывает собранную версию,
     * и стенд называет ту же («Склянка 1.0»).
     */
    private val APP_STAMP = AppStamp(versionName = "1.0", versionCode = 1)

    /** Порт настроек напоминаний для проверки «изменение видно сразу». */
    fun reminderSettings(world: AcceptanceWorld): ReminderSettings = world.reminders.settingsStore
}
