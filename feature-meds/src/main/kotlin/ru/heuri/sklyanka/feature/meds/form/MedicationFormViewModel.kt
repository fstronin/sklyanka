package ru.heuri.sklyanka.feature.meds.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.heuri.sklyanka.domain.medication.MedicationDraft
import ru.heuri.sklyanka.domain.medication.SaveMedicationUseCase
import ru.heuri.sklyanka.domain.medication.SaveResult
import ru.heuri.sklyanka.domain.medication.ValidateMedicationDraft
import ru.heuri.sklyanka.domain.medication.applicability
import ru.heuri.sklyanka.domain.medication.normalized
import ru.heuri.sklyanka.domain.repository.ActiveProfileRepository
import ru.heuri.sklyanka.domain.repository.MedicationRepository
import ru.heuri.sklyanka.domain.time.TimeSource
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

    /**
     * Открыть форму.
     *
     * @param medicationId `null` — новое лекарство; иначе правка существующего той же формой.
     */
    fun open(medicationId: String? = null) {
        viewModelScope.launch {
            val existing = medicationId?.let { medications.get(it) }
            if (existing == null) {
                val draft = MedicationDraft.new(profileId = activeProfile.current(), today = time.today())
                _state.value = MedFormUiState.of(draft, FormMode.Create, FormInputs())
            } else {
                val draft = MedicationDraft.of(existing)
                _state.value = MedFormUiState.of(draft, FormMode.Edit(existing.name), inputsOf(draft))
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
        val draft = state.draft
        val inputs = state.inputs
        return when (intent) {
            is MedFormIntent.NameChanged -> state.with(draft.copy(name = intent.value))
            is MedFormIntent.ReleaseFormChanged -> state.with(draft.copy(form = intent.form))
            is MedFormIntent.ShapeChanged -> state.with(draft.copy(shape = intent.shape))
            is MedFormIntent.DoseAmountChanged ->
                state.with(draft.copy(doseAmount = decimalOf(intent.text)), inputs.copy(doseAmount = intent.text))

            is MedFormIntent.StrengthChanged -> state.with(
                // Единица дозировки в макете зафиксирована как «мг» и человеком не правится
                // (ОВ-16), поэтому она следует за значением: есть число — есть единица.
                draft.copy(
                    strength = decimalOf(intent.text),
                    strengthUnit = if (decimalOf(intent.text) == null) null else STRENGTH_UNIT,
                ),
                inputs.copy(strength = intent.text),
            )

            is MedFormIntent.ModeChanged -> state.with(draft.copy(scheduleMode = intent.mode))
            MedFormIntent.TimeAdded -> state.with(draft.copy(times = draft.times + nextTime(draft.times)))
            MedFormIntent.TimeRemovedFromEnd -> state.with(draft.copy(times = draft.times.dropLast(1)))
            is MedFormIntent.TimeRemoved ->
                state.with(draft.copy(times = draft.times.filterIndexed { index, _ -> index != intent.index }))

            is MedFormIntent.TimeChanged -> state.with(
                draft.copy(
                    times = draft.times.mapIndexed { index, time ->
                        if (index == intent.index) intent.time else time
                    },
                ),
            )

            MedFormIntent.MaxPerDayIncreased -> state.with(draft.copy(maxPerDay = (draft.maxPerDay ?: 0) + 1))
            MedFormIntent.MaxPerDayDecreased ->
                state.with(draft.copy(maxPerDay = maxOf(0, (draft.maxPerDay ?: 0) - 1)))

            is MedFormIntent.FoodRelationChanged -> state.with(draft.copy(foodRelation = intent.relation))
            is MedFormIntent.StockChanged ->
                state.with(draft.copy(stockLeft = decimalOf(intent.text)), inputs.copy(stockLeft = intent.text))

            is MedFormIntent.StockReminderToggled -> state.with(
                draft.copy(stockThresholdDays = if (intent.enabled) DEFAULT_THRESHOLD_DAYS else null),
            )

            is MedFormIntent.StartDateChanged -> state.with(draft.copy(startDate = intent.date))
            is MedFormIntent.EndDateChanged -> state.with(draft.copy(endDate = intent.date))
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
     * Новое состояние формы.
     *
     * Черновик нормализуется здесь, а не перед сохранением: иначе между сменой формы
     * выпуска и нажатием «Сохранить» экран показывает одно, а черновик держит другое.
     *
     * Перечень нарушений пересчитывается только после первой неудачной попытки
     * сохранить: до неё форма молчит, после — гаснет сама, как только правило выполнено.
     * Иначе пустая форма ругалась бы на поля, до которых человек ещё не дошёл.
     */
    private fun MedFormUiState.with(draft: MedicationDraft, inputs: FormInputs = this.inputs): MedFormUiState {
        val normalized = draft.normalized()
        return copy(
            draft = normalized,
            inputs = inputs,
            applicability = normalized.applicability(),
            violations = if (violations.isEmpty()) emptyList() else validate(normalized),
        )
    }

    private fun save(state: MedFormUiState) {
        viewModelScope.launch {
            when (val result = saveMedication(state.draft)) {
                is SaveResult.Saved -> _state.value = _state.value?.copy(saved = true)
                // Черновик не трогается: сохраняется всё, что человек ввёл, включая
                // текст числовых полей, который в число не разобрался.
                is SaveResult.Rejected -> _state.value = _state.value?.copy(violations = result.violations)
            }
        }
    }

    /** Текст числовых полей при открытии правки: то, что уже записано. */
    private fun inputsOf(draft: MedicationDraft) = FormInputs(
        doseAmount = draft.doseAmount.plain(),
        strength = draft.strength.plain(),
        stockLeft = draft.stockLeft.plain(),
    )

    private fun BigDecimal?.plain(): String = this?.stripTrailingZeros()?.toPlainString().orEmpty()

    /**
     * Разбор набранного человеком числа.
     *
     * Запятая приравнивается к точке: на русской раскладке десятичный разделитель —
     * запятая, и «0,5» обязано означать половину, а не отказ «введите число».
     * Неразобранный текст даёт пустое значение, и о нём говорит правило поля,
     * а не молчаливое обнуление.
     */
    private fun decimalOf(text: String): BigDecimal? =
        text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()

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

        /** Подпись порога в макете: «Когда останется на 5 дней». */
        const val DEFAULT_THRESHOLD_DAYS = 5

        /** Единица дозировки из макета формы; свободный ввод — открытый вопрос ОВ-16. */
        const val STRENGTH_UNIT = "mg"
    }
}
