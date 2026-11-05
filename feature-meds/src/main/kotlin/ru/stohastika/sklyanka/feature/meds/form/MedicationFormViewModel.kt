package ru.stohastika.sklyanka.feature.meds.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.medication.SaveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.SaveResult
import ru.stohastika.sklyanka.domain.medication.StrengthUnit
import ru.stohastika.sklyanka.domain.medication.ValidateMedicationDraft
import ru.stohastika.sklyanka.domain.medication.applicability
import ru.stohastika.sklyanka.domain.medication.normalized
import ru.stohastika.sklyanka.domain.model.ScheduleRule
import ru.stohastika.sklyanka.domain.model.Stage
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.stock.StockThresholds
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.feature.meds.amountOf
import java.math.BigDecimal
import java.time.LocalTime
import javax.inject.Inject

/**
 * Состояние формы заведения и правки лекарства.
 *
 * Каждое изменение поля порождает новый черновик и пересчитывает применимость блоков;
 * неприменимые значения при этом сбрасываются, а не прячутся (`TDD.md §9.2`).
 *
 * Отказ в сохранении черновик не трогает: он только добавляет перечень нарушенных
 * правил. Введённое не теряется — ни числа, ни набранный человеком текст этих чисел.
 */
@HiltViewModel
class MedicationFormViewModel @Inject constructor(
    private val activeProfile: ActiveProfileRepository,
    private val medications: MedicationRepository,
    private val saveMedication: SaveMedicationUseCase,
    private val time: TimeSource,
) : ViewModel() {

    private val validate = ValidateMedicationDraft()

    private val _state = MutableStateFlow<MedFormUiState?>(null)

    /** `null` — форма ещё не открыта: профиль и правящийся курс читаются из хранилища. */
    val state: StateFlow<MedFormUiState?> = _state.asStateFlow()

    private val _closed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Успешное сохранение закрывает форму один раз.
     *
     * Событие, а не состояние: открытие формы после сохранения — новая подписка, и
     * прошлое сохранение ей не принадлежит. Состояние с флагом «сохранено» читалось
     * при повторном открытии как только что наступившее, и форма закрывалась сама
     * на первом кадре (SKL-57).
     */
    val closed: SharedFlow<Unit> = _closed.asSharedFlow()

    /**
     * Открыть форму.
     *
     * Состояние гасится синхронно, до чтения хранилища: иначе первый кадр формы
     * показывал предыдущий черновик, пока асинхронное чтение не заменит его.
     *
     * @param medicationId `null` — новое лекарство; иначе правка существующего той же формой.
     */
    fun open(medicationId: String? = null) {
        _state.value = null
        viewModelScope.launch {
            val existing = medicationId?.let { medications.get(it) }
            if (existing == null) {
                val draft = MedicationDraft.new(profileId = activeProfile.current(), today = time.today())
                _state.value = MedFormUiState.of(draft, FormMode.Create, FormInputs())
            } else {
                val draft = MedicationDraft.of(existing)
                _state.value = MedFormUiState.of(
                    draft = draft,
                    mode = FormMode.Edit(existing.name),
                    inputs = inputsOf(draft),
                )
            }
        }
    }

    fun onIntent(intent: MedFormIntent) {
        val current = _state.value ?: return
        if (intent is MedFormIntent.Save) {
            save(current)
            return
        }
        _state.value = reduce(current, intent)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun reduce(state: MedFormUiState, intent: MedFormIntent): MedFormUiState {
        // Намерения вида расписания живут отдельно: их девять, и вместе с остальными они
        // вывели бы `when` за порог читаемости (у формы одиннадцать блоков).
        reduceSchedule(state, intent)?.let { return it }
        val draft = state.draft
        val inputs = state.inputs
        return when (intent) {
            is MedFormIntent.NameChanged -> state.with(draft.copy(name = intent.value))
            is MedFormIntent.ReleaseFormChanged -> state.with(draft.copy(form = intent.form))
            is MedFormIntent.ShapeChanged -> state.with(draft.copy(shape = intent.shape))
            is MedFormIntent.DoseAmountChanged ->
                state.with(draft.copy(doseAmount = amountOf(intent.text)), inputs.copy(doseAmount = intent.text))

            is MedFormIntent.StrengthChanged ->
                state.with(strengthWith(draft, intent.text), inputs.copy(strength = intent.text))

            is MedFormIntent.StrengthUnitChanged -> state.with(draft.copy(strengthUnit = intent.unit))

            is MedFormIntent.ModeChanged -> state.with(draft.copy(scheduleMode = intent.mode))

            // Намерения вида расписания обработаны выше (`reduceSchedule`) и сюда не доходят.
            is MedFormIntent.ScheduleKindChanged, is MedFormIntent.WeekdayToggled,
            is MedFormIntent.IntervalChanged, is MedFormIntent.CycleOnChanged,
            is MedFormIntent.CycleOffChanged, is MedFormIntent.StageDoseChanged,
            is MedFormIntent.StageDaysChanged, MedFormIntent.StageAdded,
            is MedFormIntent.StageRemoved,
            -> state

            MedFormIntent.TimeAdded -> state.with(draft.copy(times = draft.times + nextTime(draft.times)))
            MedFormIntent.TimeRemovedFromEnd -> state.with(draft.copy(times = draft.times.dropLast(1)))
            is MedFormIntent.TimeRemoved ->
                state.with(draft.copy(times = draft.times.filterIndexed { index, _ -> index != intent.index }))

            is MedFormIntent.TimeChanged -> state.with(withTime(draft, intent.index, intent.time))

            MedFormIntent.MaxPerDayIncreased -> state.with(draft.copy(maxPerDay = (draft.maxPerDay ?: 0) + 1))
            MedFormIntent.MaxPerDayDecreased ->
                state.with(draft.copy(maxPerDay = maxOf(0, (draft.maxPerDay ?: 0) - 1)))

            is MedFormIntent.FoodRelationChanged -> state.with(draft.copy(foodRelation = intent.relation))
            is MedFormIntent.StockChanged ->
                state.with(draft.copy(stockLeft = amountOf(intent.text)), inputs.copy(stockLeft = intent.text))

            is MedFormIntent.PackageSizeChanged ->
                state.with(
                    draft.copy(packageSize = amountOf(intent.text)),
                    inputs.copy(packageSize = intent.text),
                )

            is MedFormIntent.StockReminderToggled -> state.with(
                // Включение ставит доменное умолчание (ПМ-13: 7 дней), выключение убирает
                // порог: пустое значение и есть выключенное напоминание.
                draft.copy(stockThresholdDays = if (intent.enabled) StockThresholds.DEFAULT_DAYS else null),
            )

            is MedFormIntent.StockThresholdChanged -> state.with(draft.copy(stockThresholdDays = intent.days))

            is MedFormIntent.StartDateChanged -> state.with(draft.copy(startDate = intent.date))
            is MedFormIntent.EndDateChanged -> state.with(draft.copy(endDate = intent.date))

            is MedFormIntent.CourseEndPresetChosen ->
                state.with(draft.copy(endDate = intent.preset.endDateFrom(draft.startDate)))

            is MedFormIntent.IndefiniteToggled -> state.with(
                // Пустая дата окончания и есть бессрочный приём (ПР-6). Выключение
                // переключателя не оставляет строку «Окончание» пустой, иначе она
                // требует даты, которой неоткуда взяться: предлагается дата начала,
                // то есть курс на один день, и человек правит её выбором даты.
                draft.copy(endDate = if (intent.indefinite) null else draft.startDate),
            )

            MedFormIntent.Save -> state
        }
    }

    /**
     * Намерения блока вида расписания (§5.10, ПР-15…ПР-19).
     *
     * @return новое состояние либо `null`, если намерение не о виде расписания.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun reduceSchedule(state: MedFormUiState, intent: MedFormIntent): MedFormUiState? {
        val draft = state.draft
        val inputs = state.inputs
        return when (intent) {
            is MedFormIntent.ScheduleKindChanged -> {
                val prepared = inputsForKind(intent.kind, inputs)
                state.with(
                    draft = draft.copy(schedule = ruleFor(intent.kind, draft.schedule, prepared)),
                    inputs = prepared,
                    scheduleKind = intent.kind,
                )
            }

            is MedFormIntent.WeekdayToggled -> {
                val days = (draft.schedule as? ScheduleRule.Weekdays)?.days.orEmpty()
                val next = if (intent.day in days) days - intent.day else days + intent.day
                state.with(
                    draft = draft.copy(schedule = ScheduleRule.Weekdays(next)),
                    scheduleKind = ScheduleKind.WEEKDAYS,
                )
            }

            is MedFormIntent.IntervalChanged -> state.with(
                draft = draft.copy(schedule = intervalRule(intent.text)),
                inputs = inputs.copy(interval = intent.text),
                scheduleKind = ScheduleKind.INTERVAL,
            )

            is MedFormIntent.CycleOnChanged -> state.with(
                draft = draft.copy(schedule = cyclesRule(intent.text, inputs.cycleOff)),
                inputs = inputs.copy(cycleOn = intent.text),
                scheduleKind = ScheduleKind.CYCLES,
            )

            is MedFormIntent.CycleOffChanged -> state.with(
                draft = draft.copy(schedule = cyclesRule(inputs.cycleOn, intent.text)),
                inputs = inputs.copy(cycleOff = intent.text),
                scheduleKind = ScheduleKind.CYCLES,
            )

            is MedFormIntent.StageDoseChanged -> {
                val next = inputs.stageDoses.replaceAt(intent.index, intent.text)
                state.with(
                    draft = draft.copy(schedule = stagesRule(next, inputs.stageDays)),
                    inputs = inputs.copy(stageDoses = next),
                    scheduleKind = ScheduleKind.STAGES,
                )
            }

            is MedFormIntent.StageDaysChanged -> {
                val next = inputs.stageDays.replaceAt(intent.index, intent.text)
                state.with(
                    draft = draft.copy(schedule = stagesRule(inputs.stageDoses, next)),
                    inputs = inputs.copy(stageDays = next),
                    scheduleKind = ScheduleKind.STAGES,
                )
            }

            MedFormIntent.StageAdded -> {
                val doses = inputs.stageDoses + ""
                val days = inputs.stageDays + ""
                state.with(
                    draft = draft.copy(schedule = stagesRule(doses, days)),
                    inputs = inputs.copy(stageDoses = doses, stageDays = days),
                    scheduleKind = ScheduleKind.STAGES,
                )
            }

            is MedFormIntent.StageRemoved -> {
                // Последний этап не удаляется (§5.10): курс без этапов не собрать (ИНВ-23).
                if (inputs.stageDoses.size <= 1) {
                    state
                } else {
                    val doses = inputs.stageDoses.filterIndexed { index, _ -> index != intent.index }
                    val days = inputs.stageDays.filterIndexed { index, _ -> index != intent.index }
                    state.with(
                        draft = draft.copy(schedule = stagesRule(doses, days)),
                        inputs = inputs.copy(stageDoses = doses, stageDays = days),
                        scheduleKind = ScheduleKind.STAGES,
                    )
                }
            }

            else -> null
        }
    }

    /**
     * Подготовка полей ввода под выбранный вид.
     *
     * Ступенчатый вид приходит с одним пустым этапом: норма показывает карточку «Этап 1»
     * сразу после выбора (§5.10), и курс без этапов не собрать (ИНВ-23). Остальные виды
     * оставляют набранное: параметры вида сохраняются и возвращаются, как и сам вид.
     */
    private fun inputsForKind(kind: ScheduleKind, inputs: FormInputs): FormInputs =
        if (kind == ScheduleKind.STAGES && inputs.stageDoses.isEmpty()) {
            inputs.copy(stageDoses = listOf(""), stageDays = listOf(""))
        } else {
            inputs
        }

    /** Правило вида из выбранного вида и набранных чисел. */
    private fun ruleFor(kind: ScheduleKind, current: ScheduleRule, inputs: FormInputs): ScheduleRule = when (kind) {
        ScheduleKind.DAILY -> ScheduleRule.Daily
        // Дни недели живут в самом правиле: выбор множественный, и хранить его дважды незачем.
        ScheduleKind.WEEKDAYS -> ScheduleRule.Weekdays((current as? ScheduleRule.Weekdays)?.days.orEmpty())
        ScheduleKind.INTERVAL -> intervalRule(inputs.interval)
        ScheduleKind.CYCLES -> cyclesRule(inputs.cycleOn, inputs.cycleOff)
        ScheduleKind.STAGES -> stagesRule(inputs.stageDoses, inputs.stageDays)
    }

    /**
     * «Через N дней» из текста поля (ПР-17, ПР-15).
     *
     * Пустое или непонятое поле даёт `EveryNDays(0)` — отказ «интервал не меньше двух дней».
     * «Через каждый день» (N = 1) вырождается в ежедневное расписание: сложного вида
     * не возникает. Поле интервала при этом остаётся на экране
     * (его держит [MedFormUiState.scheduleKind]), чтобы человек мог набрать второе число.
     */
    private fun intervalRule(text: String): ScheduleRule {
        val days = text.trim().toIntOrNull()
        return when {
            days == null -> ScheduleRule.EveryNDays(NO_INTERVAL)
            days <= 1 -> ScheduleRule.Daily
            else -> ScheduleRule.EveryNDays(days)
        }
    }

    /** «Циклами»: длины из текста, пустое поле — ноль, то есть отказ «нулевая длина» (ПР-18). */
    private fun cyclesRule(on: String, off: String): ScheduleRule =
        ScheduleRule.Cycles(onDays = on.trim().toIntOrNull() ?: 0, offDays = off.trim().toIntOrNull() ?: 0)

    /**
     * «По этапам»: список этапов из текстов (ПР-19).
     *
     * Пустое поле дозы или длительности даёт ноль — этап без дозы или длительности,
     * и отказ называет его словами (`error_stage_dose`, `error_stage_days`, §10).
     */
    private fun stagesRule(doses: List<String>, days: List<String>): ScheduleRule = ScheduleRule.Staged(
        doses.indices.map { index ->
            Stage(
                doseAmount = doses[index].trim().toBigDecimalOrNull() ?: BigDecimal.ZERO,
                days = days.getOrNull(index)?.trim()?.toIntOrNull() ?: 0,
            )
        },
    )

    /** Текст по индексу; индекса нет — строка остаётся прежней. */
    private fun List<String>.replaceAt(index: Int, text: String): List<String> =
        mapIndexed { position, value -> if (position == index) text else value }

    /**
     * Новое состояние формы.
     *
     * Черновик нормализуется здесь, а не перед сохранением: иначе между сменой формы
     * выпуска и нажатием «Сохранить» экран показывает одно, а черновик держит другое.
     *
     * Перечень нарушений пересчитывается только после первой неудачной попытки
     * сохранить: до неё форма молчит, после — гаснет сама, как только правило выполнено.
     * Иначе пустая форма ругалась бы на поля, до которых человек ещё не дошёл.
     */
    private fun MedFormUiState.with(
        draft: MedicationDraft,
        inputs: FormInputs = this.inputs,
        scheduleKind: ScheduleKind = this.scheduleKind,
    ): MedFormUiState {
        val normalized = draft.normalized()
        val broken = validate(normalized)
        return copy(
            draft = normalized,
            inputs = inputs,
            scheduleKind = scheduleKind,
            applicability = normalized.applicability(),
            violations = if (violations.isEmpty()) emptyList() else broken,
        )
    }

    private fun save(state: MedFormUiState) {
        // Повтор нажатия во время записи не даёт ни второй записи, ни второго плана приёмов:
        // у нового черновика нет идентификатора, и каждый вызов сценария сохранения выдаёт
        // новый (SKL-141). Признак ставится синхронно, до запуска корутины: второе нажатие
        // приходит раньше, чем запись успевает начаться, и обязано увидеть «идёт запись».
        if (state.saving) return
        _state.value = state.copy(saving = true)
        viewModelScope.launch {
            when (val result = saveMedication(state.draft)) {
                is SaveResult.Saved -> {
                    _state.value = _state.value?.copy(saving = false)
                    _closed.tryEmit(Unit)
                }
                // Черновик не трогается: сохраняется всё, что человек ввёл, включая
                // текст числовых полей, который в число не разобрался. Отказ снимает
                // признак записи: форма снова готова принять нажатие.
                is SaveResult.Rejected -> _state.value = _state.value?.copy(
                    violations = result.violations,
                    saving = false,
                )
            }
        }
    }

    /**
     * Текст числовых полей при открытии правки: то, что уже записано.
     *
     * Параметры вида берутся только у своего вида: у курса он один (ИНВ-25), и чужие
     * поля остаются пустыми, а не показывают числа другого расписания.
     */
    private fun inputsOf(draft: MedicationDraft): FormInputs {
        val interval = (draft.schedule as? ScheduleRule.EveryNDays)?.takeIf { it.days > 1 }
        val cycle = draft.schedule as? ScheduleRule.Cycles
        val stages = (draft.schedule as? ScheduleRule.Staged)?.stages.orEmpty()
        return FormInputs(
            doseAmount = draft.doseAmount.plain(),
            strength = draft.strength.plain(),
            stockLeft = draft.stockLeft.plain(),
            packageSize = draft.packageSize.plain(),
            interval = interval?.days?.toString().orEmpty(),
            cycleOn = cycle?.onDays?.toString().orEmpty(),
            cycleOff = cycle?.offDays?.toString().orEmpty(),
            stageDoses = stages.map { it.doseAmount.stripTrailingZeros().toPlainString() },
            stageDays = stages.map { if (it.days == 0) "" else it.days.toString() },
        )
    }

    private fun BigDecimal?.plain(): String = this?.stripTrailingZeros()?.toPlainString().orEmpty()

    /**
     * Время новой строки расписания.
     *
     * Первая строка — восемь утра, дальше шаг в четыре часа: подряд идущие приёмы
     * человек всё равно правит выбором времени, а две одинаковые строки подряд
     * заставляли бы его делать это дважды. Совпадение всё же возможно на шестой
     * строке, и тогда о повторе скажет правило ИНВ-6, а не молчаливая подмена.
     */
    private fun nextTime(times: List<LocalTime>): LocalTime =
        times.lastOrNull()?.plusHours(TIME_STEP_HOURS) ?: DEFAULT_TIME

    private companion object {
        val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)
        const val TIME_STEP_HOURS = 4L

        /** Пустое или непонятое поле интервала: отказ «интервал не меньше двух дней» (ПР-17). */
        const val NO_INTERVAL = 0
    }
}

/**
 * Дозировка и её единица — вместе или никак (ИНВ-5).
 *
 * Единица выбирается из закрытого списка (Р-45) и переживает правку числа: сброс к «мг»
 * на каждой цифре стирал бы выбор человека. Нет числа — нет и единицы.
 */
private fun strengthWith(draft: MedicationDraft, text: String): MedicationDraft {
    val amount = amountOf(text)
    return draft.copy(
        strength = amount,
        strengthUnit = if (amount == null) null else draft.strengthUnit ?: StrengthUnit.MILLIGRAM,
    )
}

/** Список времён с заменой одной строки: время правится выбором, а не перебором. */
private fun withTime(draft: MedicationDraft, index: Int, time: LocalTime): MedicationDraft = draft.copy(
    times = draft.times.mapIndexed { position, current -> if (position == index) time else current },
)
