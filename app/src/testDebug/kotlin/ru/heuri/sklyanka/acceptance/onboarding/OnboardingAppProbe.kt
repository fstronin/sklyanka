package ru.heuri.sklyanka.acceptance.onboarding

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
import ru.heuri.sklyanka.domain.intake.SweepReason
import ru.heuri.sklyanka.domain.onboarding.AutostartOpenResult
import ru.heuri.sklyanka.domain.onboarding.ConfirmAutostartUseCase
import ru.heuri.sklyanka.domain.onboarding.LeaveOnboardingUseCase
import ru.heuri.sklyanka.domain.onboarding.OnboardingSource
import ru.heuri.sklyanka.domain.onboarding.PermissionStep
import ru.heuri.sklyanka.domain.onboarding.ReadPermissionChecklistUseCase
import ru.heuri.sklyanka.domain.reminder.BuildReminderDiagnosticsUseCase
import ru.heuri.sklyanka.domain.reminder.ExportReminderJournalUseCase
import ru.heuri.sklyanka.domain.reminder.ReadReminderReliabilityUseCase
import ru.heuri.sklyanka.domain.reminder.TestReminderUseCase
import ru.heuri.sklyanka.domain.schedule.PlanReason
import ru.heuri.sklyanka.domain.schedule.PlanScope
import ru.heuri.sklyanka.feature.onboarding.OnboardingExit
import ru.heuri.sklyanka.feature.onboarding.OnboardingUiState
import ru.heuri.sklyanka.feature.onboarding.OnboardingViewModel
import ru.heuri.sklyanka.feature.onboarding.PermissionsActions
import ru.heuri.sklyanka.feature.onboarding.PermissionsScreen
import ru.heuri.sklyanka.feature.onboarding.SystemSettingsNavigator
import ru.heuri.sklyanka.feature.reminder.DiagnosticsScreen
import ru.heuri.sklyanka.feature.reminder.DiagnosticsUiState
import ru.heuri.sklyanka.feature.reminder.DiagnosticsViewModel
import ru.heuri.sklyanka.feature.reminder.ReminderCheckNow
import ru.heuri.sklyanka.feature.today.TodayScreen
import ru.heuri.sklyanka.feature.today.TodayUiState
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import java.time.Instant

/**
 * Стенд композиции онбординга (SKL-14, группа `@composition`).
 *
 * Здесь собирается настоящий `OnboardingViewModel` на мире сценария и настоящий экран
 * разрешений; рядом — «Сегодня», настройки и диагностика, потому что сценарии говорят
 * о нескольких экранах сразу (`SKL-26/TDD.md §4.3`). Экраны те же, что у продукта;
 * состояния — из мира настоящими портами.
 *
 * Обвязка модели повторена из стенда `feature-onboarding` — иначе и не может быть:
 * модули экранов не видят тестовые наборы друг друга, а стенд обязан собирать продукт,
 * а не его пересказ. Повторяется композиция портов, не правило.
 *
 * Подменённый главный диспетчер обязан сниматься прогоном (`dropMainDispatcher`).
 */
@OptIn(ExperimentalTestApi::class)
internal object OnboardingAppProbe {

    private var lastWorld: AcceptanceWorld? = null
    private var viewModel: OnboardingViewModel? = null
    private var diagnostics: DiagnosticsViewModel? = null

    /** Системные переходы: кто открывался и чем кончился автозапуск. */
    val navigator = AppRecordingNavigator()

    /** Шаги, для которых хозяин экрана показывает системный диалог (ПЛ-13). */
    val requested: MutableList<PermissionStep> = mutableListOf()

