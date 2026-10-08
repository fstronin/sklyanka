package ru.stohastika.sklyanka.acceptance.onboarding

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.onboarding.ConfirmReliabilityStepUseCase
import ru.stohastika.sklyanka.domain.onboarding.LeaveOnboardingUseCase
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.ReadPermissionChecklistUseCase
import ru.stohastika.sklyanka.domain.onboarding.StepOpenResult
import ru.stohastika.sklyanka.feature.onboarding.OnboardingUiState
import ru.stohastika.sklyanka.feature.onboarding.OnboardingViewModel
import ru.stohastika.sklyanka.feature.onboarding.PermissionsActions
import ru.stohastika.sklyanka.feature.onboarding.PermissionsRoute
import ru.stohastika.sklyanka.feature.onboarding.PermissionsScreen
import ru.stohastika.sklyanka.feature.onboarding.SystemSettingsNavigator
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.Instant

/**
 * Стенд экрана «Настроить надёжность»: настоящий `OnboardingViewModel` на мире сценария
 * (SKL-14, SKL-84).
 *
 * Состояние цепочки читается настоящим `ReadPermissionChecklistUseCase` из тех же портов,
 * что у продукта: разрешения — из системы (`ScenarioSystemState`, НФТ-52), сохранённые
 * факты, вендор и подсказки — из мира онбординга.
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

    /** Системные переходы: кто открывался и чем кончился вендорский экран. */
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

    fun exit(world: AcceptanceWorld): Unit? = model(world).exit.value

    /** Открыть экран из названного источника — как это делает хозяин экрана. */
    fun open(world: AcceptanceWorld, source: OnboardingSource, outageSince: Instant? = null) {
        model(world).start(source, outageSince)
    }

    /**
     * Куда маршрут отдал исход хозяину (`onExit`); `null` — экран ещё не закрывался.
     *
     * Отдельно от `OnboardingViewModel.exit`: наблюдаемое «экран закрылся» — это вызов
     * хозяина, а не значение в модели (ОВ-86). Именно его и проверяет приёмка.
     */
    var exitedTo: Unit? = null
        private set

    /**
     * Системная «Назад» приходит отсюда: владелец диспетчера — единственная точка входа
     * системного события, которую стенд может подставить вместо Activity.
     */
    private val backOwner = RecordingBackOwner()

    /** Показать маршрут и ничего не нажимать: проверка «экран открыт». */
    fun showRoute(world: AcceptanceWorld) {
        reset(world)
        exitedTo = null
        runComposeUiTest {
            setContent { RouteContent(world) }
            waitForIdle()
        }
    }

    /** Системная «Назад» через настоящий `BackHandler` маршрута. */
    fun pressSystemBack(world: AcceptanceWorld) {
        reset(world)
        exitedTo = null
        runComposeUiTest {
            setContent { RouteContent(world) }
            waitForIdle()
            backOwner.onBackPressedDispatcher.onBackPressed()
            waitForIdle()
        }
    }

    /** Видимый выход экрана — тем же путём, что «Назад»: так проверяется «ведёт туда же». */
    fun pressExitButton(world: AcceptanceWorld, tag: String) {
        reset(world)
        exitedTo = null
        runComposeUiTest {
            setContent { RouteContent(world) }
            waitForIdle()
            onNodeWithTag(tag).performClick()
            waitForIdle()
        }
    }

    @Composable
    private fun RouteContent(world: AcceptanceWorld) {
        CompositionLocalProvider(
            LocalOnBackPressedDispatcherOwner provides backOwner,
            LocalLifecycleOwner provides backOwner,
        ) {
            SklyankaTheme {
                PermissionsRoute(
                    viewModel = model(world),
                    source = state(world).source,
                    outageSince = state(world).outageSince,
                    onExit = { exitedTo = Unit },
                )
            }
        }
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
                    onManualConfirmed = { step, confirmed ->
                        onAction { model(world).onManualConfirmed(step, confirmed) }
                    },
                    onNext = { onAction { model(world).onNext() } },
                    onSkip = { onAction { model(world).onSkip() } },
                    onDone = { onAction { model(world).onDone() } },
                ),
                toLocal = { instant -> instant.atZone(world.clock.time.zone()).toLocalDateTime() },
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
            vendor = world.onboarding,
        ),
        confirmStep = ConfirmReliabilityStepUseCase(world.onboarding, world.onboarding),
        leaveOnboarding = LeaveOnboardingUseCase(world.onboarding, world.onboarding),
        navigator = navigator,
        log = world.onboarding,
        time = world.clock.time,
    )

    /** Забыть подменённый главный диспетчер: стенд не должен влиять на соседние тесты. */
    fun dropMainDispatcher() {
        Dispatchers.resetMain()
    }
}

/** Системные переходы в памяти: записаны — и ничего не открывают. */
internal class RecordingNavigator : SystemSettingsNavigator {

    val opened: MutableList<String> = mutableListOf()

    /** Чем отвечает вендорский экран; запасной путь проверяется отдельным сценарием. */
    var appStartupResult: StepOpenResult = StepOpenResult.OPENED
    var batterySavingResult: StepOpenResult = StepOpenResult.OPENED

    override fun openExactAlarmSettings(): StepOpenResult {
        opened += EXACT_ALARMS
        return StepOpenResult.OPENED
    }

    override fun openNotificationSettings(channelId: String?): StepOpenResult {
        opened += NOTIFICATIONS
        return StepOpenResult.OPENED
    }

    override fun openFullScreenIntentSettings(): StepOpenResult {
        opened += FULL_SCREEN
        return StepOpenResult.OPENED
    }

    override fun openAppStartupSettings(): StepOpenResult {
        opened += APP_STARTUP
        return appStartupResult
    }

    override fun openBatterySavingSettings(): StepOpenResult {
        opened += BATTERY_SAVING
        return batterySavingResult
    }

    fun clear() {
        opened.clear()
        appStartupResult = StepOpenResult.OPENED
        batterySavingResult = StepOpenResult.OPENED
    }

    companion object {
        const val NOTIFICATIONS = "notifications"
        const val EXACT_ALARMS = "exact-alarms"
        const val FULL_SCREEN = "full-screen"
        const val APP_STARTUP = "app-startup"
        const val BATTERY_SAVING = "battery-saving"
    }
}

/**
 * Владелец диспетчера системной «Назад» без Activity (ОВ-86).
 *
 * Настоящий `BackHandler` маршрута регистрируется на владельце, которого даёт композиция;
 * стенд подставляет сюда свой, и нажатие идёт через обработчик продукта, а не через
 * пересказ шага. Владельцем жизненного цикла он же и является: обработчик привязывается
 * к нему, и подделка обязана быть в состоянии `RESUMED` — иначе он выключен.
 */
internal class RecordingBackOwner :
    OnBackPressedDispatcherOwner,
    LifecycleOwner {

    override val onBackPressedDispatcher = OnBackPressedDispatcher()

    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = registry

    init {
        registry.currentState = Lifecycle.State.RESUMED
    }
}
