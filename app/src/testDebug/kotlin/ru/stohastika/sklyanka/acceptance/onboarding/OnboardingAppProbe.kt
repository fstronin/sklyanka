package ru.stohastika.sklyanka.acceptance.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.stohastika.sklyanka.acceptance.profile.TodayFromWorld
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.onboarding.ConfirmReliabilityStepUseCase
import ru.stohastika.sklyanka.domain.onboarding.LeaveOnboardingUseCase
import ru.stohastika.sklyanka.domain.onboarding.OnboardingSource
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.ReadPermissionChecklistUseCase
import ru.stohastika.sklyanka.domain.onboarding.StepOpenResult
import ru.stohastika.sklyanka.domain.onboarding.VendorProfile
import ru.stohastika.sklyanka.domain.reminder.BuildReminderDiagnosticsUseCase
import ru.stohastika.sklyanka.domain.reminder.ExportReminderJournalUseCase
import ru.stohastika.sklyanka.domain.reminder.ReadReminderReliabilityUseCase
import ru.stohastika.sklyanka.domain.reminder.ReminderOutage
import ru.stohastika.sklyanka.feature.onboarding.OnboardingUiState
import ru.stohastika.sklyanka.feature.onboarding.OnboardingViewModel
import ru.stohastika.sklyanka.feature.onboarding.PermissionsActions
import ru.stohastika.sklyanka.feature.onboarding.PermissionsScreen
import ru.stohastika.sklyanka.feature.onboarding.SystemSettingsNavigator
import ru.stohastika.sklyanka.feature.onboarding.TAG_CHECK_DELIVERY
import ru.stohastika.sklyanka.feature.reminder.DeliveryCheckController
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsScreen
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsUiState
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsViewModel
import ru.stohastika.sklyanka.feature.reminder.ReminderOutageNotice
import ru.stohastika.sklyanka.feature.reminder.TAG_CHECK_START
import ru.stohastika.sklyanka.feature.today.TodayScreen
import ru.stohastika.sklyanka.feature.today.TodayUiState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.Instant
import ru.stohastika.sklyanka.feature.reminder.R as ReminderR

