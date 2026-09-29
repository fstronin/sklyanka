package ru.stohastika.sklyanka.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.intake.CreatePrnIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.MarkIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.MarkRequest
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.intake.UndoMarkUseCase
import ru.stohastika.sklyanka.domain.intake.UndoRequest
import ru.stohastika.sklyanka.domain.intake.UndoableMark
import ru.stohastika.sklyanka.domain.intake.UndoableMarkStore
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.reminder.ReadReminderReliabilityUseCase
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.today.GroupAction
import ru.stohastika.sklyanka.domain.today.GroupMarkOutcome
import ru.stohastika.sklyanka.domain.today.MarkIntakeGroupUseCase
import ru.stohastika.sklyanka.domain.today.ObserveTodayAgendaUseCase
import ru.stohastika.sklyanka.domain.today.PartOfDay
import ru.stohastika.sklyanka.domain.today.TodayAgenda
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
    private val undoMarks: UndoableMarkStore,
    private val time: TimeSource,
    /**
     * Надёжность напоминаний читается при каждом построении состояния, а не кешируется:
     * отозванное разрешение отражается без перезапуска (НФТ-52). Сценарий читает систему
     * синхронно, поэтому вызов стоит в [screen] — втором места, кроме диагностики,
     * где продукт спрашивает систему о разрешениях, не появляется.
     */
    private val readReliability: ReadReminderReliabilityUseCase,
) : ViewModel() {

    private val view = MutableStateFlow(ViewState())

    /** Таймер текущего окна: замена отметки обязана погасить прошлый, а не ждать его. */
    private var undoJob: Job? = null

    /** Таймер подтверждения: повторный арм обязан погасить прошлый срок, а не ждать его. */
    private var confirmJob: Job? = null

    val state: StateFlow<TodayUiState> = combine(observeAgenda(), view) { agenda, current ->
        screen(agenda, current)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), TodayUiState.Opening)

    init {
        viewModelScope.launch {
            undoMarks.current.collect { mark ->
                undoJob?.cancel()
                undoJob = null
                if (mark == null) {
                    view.update { it.copy(undo = null) }
                } else {
                    showUndo(mark)
                }
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

            is TodayIntent.UndoMark -> {
                clearConfirmation()
                undo(intent.intakeIds)
            }

            is TodayIntent.TakePrn -> {
                clearConfirmation()
                viewModelScope.launch {
                    createPrn(intent.medicationId, IntakeSource.PHONE)
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
     * Конец окна отмены и подтверждения при уходе с «Сегодня».
     *
     * Уход — событие жизненного цикла маршрута, а не действие человека: его зовёт
     * `TodayRoute`, а не намерение (`TDD.md §5.4`).
     */
    fun onScreenLeft() {
        clearConfirmation()
        undoMarks.clear()
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
                undoMarks.publish(
                    intakeIds = listOf(intakeId),
                    // Текст говорит о записанном исходе, а не о нажатой кнопке: при
                    // замещении они расходятся (TDD.md §15.2).
                    result = outcome.intake?.status?.let(MarkResult::of) ?: result,
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
                undoMarks.publish(
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
        undoMarks.clear()
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
            undoMarks.clearIf(mark)
            return
        }
        view.update { it.copy(undo = mark.toNotice()) }
        undoJob = viewModelScope.launch {
            delay(remaining.toMillis())
            view.update { it.copy(undo = null) }
            // Защита от таймера прошлого действия: он не гасит чужую замену.
            undoMarks.clearIf(mark)
        }
    }

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
        TodayUiState.Empty(agenda.date)
    } else {
        TodayUiState.Content(
            date = agenda.date,
            progress = agenda.progress,
            stockWarning = agenda.stockWarning,
            // Читается на каждой сборке состояния — не только при первом показе (НФТ-52).
            reliability = readReliability().warning,
            undo = current.undo,
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
    }
}
