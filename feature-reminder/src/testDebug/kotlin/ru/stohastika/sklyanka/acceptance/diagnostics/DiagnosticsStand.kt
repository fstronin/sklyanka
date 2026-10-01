package ru.stohastika.sklyanka.acceptance.diagnostics

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.SweepReason
import ru.stohastika.sklyanka.domain.reminder.BuildReminderDiagnosticsUseCase
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckRule
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckState
import ru.stohastika.sklyanka.domain.reminder.ExportReminderJournalUseCase
import ru.stohastika.sklyanka.domain.reminder.ReadReminderReliabilityUseCase
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.schedule.PlanReason
import ru.stohastika.sklyanka.domain.schedule.PlanScope
import ru.stohastika.sklyanka.feature.reminder.DeliveryCheckController
import ru.stohastika.sklyanka.feature.reminder.DeliveryCheckNotice
import ru.stohastika.sklyanka.feature.reminder.DeliveryCheckScreen
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsScreen
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsUiState
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsViewModel
import ru.stohastika.sklyanka.feature.reminder.ReminderCheckNow
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.Instant
import java.time.LocalDateTime

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

    /**
     * Что объявляется баннером при входе (SKL-86): результат проверки или ничего.
     *
     * Стенд повторяет правило держателя `app` (`DeliveryCheckAnnouncer`): объявляется
     * завершённый исход, который ещё не подтверждён и не «не состоялась». Правило держится
     * в домене ([DeliveryCheckRule.announcementOf]), поэтому копией не расходится.
     */
    var announcement: DeliveryCheckState.Finished? = null
        private set

    fun reset(world: AcceptanceWorld) {
        if (lastWorld !== world) {
            lastWorld = world
            viewModel = null
            fixed = null
            backRequested = false
            announcement = null
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

    /**
     * Вход в приложение: истёкшая проверка закрывается, результат объявляется один раз.
     *
     * Затем снимок перечитывается — так же, как `MainActivity.onResume` и `SklyankaApp`
     * делают это в продукте.
     */
    fun enter(world: AcceptanceWorld) {
        reset(world)
        val model = model(world)
        runBlocking {
            val state = world.reminders.finishDeliveryCheck()
            announcement = DeliveryCheckRule.announcementOf(state, world.reminders.acknowledgements.announced())
        }
        model.refresh()
    }

    /** Человек подтвердил результат: дальше он не объявляется, в том числе после перезапуска. */
    fun acknowledge(world: AcceptanceWorld) {
        val finished = announcement ?: return
        runBlocking { world.reminders.acknowledgeDeliveryCheck(finished.dueAt) }
        announcement = null
    }

    /**
     * Карточка проверки так, как её поднимает система полноэкранным намерением (SKL-86).
     *
     * Карточка живёт своей Activity, и на стенде она компонуется отдельно: вопросы
     * к ней — состав и единственное действие, а не состояние экрана проверки.
     */
    fun showDeliveryCheckCard(world: AcceptanceWorld, at: LocalDateTime, question: ComposeUiTest.() -> Unit) {
        reset(world)
        runComposeUiTest {
            setContent {
                SklyankaTheme(dark = true) {
                    DeliveryCheckScreen(at = at, onAcknowledge = {})
                }
            }
            question()
            waitForIdle()
        }
    }

    @Composable
    private fun Content(world: AcceptanceWorld, onAction: (() -> Unit) -> Unit) {
        SklyankaTheme {
            Column {
                announcement?.let { finished ->
                    DeliveryCheckNotice(
                        state = finished,
                        onAcknowledge = { onAction { acknowledge(world) } },
                        onFix = { onAction { acknowledge(world) } },
                    )
                }
                DiagnosticsScreen(
                    state = state(world),
                    onCheckNow = { onAction { model(world).onCheckNow() } },
                    onExportJournal = { onAction { exportJournal(world) } },
                    // «Пройти настройку заново» подключает `app`; сценарии его не трогают.
                    onRestartSetup = null,
                    onToggleSupport = { onAction { model(world).onToggleSupport() } },
                    onFixPermission = { permission -> onAction { fixed = permission } },
                    onAlarmKindChange = { kind -> onAction { model(world).onAlarmKindChange(kind) } },
                    onCheckDelivery = { onAction { model(world).onCheckDelivery() } },
                    onDismissCheckSheet = { onAction { model(world).onDismissCheckSheet() } },
                    onStartDeliveryCheck = { onAction { model(world).onStartDeliveryCheck() } },
                    onCancelDeliveryCheck = { onAction { model(world).onCancelDeliveryCheck() } },
                    onRepeatDeliveryCheck = {
                        onAction {
                            model(world).onAcknowledgeDeliveryCheck()
                            model(world).onCheckDelivery()
                        }
                    },
                    onFixDeliveryCheck = {
                        onAction {
                            model(world).onAcknowledgeDeliveryCheck()
                            fixedSetup = true
                        }
                    },
                    onBack = { onAction { backRequested = true } },
                    toLocal = { instant: Instant -> instant.atZone(world.clock.zone).toLocalDateTime() },
                )
            }
        }
    }

    /** «Исправить» у блока проверки: результат прочитан, человек ушёл на «Настроить надёжность». */
    var fixedSetup: Boolean = false
        private set

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
            measurement = world.reminders.measurement,
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
        settings = world.reminders.settingsStore,
        deliveryCheck = StandDeliveryCheckController(world),
        time = world.clock.time,
    )

    /** Забыть подменённый главный диспетчер: стенд не должен влиять на соседние тесты. */
    fun dropMainDispatcher() {
        Dispatchers.resetMain()
    }
}

/**
 * Самопроверка доставки для стенда (SKL-86).
 *
 * Настоящие доменные сценарии на портах мира сценария: копии правила нет, а порядок
 * вызовов тот же, что собирает `app` в `DeliveryCheckScenarios`.
 */
private class StandDeliveryCheckController(private val world: AcceptanceWorld) : DeliveryCheckController {

    override suspend fun start(): DeliveryCheckState = world.reminders.startDeliveryCheck()

    override suspend fun finish(): DeliveryCheckState = world.reminders.finishDeliveryCheck()

    override suspend fun cancel() = world.reminders.cancelDeliveryCheck()

    override suspend fun acknowledge(dueAt: Instant) = world.reminders.acknowledgeDeliveryCheck(dueAt)
}
