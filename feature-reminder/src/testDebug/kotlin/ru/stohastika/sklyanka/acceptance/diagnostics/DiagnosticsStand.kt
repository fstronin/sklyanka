package ru.stohastika.sklyanka.acceptance.diagnostics

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.reminder.BuildReminderDiagnosticsUseCase
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckRule
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckState
import ru.stohastika.sklyanka.domain.reminder.ExportReminderJournalUseCase
import ru.stohastika.sklyanka.domain.reminder.ReadReminderReliabilityUseCase
import ru.stohastika.sklyanka.domain.reminder.ReminderIntake
import ru.stohastika.sklyanka.domain.reminder.ReminderSlot
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPlanSource
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.feature.reminder.DeliveryCheckController
import ru.stohastika.sklyanka.feature.reminder.DeliveryCheckNotice
import ru.stohastika.sklyanka.feature.reminder.DeliveryCheckScreen
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsScreen
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsUiState
import ru.stohastika.sklyanka.feature.reminder.DiagnosticsViewModel
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Стенд экрана диагностики напоминаний: настоящий `DiagnosticsViewModel` на мире сценария
 * (SKL-20, группа `@diagnostics`).
 *
 * Диагностика — часть продукта, а не отладочный экран, и её снимок собирает настоящий
 * `BuildReminderDiagnosticsUseCase` из тех же портов, что в `app`: журнала, будильников,
 * разрешений, плана и подтверждений самопроверки. Выгрузка журнала собирается настоящим
 * `ExportReminderJournalUseCase` на файловом порте мира: системный выбор места на стенде
 * играют шаги (`chooseJournalPlace`/`cancelJournalPick`), а не платформа.
 *
 * Подменённый главный диспетчер обязан сниматься прогоном (`dropMainDispatcher`).
 */
@OptIn(ExperimentalTestApi::class)
internal object DiagnosticsStand {

    private var lastWorld: AcceptanceWorld? = null
    private var viewModel: DiagnosticsViewModel? = null

    /** Разрешение, в системный экран которого привёл «Исправить»; null — не нажимали. */
    var fixed: ReminderPermission? = null
        private set

    /** Системная «Назад» нажата. */
    var backRequested: Boolean = false
        private set

    /** «Исправить» у блока проверки: результат прочитан, человек ушёл на «Настроить надёжность». */
    var fixedSetup: Boolean = false
        private set

    /** «Пройти настройку заново» под карточкой: человек ушёл на «Настроить надёжность» (AC-12). */
    var restartSetupRequested: Boolean = false
        private set

