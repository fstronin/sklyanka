package ru.heuri.sklyanka.acceptance.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.intake.SweepReason
import ru.heuri.sklyanka.domain.reminder.BuildReminderDiagnosticsUseCase
import ru.heuri.sklyanka.domain.reminder.ExportReminderJournalUseCase
import ru.heuri.sklyanka.domain.reminder.ReadReminderReliabilityUseCase
import ru.heuri.sklyanka.domain.reminder.TestReminderUseCase
import ru.heuri.sklyanka.domain.schedule.PlanReason
import ru.heuri.sklyanka.domain.schedule.PlanScope
import ru.heuri.sklyanka.feature.reminder.DiagnosticsScreen
import ru.heuri.sklyanka.feature.reminder.DiagnosticsUiState
import ru.heuri.sklyanka.feature.reminder.DiagnosticsViewModel
import ru.heuri.sklyanka.feature.reminder.ReminderCheckNow
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import java.time.Instant

/**
 * Стенд экрана диагностики напоминаний: настоящий `DiagnosticsViewModel` на мире сценария
 * (SKL-20, группа `@diagnostics`).
 *
 * Диагностика — часть продукта, а не отладочный экран, и её снимок собирает настоящий
 * `BuildReminderDiagnosticsUseCase` из тех же портов, что в `app`: журнала, будильников,
 * разрешений и плана. «Проверить сейчас» идёт тем же полным обходом, что
 * `ReminderPlanningTrigger` (НАП-11): проход по просроченным, генерация окна, пересборка
 * будильников. Выгрузка журнала — тем же файловым портом, что перенос данных (НФТ-51).
 *
 * Подменённый главный диспетчер обязан сниматься прогоном (`dropMainDispatcher`).
 */
@OptIn(ExperimentalTestApi::class)
internal object DiagnosticsStand {

    /** Место выгрузки журнала в стенде: системный выбор места — порт платформы (НФТ-17). */
    const val JOURNAL_HANDLE = "chosen://journal"

    private var lastWorld: AcceptanceWorld? = null
    private var viewModel: DiagnosticsViewModel? = null

    fun reset(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            lastWorld = world
            viewModel = null
            Dispatchers.setMain(UnconfinedTestDispatcher())
        }
    }

    fun model(world: AcceptanceWorld): DiagnosticsViewModel {
        reset(world)
        return viewModel ?: build(world).also { viewModel = it }
    }

    fun state(world: AcceptanceWorld): DiagnosticsUiState = model(world).state.value

    /** Системный выбор места вернул ссылку — выгрузка идёт настоящим сценарием (НФТ-51). */
    fun exportJournal(world: AcceptanceWorld, handle: String = JOURNAL_HANDLE) {
        model(world).onExportTo(handle)
    }

    @Composable
    private fun Content(world: AcceptanceWorld, onAction: (() -> Unit) -> Unit) {
        SklyankaTheme {
            DiagnosticsScreen(
                state = state(world),
                onCheckNow = { onAction { model(world).onCheckNow() } },
                onTestReminder = { onAction { model(world).onTestReminder() } },
                onExportJournal = { onAction { exportJournal(world) } },
                // «Пройти настройку заново» подключает `app`; сценарии SKL-20 его не трогают.
                onRestartSetup = null,
                onBack = {},
                toLocal = { instant: Instant -> instant.atZone(world.clock.zone).toLocalDateTime() },
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

    private fun build(world: AcceptanceWorld): DiagnosticsViewModel = DiagnosticsViewModel(
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
            // Тот же порядок, что у `ReminderPlanningTrigger.planAll`: просроченные,
            // генерация окна, пересборка будильников (НАП-11, НАП-34).
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