/**
 * Стенд композиции онбординга (SKL-14, группа `@composition`).
 *
 * Здесь собирается настоящий `OnboardingViewModel` на мире сценария и настоящий экран
 * «Настроить надёжность»; рядом — «Сегодня», настройки и диагностика, потому что сценарии
 * говорят о нескольких экранах сразу (`SKL-26/TDD.md §4.3`). Экраны те же, что у продукта;
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

    /** Системные переходы: кто открывался. */
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

    fun exit(world: AcceptanceWorld): Unit? = model(world).exit.value

    fun open(world: AcceptanceWorld, source: OnboardingSource, outageSince: Instant? = null) {
        model(world).start(source, outageSince)
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
                    onManualConfirmed = { step, confirmed ->
                        onAction { model(world).onManualConfirmed(step, confirmed) }
                    },
                    onNext = { onAction { model(world).onNext() } },
                    onSkip = { onAction { model(world).onSkip() } },
                    onDone = { onAction { model(world).onDone() } },
                    // «Проверить доставку» из плашки «Всё настроено» (SKL-86): app открывает
                    // «Проверку напоминаний» с уже поднятой шторкой.
                    onCheckDelivery = { onAction { openCheckDelivery(world) } },
                ),
                toLocal = { instant -> instant.atZone(world.clock.zone).toLocalDateTime() },
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

    /**
     * «Исправить» в сообщении о перерыве — тем же маршрутом, что в `MainActivity` (ОВ-107).
     *
     * Сообщение — настоящий `ReminderOutageNotice`; нажатие открывает экран настройки
     * с источником `OUTAGE_BANNER` и моментом перерыва: промежуточной диагностики
     * в маршруте нет.
     */
    fun pressFixInTheOutageNotice(world: AcceptanceWorld) {
        reset(world)
        val since = requireNotNull(world.outageSince) { "the scenario must name the outage moment" }
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    ReminderOutageNotice(
                        outage = ReminderOutage(since = since, missed = 1),
                        onFix = { OnboardingAppProbe.open(world, OnboardingSource.OUTAGE_BANNER, since) },
                        toLocal = { instant -> instant.atZone(world.clock.zone).toLocalDateTime() },
                    )
                }
            }
            val action = ApplicationProvider.getApplicationContext<android.content.Context>()
                .getString(ReminderR.string.reminder_outage_notice_action)
            onNodeWithText(action).performClick()
            waitForIdle()
        }
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

    /**
     * «Проверить доставку» из плашки «Всё настроено» (SKL-86, ОВ-108).
     *
     * Нажатие ведёт тем же путём, что `app`: открывается «Проверка напоминаний»
     * с поднятой шторкой. Здесь это записывается состоянием, а не Activity: стенд
     * проверяет решение маршрута, а не системы.
     */
    var checkDeliveryOpened: Boolean = false
        private set

    fun pressCheckDelivery(world: AcceptanceWorld) {
        reset(world)
        checkDeliveryOpened = false
        val performed = mutableListOf<() -> Unit>()
        runComposeUiTest {
            setContent { PermissionsContent(world, onAction = { performed += it }) }
            onNodeWithTag(TAG_CHECK_DELIVERY).performScrollTo().performClick()
            waitForIdle()
        }
        performed.forEach { it() }
    }

    private fun openCheckDelivery(world: AcceptanceWorld) {
        checkDeliveryOpened = true
        diagnosticsModel(world).onCheckDelivery()
    }

    private fun diagnosticsModel(world: AcceptanceWorld): DiagnosticsViewModel =
        diagnostics ?: buildDiagnostics(world).also { diagnostics = it }

    /** Экран проверки напоминаний, открытый из плашки: шторка уже поднята. */
    fun askCheckScreen(world: AcceptanceWorld, question: ComposeUiTest.() -> Unit) {
        reset(world)
        val model = diagnosticsModel(world)
        val snapshot = model.state.value
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    DiagnosticsScreen(
                        state = snapshot,
                        onRestartSetup = null,
                        onFixPermission = {},
                        onCheckDelivery = {},
                        onDismissCheckSheet = {},
                        onStartDeliveryCheck = { model.onStartDeliveryCheck() },
                        onCancelDeliveryCheck = {},
                        onAcknowledgeDeliveryCheck = {},
                        onFixDeliveryCheck = {},
                        onJournalExport = {},
                        onBack = {},
                        toLocal = { instant: Instant -> instant.atZone(world.clock.zone).toLocalDateTime() },
                    )
                }
            }
            question()
            waitForIdle()
        }
    }

    /** «Начать проверку» в поднятой шторке: тем же доменным путём, что у продукта. */
    fun startCheckFromSheet(world: AcceptanceWorld) {
        reset(world)
        val model = diagnosticsModel(world)
        val snapshot = model.state.value
        runComposeUiTest {
            setContent {
                SklyankaTheme {
                    DiagnosticsScreen(
                        state = snapshot,
                        onRestartSetup = null,
                        onFixPermission = {},
                        onCheckDelivery = {},
                        onDismissCheckSheet = {},
                        onStartDeliveryCheck = { model.onStartDeliveryCheck() },
                        onCancelDeliveryCheck = {},
                        onAcknowledgeDeliveryCheck = {},
                        onFixDeliveryCheck = {},
                        onJournalExport = {},
                        onBack = {},
                        toLocal = { instant: Instant -> instant.atZone(world.clock.zone).toLocalDateTime() },
                    )
                }
            }
            onNodeWithTag(TAG_CHECK_START).performClick()
            waitForIdle()
        }
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
                        onRestartSetup = null,
                        onFixPermission = {},
                        onCheckDelivery = {},
                        onDismissCheckSheet = {},
                        onStartDeliveryCheck = {},
                        onCancelDeliveryCheck = {},
                        onAcknowledgeDeliveryCheck = {},
                        onFixDeliveryCheck = {},
                        onJournalExport = {},
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
            time = world.clock.time,
            vendor = world.onboarding,
        ),
        confirmStep = ConfirmReliabilityStepUseCase(world.onboarding, world.onboarding),
        leaveOnboarding = LeaveOnboardingUseCase(world.onboarding, world.onboarding),
        navigator = navigator,
        log = world.onboarding,
        // Аналитика стенду не подотчётна: настоящий рекордер поверх подмен в памяти,
        // чтобы проверяемое поведение экрана осталось тем же, что в продукте.
        recorder = world.analytics.recorder(CoroutineScope(Dispatchers.Main)),
        time = world.clock.time,
    )

    private fun buildDiagnostics(world: AcceptanceWorld): DiagnosticsViewModel = DiagnosticsViewModel(
        diagnostics = BuildReminderDiagnosticsUseCase(
            journal = world.reminders.journal,
            alarms = world.reminders.alarms,
            permissions = world.system,
            plan = world.reminders.planSource(),
            reliability = ReadReminderReliabilityUseCase(world.system),
            medications = world.repositories.medicationStore,
            measurement = world.reminders.measurement,
            acknowledgements = world.reminders.acknowledgements,
            time = world.clock.time,
        ),
        deliveryCheck = object : DeliveryCheckController {
            override suspend fun start() = world.reminders.startDeliveryCheck()

            override suspend fun finish() = world.reminders.finishDeliveryCheck()

            override suspend fun cancel() = world.reminders.cancelDeliveryCheck()

            override suspend fun acknowledge(dueAt: Instant) = world.reminders.acknowledgeDeliveryCheck(dueAt)
        },
        exportJournal = ExportReminderJournalUseCase(
            journal = world.reminders.journal,
            files = world.transfer.files,
            time = world.clock.time,
        ),
        time = world.clock.time,
        // Стенд проверяет экран, а не события: подмены те же, что у соседних экранов.
        analytics = world.analytics.recorder(CoroutineScope(Dispatchers.Unconfined)),
    )

    /** Забыть подменённый главный диспетчер: стенд не должен влиять на соседние тесты. */
    fun dropMainDispatcher() {
        Dispatchers.resetMain()
    }
}

/** Системные переходы в памяти: записаны — и ничего не открывают. */
internal class AppRecordingNavigator : SystemSettingsNavigator {

    val opened: MutableList<String> = mutableListOf()

    var appStartupResult: StepOpenResult = StepOpenResult.OPENED
    var batterySavingResult: StepOpenResult = StepOpenResult.OPENED

    override fun openExactAlarmSettings(): StepOpenResult {
        opened += "exact-alarms"
        return StepOpenResult.OPENED
    }

    override fun openNotificationSettings(channelId: String?): StepOpenResult {
        opened += "notifications"
        return StepOpenResult.OPENED
    }

    override fun openFullScreenIntentSettings(): StepOpenResult {
        opened += "full-screen"
        return StepOpenResult.OPENED
    }

    override fun openVendorStep(profile: VendorProfile, step: PermissionStep): StepOpenResult = when (step) {
        PermissionStep.APP_STARTUP -> {
            opened += "app-startup"
            appStartupResult
        }

        PermissionStep.BATTERY_SAVING -> {
            opened += "battery-saving"
            batterySavingResult
        }

        // У закрепления в недавних системного экрана нет, и сюда оно не приходит.
        else -> StepOpenResult.OPENED
    }

    fun clear() {
        opened.clear()
        appStartupResult = StepOpenResult.OPENED
        batterySavingResult = StepOpenResult.OPENED
    }
}
