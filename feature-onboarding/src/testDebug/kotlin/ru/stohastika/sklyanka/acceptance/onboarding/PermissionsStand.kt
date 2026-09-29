package ru.stohastika.sklyanka.acceptance.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.onboarding.AutostartOpenResult
import ru.stohastika.sklyanka.domain.onboarding.ConfirmAutostartUseCase
import ru.stohastika.sklyanka.domain.onboarding.LeaveOnboardingUseCase
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.ReadPermissionChecklistUseCase
import ru.stohastika.sklyanka.feature.onboarding.OnboardingExit
import ru.stohastika.sklyanka.feature.onboarding.OnboardingUiState
import ru.stohastika.sklyanka.feature.onboarding.OnboardingViewModel
import ru.stohastika.sklyanka.feature.onboarding.PermissionsActions
import ru.stohastika.sklyanka.feature.onboarding.PermissionsScreen
import ru.stohastika.sklyanka.feature.onboarding.SystemSettingsNavigator
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Стенд экрана разрешений: настоящий `OnboardingViewModel` на мире сценария (SKL-14).
 *
 * Состояние цепочки читается настоящим `ReadPermissionChecklistUseCase` из тех же портов,
 * что у продукта: разрешения — из системы (`ScenarioSystemState`, НФТ-52), сохранённые
 * факты и подсказки — из мира онбординга.
 *
 * Подменяется только платформа: системные переходы записывает [RecordingNavigator],
 * а системный диалог уведомлений, который в продукте показывает `PermissionsRoute`,
 * отмечается записью «запрошен» — сам диалог на JVM не существует. Это порт платформы,
 * а не правило: «кто просит разрешение» решает домен (`actionFor`).
 *
 * Подменённый главный диспетчер обязан сниматься прогоном (`dropMainDispatcher`).
 */
@OptIn(ExperimentalTestApi::class)
internal object PermissionsStand {

    private var lastWorld: AcceptanceWorld? = null
    private var viewModel: OnboardingViewModel? = null

    /** Системные переходы: кто открывался и чем кончился автозапуск. */
    val navigator = RecordingNavigator()

    /** Шаги, для которых хозяин экрана обязан показать системный диалог (ПЛ-13). */
    val requested: MutableList<PermissionStep> = mutableListOf()

    fun reset(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            lastWorld = world
            viewModel = null
            navigator.clear()
            requested.clear()
            Dispatchers.setMain(UnconfinedTestDispatcher())
        }
    }

    fun model(world: AcceptanceWorld): OnboardingViewModel {
        reset(world)
        return viewModel ?: build(world).also { viewModel = it }
    }

    fun state(world: AcceptanceWorld): OnboardingUiState = model(world).state.value

    fun exit(world: AcceptanceWorld): OnboardingExit? = model(world).exit.value

    /** Открыть экран из названного источника — как это делает хозяин экрана. */
    fun open(world: AcceptanceWorld, source: OnboardingSource) {
        model(world).start(source)
    }

    /** Системный диалог разрешения: результат приходит из системы, а не из ответа человека. */
    fun reportPermissionResult(world: AcceptanceWorld, step: PermissionStep, granted: Boolean) {
        model(world).onPermissionResult(step, granted)
    }

    @Composable
    private fun Content(world: AcceptanceWorld, onAction: (() -> Unit) -> Unit) {
        SklyankaTheme {
            PermissionsScreen(
                state = state(world),
                actions = PermissionsActions(
                    onStepAction = { step ->
                        onAction {
                            // «Да» модели означает системный диалог: его в продукте показывает
                            // PermissionsRoute, и стенд отмечает ровно этот факт.
                            if (model(world).onStepAction(step)) requested += step
                        }
                    },
                    onAutostartConfirmed = { confirmed -> onAction { model(world).onAutostartConfirmed(confirmed) } },
                    onNext = { onAction { model(world).onNext() } },
                    onSkip = { onAction { model(world).onSkip() } },
                ),
            )
        }
    }

    fun ask(world: AcceptanceWorld, question: ComposeUiTest.() -> Unit) {
        reset(world)
        runComposeUiTest {
            setContent { Content(world, onAction = {}) }
            question()
            waitForIdle()
        }
    }

    /** Действие человека: нажатия копятся и применяются после композиции. */
    fun act(world: AcceptanceWorld, action: ComposeUiTest.() -> Unit) {
        reset(world)
        val performed = mutableListOf<() -> Unit>()
        runComposeUiTest {
            setContent { Content(world, onAction = { performed += it }) }
            action(this)
            waitForIdle()
        }
        performed.forEach { it() }
    }

    private fun build(world: AcceptanceWorld): OnboardingViewModel = OnboardingViewModel(
        readChecklist = ReadPermissionChecklistUseCase(
            permissions = world.system,
            store = world.onboarding,
            hints = world.onboarding,
            requests = world.onboarding,
            time = world.clock.time,
        ),
        confirmAutostart = ConfirmAutostartUseCase(world.onboarding, world.onboarding),
        leaveOnboarding = LeaveOnboardingUseCase(world.onboarding, world.onboarding),
        navigator = navigator,
        log = world.onboarding,
    )

    /** Забыть подменённый главный диспетчер: стенд не должен влиять на соседние тесты. */
    fun dropMainDispatcher() {
        Dispatchers.resetMain()
    }
}

/** Системные переходы в памяти: записаны — и ничего не открывают. */
internal class RecordingNavigator : SystemSettingsNavigator {

    val opened: MutableList<String> = mutableListOf()

    /** Чем отвечает экран автозапуска; запасной путь проверяется отдельным сценарием. */
    var autostartResult: AutostartOpenResult = AutostartOpenResult.OPENED

    override fun openExactAlarmSettings() {
        opened += EXACT_ALARMS
    }

    override fun openNotificationSettings() {
        opened += NOTIFICATIONS
    }

    override fun openAutostartSettings(): AutostartOpenResult {
        opened += AUTOSTART
        return autostartResult
    }

    fun clear() {
        opened.clear()
        autostartResult = AutostartOpenResult.OPENED
    }

    companion object {
        const val NOTIFICATIONS = "notifications"
        const val EXACT_ALARMS = "exact-alarms"
        const val AUTOSTART = "autostart"
    }
}