    fun reset(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            lastWorld = world
            viewModel = null
            diagnostics = null
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

    fun open(world: AcceptanceWorld, source: OnboardingSource) {
        model(world).start(source)
    }

    // --- Композиция ----------------------------------------------------------------------------

    @Composable
    private fun PermissionsContent(world: AcceptanceWorld, onAction: (() -> Unit) -> Unit) {
        SklyankaTheme {
            PermissionsScreen(
                state = state(world),
                actions = PermissionsActions(
                    onStepAction = { step ->
                        onAction {
                            if (model(world).onStepAction(step)) requested += step
                        }
                    },
                    onAutostartConfirmed = { confirmed -> onAction { model(world).onAutostartConfirmed(confirmed) } },
                    onTestReminder = { onAction { model(world).onTestReminder() } },
                    onNext = { onAction { model(world).onNext() } },
                    onSkip = { onAction { model(world).onSkip() } },
                ),
                toLocal = { instant: Instant -> instant.atZone(world.clock.zone).toLocalDateTime() },
            )
        }
    }

    fun askPermissions(world: AcceptanceWorld, question: ComposeUiTest.() -> Unit) {
        reset(world)
        runComposeUiTest {
            setContent { PermissionsContent(world, onAction = {}) }
            question()
            waitForIdle()
        }
    }

    /** Действие человека: нажатия копятся и применяются после композиции. */
    fun actPermissions(world: AcceptanceWorld, action: ComposeUiTest.() -> Unit) {
        reset(world)
        val performed = mutableListOf<() -> Unit>()
        runComposeUiTest {
            setContent { PermissionsContent(world, onAction = { performed += it }) }
            action(this)
            waitForIdle()
        }
        performed.forEach { it() }
    }

    fun askToday(world: AcceptanceWorld, question: (ComposeUiTest, TodayUiState) -> Unit) {
        reset(world)
        val state = TodayFromWorld.state(world)
        runComposeUiTest {
            setContent { SklyankaTheme { TodayScreen(state = state, onIntent = {}) } }
            question(this, state)
            waitForIdle()
        }
    }

    fun diagnosticsState(world: AcceptanceWorld): DiagnosticsUiState {
        reset(world)
        val model = diagnostics ?: buildDiagnostics(world).also { diagnostics = it }
        return model.state.value
    }

    fun askDiagnostics(world: AcceptanceWorld, question: ComposeUiTest.() -> Unit) {
        reset(world)
        val model = diagnostics ?: buildDiagnostics(world).also { diagnostics = it }
        // Снимок состояния берётся до композиции: внутри неё `StateFlow.value` читать нельзя.
        val snapshot = model.state.value
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    DiagnosticsScreen(
                        state = snapshot,
                        onCheckNow = {},
                        onTestReminder = {},
                        onExportJournal = {},
                        onRestartSetup = null,
                        onBack = {},
                        toLocal = { instant: Instant -> instant.atZone(world.clock.zone).toLocalDateTime() },
                    )
                }
            }
            question()
            waitForIdle()
        }
    }

    private fun build(world: AcceptanceWorld): OnboardingViewModel = OnboardingViewModel(
        readChecklist = ReadPermissionChecklistUseCase(
            permissions = world.system,
            store = world.onboarding,
            hints = world.onboarding,
            requests = world.onboarding,
            journal = world.reminders.journal,
            time = world.clock.time,
        ),
        confirmAutostart = ConfirmAutostartUseCase(world.onboarding, world.onboarding),
        leaveOnboarding = LeaveOnboardingUseCase(world.onboarding, world.onboarding),
        testReminder = TestReminderUseCase(world.reminders.alarms, world.clock.time),
        navigator = navigator,
        log = world.onboarding,
        time = world.clock.time,
    )

    private fun buildDiagnostics(world: AcceptanceWorld): DiagnosticsViewModel = DiagnosticsViewModel(
        diagnostics = BuildReminderDiagnosticsUseCase(
            journal = world.reminders.journal,
            alarms = world.reminders.alarms,
            permissions = world.system,
            plan = world.reminders.planSource(),
            reliability = ReadReminderReliabilityUseCase(world.system),
            time = world.clock.time,
        ),
        testReminder = TestReminderUseCase(world.reminders.alarms, world.clock.time),
        check = ReminderCheckNow { reason ->
            world.sweep(SweepReason.REPLAN)
            world.plan(PlanScope.AllProfiles, PlanReason.MANUAL_CHECK)
            world.reminders.reschedule(reason)
        },
        exportJournal = ExportReminderJournalUseCase(
            journal = world.reminders.journal,
            files = world.transfer.files,
            time = world.clock.time,
        ),
        time = world.clock.time,
    )

    /** Забыть подменённый главный диспетчер: стенд не должен влиять на соседние тесты. */
    fun dropMainDispatcher() {
        Dispatchers.resetMain()
    }
}

/** Системные переходы в памяти: записаны — и ничего не открывают. */
internal class AppRecordingNavigator : SystemSettingsNavigator {

    val opened: MutableList<String> = mutableListOf()

    override fun openExactAlarmSettings() {
        opened += "exact-alarms"
    }

    override fun openNotificationSettings() {
        opened += "notifications"
    }

    override fun openAutostartSettings(): AutostartOpenResult {
        opened += "autostart"
        return AutostartOpenResult.OPENED
    }

    fun clear() {
        opened.clear()
    }
}