    /** Имя файла, которое экран назвал системе при нажатии; null — выбор ещё не запрошен. */
    var exportRequested: String? = null
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
            fixedSetup = false
            restartSetupRequested = false
            announcement = null
            exportRequested = null
            Dispatchers.setMain(UnconfinedTestDispatcher())
        }
    }

    fun model(world: AcceptanceWorld): DiagnosticsViewModel {
        reset(world)
        return viewModel ?: build(world).also { viewModel = it }
    }

    fun state(world: AcceptanceWorld): DiagnosticsUiState = model(world).state.value

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
                    // «Пройти настройку заново» подключает `app`; стенд фиксирует нажатие.
                    onRestartSetup = { onAction { restartSetupRequested = true } },
                    onFixPermission = { permission -> onAction { fixed = permission } },
                    onCheckDelivery = { onAction { model(world).onCheckDelivery() } },
                    onDismissCheckSheet = { onAction { model(world).onDismissCheckSheet() } },
                    onStartDeliveryCheck = { onAction { model(world).onStartDeliveryCheck() } },
                    onCancelDeliveryCheck = { onAction { model(world).onCancelDeliveryCheck() } },
                    onAcknowledgeDeliveryCheck = { onAction { model(world).onAcknowledgeDeliveryCheck() } },
                    onFixDeliveryCheck = {
                        onAction {
                            model(world).onAcknowledgeDeliveryCheck()
                            fixedSetup = true
                        }
                    },
                    onJournalExport = {
                        onAction { exportRequested = model(world).journalExportFileName() }
                    },
                    onBack = { onAction { backRequested = true } },
                    toLocal = { instant: Instant -> instant.atZone(world.clock.zone).toLocalDateTime() },
                )
            }
        }
    }

    /**
     * Вопрос к собранной композиции.
     *
     * Технического блока «Для поддержки» на первом уровне больше нет (ОВ-126), поэтому
     * `ask`/`act` совпадают с `askRaw`/`actRaw`; имена оставлены, чтобы смежные шаги
     * не переписывать.
     */
    fun ask(world: AcceptanceWorld, question: ComposeUiTest.() -> Unit) {
        askRaw(world, question)
    }

    /** Вопрос к композиции без раскрытия техблока. */
    fun askRaw(world: AcceptanceWorld, question: ComposeUiTest.() -> Unit) {
        reset(world)
        runComposeUiTest {
            setContent { Content(world, onAction = {}) }
            question()
            waitForIdle()
        }
    }

    /** Действие человека: нажатия копятся и применяются после композиции. */
    fun act(world: AcceptanceWorld, action: ComposeUiTest.() -> Unit) {
        actRaw(world, action)
    }

    /** Действие человека без раскрытия техблока. */
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

    /**
     * Системная «Назад»: нажатие идёт через настоящий диспетчер окна стенда (Р-54).
     *
     * Шторка проверки доставки действия в шапке больше не несёт, и её выход наблюдаем
     * только так: обработчик возврата — её собственный `BackHandler`, а диспетчер даёт
     * композиция. Раньше шаг нажимал слово «Отмена» из шапки; способ изменился, поведение
     * закрытия — нет.
     */
    fun pressSystemBack(world: AcceptanceWorld) {
        reset(world)
        val performed = mutableListOf<() -> Unit>()
        runComposeUiTest {
            var back: OnBackPressedDispatcher? = null
            setContent {
                back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
                Content(world, onAction = { performed += it })
            }
            waitForIdle()
            requireNotNull(back) { "the stand must run inside a composition with a back dispatcher" }.onBackPressed()
            waitForIdle()
        }
        performed.forEach { it() }
    }

    /** Технического блока больше нет: вызов оставлен ради смежных шагов. */
    @Suppress("UNUSED_PARAMETER")
    fun expandSupport(world: AcceptanceWorld) = Unit

    private fun build(world: AcceptanceWorld): DiagnosticsViewModel {
        // Перерыв, названный Предысторией, стенд превращает в ожидавшееся срабатывание,
        // которого не было: экран считает перерыв тем же `OutageDetector`, что и продукт.
        val outage = world.outageSince
        return DiagnosticsViewModel(
            diagnostics = BuildReminderDiagnosticsUseCase(
                journal = world.reminders.journal,
                alarms = world.reminders.alarms,
                permissions = world.system,
                plan = outage?.let { OutagePlanSource(world.reminders.planSource(), it, world.clock.zone) }
                    ?: world.reminders.planSource(),
                reliability = ReadReminderReliabilityUseCase(world.system),
                medications = world.repositories.medicationStore,
                measurement = world.reminders.measurement,
                acknowledgements = world.reminders.acknowledgements,
                time = outage?.let { OutageTimeSource(world.clock.time, it) } ?: world.clock.time,
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
        )
    }

    /** Человек выбрал место в системном диалоге: экран получает handle и пишет журнал. */
    fun chooseJournalPlace(world: AcceptanceWorld, handle: String = "memory://document") {
        model(world).onJournalExportResult(handle)
    }

    /** Человек отменил выбор: исход — молчание, не «не сохранено» (ПЕР-31). */
    fun cancelJournalPick(world: AcceptanceWorld) {
        model(world).onJournalExportResult(null)
    }

    /** Забыть подменённый главный диспетчер: стенд не должен влиять на соседние тесты. */
    fun dropMainDispatcher() {
        Dispatchers.resetMain()
    }
}

/**
 * Время для сборки снимка, когда Предыстория назвала перерыв: момент сдвинут за начало
 * перерыва, чтобы `OutageDetector` увидел несостоявшееся срабатывание. Остальной мир живёт
 * по своим часам — сдвиг нужен только правилу перерыва.
 */
private class OutageTimeSource(private val base: TimeSource, outage: Instant) : TimeSource {

    private val now: Instant = maxOf(base.now(), outage.plus(OUTAGE_GRACE))

    override fun now(): Instant = now

    override fun zone(): ZoneId = base.zone()

    private companion object {
        val OUTAGE_GRACE: Duration = Duration.ofMinutes(5)
    }
}

/**
 * План для сборки снимка при названном перерыве: добавляет к настоящим приёмам один
 * ожидавшийся, но не сработавший — на момент начала перерыва. Названий и доз он не несёт:
 * диагностика разрешает имя только для последнего и следующего напоминания (НФТ-19).
 */
private class OutagePlanSource(
    private val delegate: ReminderPlanSource,
    private val since: Instant,
    private val zone: ZoneId,
) : ReminderPlanSource {

    override suspend fun candidates(from: LocalDateTime, toInclusive: LocalDateTime): List<ReminderIntake> {
        val at = since.atZone(zone).toLocalDateTime()
        val missed = if (!at.isBefore(from) && !at.isAfter(toInclusive)) listOf(missedFiring(at)) else emptyList()
        return delegate.candidates(from, toInclusive) + missed
    }

    override suspend fun intake(intakeId: String): ReminderIntake? = delegate.intake(intakeId)

    override suspend fun pendingInSlot(slot: ReminderSlot): List<ReminderIntake> = delegate.pendingInSlot(slot)

    private fun missedFiring(at: LocalDateTime): ReminderIntake = ReminderIntake(
        intakeId = OUTAGE_INTAKE_ID,
        medicationId = OUTAGE_MEDICATION_ID,
        profileId = OUTAGE_PROFILE_ID,
        scheduledAt = at,
        status = IntakeStatus.PENDING,
        scheduleMode = ScheduleMode.SCHEDULED,
        medicationArchived = false,
        medicationDeleted = false,
        profileIsSelf = true,
        profileMirrorsReminders = false,
    )

    private companion object {
        const val OUTAGE_INTAKE_ID = "outage-expected"
        const val OUTAGE_MEDICATION_ID = "outage-medication"
        const val OUTAGE_PROFILE_ID = "outage-profile"
    }
}
