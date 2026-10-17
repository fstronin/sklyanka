package ru.stohastika.sklyanka.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.intake.CreatePrnIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.MarkIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.MarkRequest
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.intake.PrnOutcome
import ru.stohastika.sklyanka.domain.intake.UndoMarkUseCase
import ru.stohastika.sklyanka.domain.intake.UndoRequest
import ru.stohastika.sklyanka.domain.medication.UndoDeleteMedicationUseCase
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.reminder.ReadReminderReliabilityUseCase
import ru.stohastika.sklyanka.domain.startup.ExplainDatabaseResetUseCase
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.today.GroupAction
import ru.stohastika.sklyanka.domain.today.GroupMarkOutcome
import ru.stohastika.sklyanka.domain.today.MarkIntakeGroupUseCase
import ru.stohastika.sklyanka.domain.today.ObserveTodayAgendaUseCase
import ru.stohastika.sklyanka.domain.today.PartOfDay
import ru.stohastika.sklyanka.domain.today.TodayAgenda
import ru.stohastika.sklyanka.domain.undo.UndoableActionStore
import ru.stohastika.sklyanka.domain.undo.UndoableDeletion
import ru.stohastika.sklyanka.domain.undo.UndoableMark
import java.time.Duration
import java.time.LocalTime
import javax.inject.Inject

/**
 * Экран «Сегодня»: один источник состояния, одно направление данных.
 *
 * Оптимистичного состояния здесь нет (`TDD.md §9.1`): локальная запись в SQLite
 * укладывается в бюджет отклика 150 мс (НФТ-22) с запасом, а вторая версия правды
 * разошлась бы с идемпотентностью при отметке с часов (ПР-2, ПР-3).
 *
 * Отметка идёт только через [MarkIntakeUseCase] и построенный над ним
 * [MarkIntakeGroupUseCase] — второго пути записи отметки в приложении нет (ЧАС-24).
 * Отмена — через [UndoMarkUseCase], тоже единственная точка.
 *
 * Окно отмены (SKL-30): после состоявшейся отметки сюда приходит [UndoableMark], экран
 * показывает снэкбар и живёт по дедлайну из магазина. Уход с экрана заканчивает окно.
 */
