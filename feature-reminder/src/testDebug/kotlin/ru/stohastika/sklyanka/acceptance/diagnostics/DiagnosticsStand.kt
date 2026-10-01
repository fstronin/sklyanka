package ru.stohastika.sklyanka.acceptance.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.SweepReason
import ru.stohastika.sklyanka.domain.reminder.BuildReminderDiagnosticsUseCase
import ru.stohastika.sklyanka.domain.reminder.ExportReminderJournalUseCase
import ru.stohastika.sklyanka.domain.reminder.ReadReminderReliabilityUseCase
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.schedule.PlanReason
import ru.stohastika.sklyanka.domain.schedule.PlanScope
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsScreen
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsUiState
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsViewModel
import ru.stohastika.sklyanka.feature.reminder.ReminderCheckNow
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
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

    /** Разрешение, в системный экран которого привёл «Исправить»; null — не нажимали. */
    var fixed: ReminderPermission? = null
        private set

    /** Системная «Назад» нажата. */
    var backRequested: Boolean = false
        private set

    fun reset(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            lastWorld = world
            viewModel = null
            fixed = null
            backRequested = false
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

    /** Раскрыть технический блок «Для поддержки», если он свёрнут (SKL-79). */
    fun expandSupport(world: AcceptanceWorld) {
        if (!state(world).supportExpanded) model(world).onToggleSupport()
    }

    /** Перечитать снимок: человек вернулся из системных настроек (НФТ-52). */
    fun refresh(world: AcceptanceWorld) {
        model(world).refresh()
    }

    @Composable
    private fun Content(world: AcceptanceWorld, onAction: (() -> Unit) -> Unit) {
        SklyankaTheme {
            DiagnosticsScreen(
                state = state(world),
                onCheckNow = { onAction { model(world).onCheckNow() } },
                onExportJournal = { onAction { exportJournal(world) } },
                // «Пройти настройку заново» подключает `app`; сценарии его не трогают.
                onRestartSetup = null,
                onToggleSupport = { onAction { model(world).onToggleSupport() } },
                onFixPermission = { permission -> onAction { fixed = permission } },
                onBack = { onAction { backRequested = true } },
                toLocal = { instant: Instant -> instant.atZone(world.clock.zone).toLocalDateTime() },
            )
        }
    }

    /**
     * Вопрос к собранной композиции с раскрытым техблоком.
     *
     * Сценарии SKL-13/SKL-20 говорят о технических деталях (журнал, счётчики, секунды
     * отклонения), и блок «Для поддержки» раскрывается перед вопросом: иначе их проверки
     * видели бы только первый уровень. Свёрнутость блока проверяют сценарии SKL-79
     * через [askRaw].
     */
    fun ask(world: AcceptanceWorld, question: ComposeUiTest.() -> Unit) {
        expandSupport(world)
        askRaw(world, question)
    }

    /** Вопрос к композиции без раскрытия техблока (SKL-79: «содержимое свёрнуто»). */
    fun askRaw(world: AcceptanceWorld, question: ComposeUiTest.() -> Unit) {
        reset(world)
        runComposeUiTest {
            setContent { Content(world, onAction = {}) }
            question()
            waitForIdle()
        }
    }

    /** Действие человека с раскрытым техблоком: нажатия копятся и применяются после композиции. */
    fun act(world: AcceptanceWorld, action: ComposeUiTest.() -> Unit) {
        expandSupport(world)
        actRaw(world, action)
    }

    /** Действие человека без раскрытия техблока (SKL-79: нажатие «Для поддержки»). */
    fun actRaw(world: AcceptanceWorld, action: ComposeUiTest.() -> Unit) {
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
            medications = world.repositories.medicationStore,
            time = world.clock.time,
        ),
        check = ReminderCheckNow { reason ->
            // Сценарий отказа: проверка не может завершиться (SKL-79, НФТ-8).
            check(!world.failReminderCheck) { "the manual check could not finish" }
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