@HiltViewModel
// Сценарии экрана — контракт его возможностей: разбивка на группы скрыла бы одну
// из них за «набором», а полей меньше восьми здесь быть не может (SKL-30).
@Suppress("LongParameterList")
class TodayViewModel @Inject constructor(
    observeAgenda: ObserveTodayAgendaUseCase,
    private val markIntake: MarkIntakeUseCase,
    private val markGroup: MarkIntakeGroupUseCase,
    private val createPrn: CreatePrnIntakeUseCase,
    private val undoMark: UndoMarkUseCase,
    private val undoActions: UndoableActionStore,
    /** Возврат удаления лекарства в коротком окне (SKL-99, ОВ-124). */
    private val undoDelete: UndoDeleteMedicationUseCase,
    private val time: TimeSource,
    /**
     * Надёжность напоминаний читается при каждом построении состояния, а не кешируется:
     * отозванное разрешение отражается без перезапуска (НФТ-52). Сценарий читает систему
     * синхронно, поэтому вызов стоит в [screen] — втором места, кроме диагностики,
     * где продукт спрашивает систему о разрешениях, не появляется.
     */
    private val readReliability: ReadReminderReliabilityUseCase,
    /**
     * Объяснение сброса данных после повреждения базы (МГ-8, Р-87 п. 2): старт экрана
     * спрашивает, не пересоздана ли база на жившей отметке, и показывает баннер один раз.
     */
    private val explainDatabaseReset: ExplainDatabaseResetUseCase,
) : ViewModel() {

    private val view = MutableStateFlow(ViewState())

    /**
     * Поводы перечитать разрешения, пока экран жив (SKL-48).
     *
     * Очередь на один сигнал: повод — это «прочитай сейчас», а не значение. Без
     * подписчика сигнал не копится: первый кадр после подписки читает состояние сам.
     */
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Таймер текущего окна: замена отметки обязана погасить прошлый, а не ждать его. */
    private var undoJob: Job? = null

    /** Таймер подтверждения: повторный арм обязан погасить прошлый срок, а не ждать его. */
    private var confirmJob: Job? = null

    val state: StateFlow<TodayUiState> = combine(
        observeAgenda(),
        view,
        reliabilityReads(),
    ) { agenda, current, _ ->
        screen(agenda, current)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), TodayUiState.Opening)

    /**
     * Перечитать разрешения, не дожидаясь изменения повестки (SKL-48, НФТ-52).
     *
     * Зовётся возвратом в приложение: экран мог остаться живым, пока человек ходил
     * в системные настройки, и без этого вызова предупреждение осталось бы отозванным
     * (наблюдение smoke-прогона 30.09.2026).
     */
    fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    /**
     * Поводы перечитать признак надёжности: первый — по подписке, дальше — минута
     * за минутой, плюс [refresh] с возврата в приложение.
     *
     * Отдельным потоком, а не снятием `distinctUntilChanged` у повестки: повестка —
     * значения, и минутный такт, не изменивший карточек, до сборки состояния не
     * доходит намеренно. Поток живёт внутри `stateIn` и без подписчика не работает —
     * фоновой работы не добавляется (НФТ-27).
     */
    private fun reliabilityReads(): Flow<Unit> = merge(
        flow { emit(Unit) },
        flow {
            while (true) {
                delay(RELIABILITY_TICK_MS)
                emit(Unit)
            }
        },
        refreshRequests,
    )

    init {
        viewModelScope.launch {
            undoActions.current.collect { action ->
                undoJob?.cancel()
                undoJob = null
                when (action) {
                    null -> view.update { it.copy(undo = null, deletion = null) }
                    is UndoableMark -> showUndo(action)
                    is UndoableDeletion -> showDeletion(action)
                }
            }
        }
        viewModelScope.launch {
            // Один раз на процесс: признак пересоздания снимается первым чтением, а
            // незакрытое объяснение переживает поворот и возврат на экран (МГ-8).
            if (explainDatabaseReset.explanationOnStartup()) {
                view.update { it.copy(databaseResetExplanation = true) }
            }
        }
    }

    /**
     * Подтверждение групповой отметки снимает любое намерение, кроме арма: следующий
     * шаг человека — отказ от вопроса, а не ответ на него (ОВ-32). Переходы наружу
     * состояния не трогают и разбираются маршрутом — их гасит уход с экрана.
     */
    fun onIntent(intent: TodayIntent) {
        when (intent) {
            is TodayIntent.MarkTaken -> {
                clearConfirmation()
                mark(intent.intakeId, MarkResult.TAKEN)
            }

            is TodayIntent.MarkSkipped -> {
                clearConfirmation()
                mark(intent.intakeId, MarkResult.SKIPPED)
            }

            is TodayIntent.MarkGroupTaken -> {
                // Второе нажатие батча: подтверждение снимается до запуска отметки,
                // путь отметки группы не меняется.
                clearConfirmation()
                viewModelScope.launch {
                    markTheGroup(intent.intakeIds)
                }
            }

            is TodayIntent.ArmGroupMark -> armGroup(intent.time)

            TodayIntent.DismissGroupConfirm -> clearConfirmation()

            TodayIntent.DismissResetExplanation -> dismissDatabaseReset()

            is TodayIntent.UndoMark -> {
                clearConfirmation()
                undo(intent.intakeIds)
            }

            is TodayIntent.UndoDeletion -> {
                clearConfirmation()
                undoDeletion(intent.medicationId)
            }

            is TodayIntent.TakePrn -> {
                clearConfirmation()
                viewModelScope.launch {
                    // Окно отмены открывается только по состоявшейся записи: отказ лимита —
                    // не новое действие, и снимать в нём нечего (Р-46, SKL-127).
                    val outcome = createPrn(intent.medicationId, IntakeSource.PHONE)
                    if (outcome is PrnOutcome.Created) {
                        undoActions.publishMark(
                            intakeIds = listOf(outcome.intake.id),
                            result = MarkResult.TAKEN,
                            medicationNames = listOfNotNull(prnMedicationName(outcome.intake.medicationId)),
                        )
                    }
                }
            }

            is TodayIntent.ToggleSection -> {
                clearConfirmation()
                view.update { it.withToggledSection(intent.partOfDay) }
            }

            TodayIntent.TogglePrnBlock -> {
                clearConfirmation()
                view.update { it.copy(prnCollapsed = !it.prnCollapsed) }
            }

            // Переходы наружу состояния экрана не меняют: их разбирает маршрут.
            is TodayIntent.OpenMedication,
            is TodayIntent.OpenStock,
            TodayIntent.AddMedication,
            TodayIntent.OpenMedicationList,
            TodayIntent.OpenSettings,
            TodayIntent.OpenPermissions,
            TodayIntent.OpenHistory,
            -> Unit
        }
    }

    /**
     * «Закрыть» на объяснении сброса: событие признаётся в постоянных отметках и
     * больше не показывается; следующее пересоздание базы заведёт новое (МГ-8).
     */
    private fun dismissDatabaseReset() {
        view.update { it.copy(databaseResetExplanation = false) }
        viewModelScope.launch { explainDatabaseReset.dismissExplanation() }
    }

    /**
     * Конец окна отмены и подтверждения при уходе с «Сегодня».
     *
     * Уход — событие жизненного цикла маршрута, а не действие человека: его зовёт
     * `TodayRoute`, а не намерение (`TDD.md §5.4`).
     */
    fun onScreenLeft() {
        clearConfirmation()
        undoJob?.cancel()
        undoJob = null
        view.update { it.copy(undo = null, deletion = null) }
        undoActions.clear()
    }

    /**
     * Арм батч-кнопки: заряжает подтверждение и запускает его срок.
     *
     * Повторный арм перезапускает срок — прошлый таймер гасится. Дедлайна в состоянии
     * нет: подтверждение не переживает уход с экрана и поворот (TDD §16.3), поэтому
     * окно меряется монотонными секундами `viewModelScope`, а не `TimeSource`.
     */
    private fun armGroup(time: LocalTime) {
        confirmJob?.cancel()
        view.update { it.copy(confirmingGroup = time) }
        confirmJob = viewModelScope.launch {
            delay(CONFIRM_WINDOW_MS)
            confirmJob = null
            view.update { it.copy(confirmingGroup = null) }
        }
    }

    /**
     * Снятие подтверждения молча: приёмы не трогаются, записи в домен не идут.
     *
     * Идемпотентно: снятие намерением и уход с экрана не будят поток, когда
     * подтверждения и не было.
     */
    private fun clearConfirmation() {
        confirmJob?.cancel()
        confirmJob = null
        view.update { current ->
            if (current.confirmingGroup == null) current else current.copy(confirmingGroup = null)
        }
    }

    private fun mark(intakeId: String, result: MarkResult) {
        viewModelScope.launch {
            val outcome = markIntake(MarkRequest(intakeId = intakeId, result = result, source = IntakeSource.PHONE))
            // Публикуется только состоявшаяся отметка: отброшенная — не новое действие,
            // и срок снэкбара идёт от единственной состоявшейся (приёмка, строка 203).
            if (outcome.isCompleted) {
                val recorded = outcome.intake?.status?.let(MarkResult::of) ?: result
                undoActions.publishMark(
                    intakeIds = listOf(intakeId),
                    // Текст говорит о записанном исходе, а не о нажатой кнопке: при
                    // замещении они расходятся (TDD.md §15.2).
                    result = recorded,
                    medicationNames = listOfNotNull(medicationName(intakeId)),
                )
            }
        }
    }

    private suspend fun markTheGroup(intakeIds: List<String>) {
        when (val outcome = markGroup(intakeIds, IntakeSource.PHONE)) {
            // Отменяются ровно изменившиеся приёмы: отмеченный часами в действие
            // не входит, и снять чужое решение отмена не имеет права (ПР-3).
            is GroupMarkOutcome.Applied -> if (outcome.changedIntakeIds.isNotEmpty()) {
                undoActions.publishMark(
                    intakeIds = outcome.changedIntakeIds,
                    result = MarkResult.TAKEN,
                    medicationNames = outcome.changedIntakeIds.mapNotNull(::medicationName),
                )
            }

            is GroupMarkOutcome.Rejected -> Unit
        }
    }

    /**
     * «Отменить»: окно закрывается синхронно, до запуска самой отмены.
     *
     * Новая отметка в тот же миг обязана открыть новое окно, а не ждать, пока
     * завершится отмена предыдущей. Окно закрывается и при пустом исходе: отменять
     * было нечего, и снэкбар об этом не сообщает.
     */
    private fun undo(intakeIds: List<String>) {
        undoJob?.cancel()
        undoJob = null
        undoActions.clear()
        view.update { it.copy(undo = null) }
        viewModelScope.launch {
            undoMark(UndoRequest(intakeIds = intakeIds, source = IntakeSource.PHONE))
        }
    }

    /**
     * Показ окна по дедлайну, а не по безусловным пяти секундам.
     *
     * Остаток считается от `expiresAt` магазина: замена пересчитывает срок от нового
     * дедлайна, а истёкшее окно (например, пока экран не был подписан) не показывается
     * вовсе. Часы — подменяемый источник времени (НФТ-48), а не системные.
     */
    private fun showUndo(mark: UndoableMark) {
        val remaining = Duration.between(time.now(), mark.expiresAt)
        if (remaining.isNegative || remaining.isZero) {
            view.update { it.copy(undo = null) }
            undoActions.clearIf(mark)
            return
        }
        view.update { it.copy(undo = mark.toNotice()) }
        undoJob = viewModelScope.launch {
            delay(remaining.toMillis())
            view.update { it.copy(undo = null) }
            // Защита от таймера прошлого действия: он не гасит чужую замену.
            undoActions.clearIf(mark)
        }
    }

    /** Окно удаления на «Сегодня» (ОВ-124): тот же срок и та же защита от чужого таймера. */
    private fun showDeletion(deletion: UndoableDeletion) {
        val remaining = Duration.between(time.now(), deletion.expiresAt)
        if (remaining.isNegative || remaining.isZero) {
            view.update { it.copy(deletion = null) }
            undoActions.clearIf(deletion)
            return
        }
        view.update { it.copy(deletion = deletion) }
        undoJob = viewModelScope.launch {
            delay(remaining.toMillis())
            view.update { it.copy(deletion = null) }
            undoActions.clearIf(deletion)
        }
    }

    /**
     * «Отменить» удаление: окно закрывается синхронно, до запуска возврата, — новая
     * команда в тот же миг открывает новое окно, а не ждёт завершения старой.
     */
    private fun undoDeletion(medicationId: String) {
        undoJob?.cancel()
        undoJob = null
        view.update { it.copy(deletion = null) }
        undoActions.clear()
        viewModelScope.launch { undoDelete(medicationId) }
    }

    /**
     * Имя лекарства по идентификатору — из блока «По потребности», второго чтения нет.
     *
     * Записи «по потребности» в лентах времени не стоят (у них нет назначенного времени),
     * поэтому имя берётся из того же блока, откуда пришло нажатие.
     */
    private fun prnMedicationName(medicationId: String): String? = (state.value as? TodayUiState.Content)
        ?.prn
        ?.firstOrNull { it.medicationId == medicationId }
        ?.medication
        ?.name

    /** Имя лекарства по идентификатору приёма — из очереди суток, второго чтения нет. */
    private fun medicationName(intakeId: String): String? = (state.value as? TodayUiState.Content)
        ?.layout
        ?.groups
        ?.flatMap { it.items }
        ?.firstOrNull { it.id == intakeId }
        ?.medication
        ?.name

    private fun UndoableMark.toNotice(): UndoNotice = UndoNotice(
        intakeIds = intakeIds,
        result = result,
        medicationNames = medicationNames,
    )

    private fun screen(agenda: TodayAgenda, current: ViewState): TodayUiState = if (agenda.isFirstRun) {
        TodayUiState.Empty(
            date = agenda.date,
            deletion = current.deletion,
            databaseReset = current.databaseResetExplanation,
        )
    } else {
        TodayUiState.Content(
            date = agenda.date,
            progress = agenda.progress,
            stockWarning = agenda.stockWarning,
            // Читается на каждой сборке состояния — не только при первом показе (НФТ-52).
            reliability = readReliability().warning,
            undo = current.undo,
            deletion = current.deletion,
            layout = agenda.layout,
            prn = agenda.prn,
            toggledSections = current.toggledSections,
            // Подтверждение, потерявшее предмет, не переносится в состояние: группа
            // пропала, перестала быть наступившей или ожидающих в ней меньше двух.
            // Это защищает от расхождения с чужой отметкой, оставившей одного (TDD §16.4).
            confirmingGroup = current.confirmingGroup?.takeIf { time ->
                agenda.layout.groups.any { it.time == time && it.isNow && GroupAction.of(it) != null }
            },
            prnCollapsed = current.prnCollapsed,
            nothingToday = agenda.hasNothingToday,
            databaseReset = current.databaseResetExplanation,
        )
    }

    /**
     * Чисто зрительная часть состояния: что человек развернул руками, какая группа
     * ждёт подтверждения и что показывает снэкбар отмены.
     *
     * Отдельным потоком рядом с повесткой дня, а не полем внутри неё: повестку
     * пересобирает домен при каждом изменении данных, и развёрнутая секция, подтверждение
     * и окно отмены обязаны это пережить.
     */
    private data class ViewState(
        val toggledSections: Set<PartOfDay> = emptySet(),
        val confirmingGroup: LocalTime? = null,
        val prnCollapsed: Boolean = false,
        val undo: UndoNotice? = null,
        val deletion: UndoableDeletion? = null,
        val databaseResetExplanation: Boolean = false,
    ) {
        fun withToggledSection(partOfDay: PartOfDay): ViewState = copy(
            toggledSections = if (partOfDay in toggledSections) {
                toggledSections - partOfDay
            } else {
                toggledSections + partOfDay
            },
        )
    }

    private companion object {

        /**
         * Сколько поток живёт без подписчиков.
         *
         * Пять секунд переживают поворот экрана и короткий уход в фон: иначе после
         * поворота экран пересобирается с нуля, а вместе с ним — проход по
         * просроченным приёмам.
         */
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

        /**
         * Сколько ждёт подтверждение групповой отметки (ОВ-32).
         *
         * Десять секунд, а не пять секунд окна отмены (SKL-30): там узнают случившееся,
         * здесь принимают решение, и при системном шрифте до 200 % вопрос обязан
         * успеть прочитаться.
         */
        const val CONFIRM_WINDOW_MS = 10_000L

        /**
         * Шаг перечитывания разрешений (SKL-48).
         *
         * Минута — тот же шаг, что у повестки (`ObserveTodayAgendaUseCase.TICK`):
         * точнее минуты на «Сегодня» ничего не меняется, и второй шаг не вводится.
         */
        const val RELIABILITY_TICK_MS = 60_000L
    }
}
