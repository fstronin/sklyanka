package ru.stohastika.sklyanka.feature.meds.form

import androidx.compose.runtime.Immutable
import ru.stohastika.sklyanka.domain.medication.DraftField
import ru.stohastika.sklyanka.domain.medication.DraftViolation
import ru.stohastika.sklyanka.domain.medication.FieldApplicability
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.medication.ValidateMedicationDraft
import ru.stohastika.sklyanka.domain.medication.applicability
import ru.stohastika.sklyanka.domain.medication.dosePerIntakeVisible
import ru.stohastika.sklyanka.domain.medication.effectiveSchedule
import ru.stohastika.sklyanka.domain.pro.ComplexScheduleGate

/**
 * Что делает форма: заводит курс или правит существующий.
 *
 * Правка отличается не только заголовком: в шапке стоит название лекарства, а нижняя
 * кнопка подписана «Сохранить» (`docs/norm/04-behavior.md`). Профиль в правке не
 * предлагается вовсе — он неизменяем (ИНВ-3).
 */
sealed interface FormMode {

    data object Create : FormMode

    data class Edit(val title: String) : FormMode
}

/**
 * Сырой текст числовых полей.
 *
 * Черновик хранит числа, а человек набирает текст, и между ними есть «−5», «0,5»
 * и пустая строка. Хранить только число значит терять набранное при первом же отказе
 * в сохранении — а критерий приёмки прямо требует, чтобы форма осталась заполненной.
 *
 * Текст параметров вида хранится так же: интервал, длины цикла и числа этапов обязаны
 * пережить отказ и переключение вида, а «1» в поле интервала — ещё и не считаться
 * сложным видом (`ScheduleKind.INTERVAL`).
 */
@Immutable
data class FormInputs(
    val doseAmount: String = "",
    val strength: String = "",
    val stockLeft: String = "",
    val packageSize: String = "",
    /** «Интервал, дней» (ПР-17). */
    val interval: String = "",
    /** «Дней приёма подряд» цикла (ПР-18). */
    val cycleOn: String = "",
    /** «Дней перерыва» цикла (ПР-18). */
    val cycleOff: String = "",
    /** Дозы этапов по элементу на этап (ПР-19). */
    val stageDoses: List<String> = emptyList(),
    /** Длительности этапов по элементу на этап (ПР-19). */
    val stageDays: List<String> = emptyList(),
)

/**
 * Состояние формы лекарства.
 *
 * Черновик — единственный источник правды: локального состояния в разметке нет вовсе.
 * У формы одиннадцать блоков, связанных правилами применимости, и хранение части
 * состояния в компонентах означало бы, что «вид таблетки» пережил бы смену формы
 * выпуска незаметно для домена.
 */
@Immutable
data class MedFormUiState(
    val mode: FormMode,
    val draft: MedicationDraft,
    val inputs: FormInputs,
    val applicability: FieldApplicability,
    /**
     * Какой вид расписания человек выбрал строкой (§5.10).
     *
     * Хранится отдельно от [MedicationDraft.schedule], потому что «через каждый день»
     * (N = 1) вырождается в ежедневное (ПР-15): правило становится `Daily`, а поле
     * интервала обязано остаться на экране, чтобы человек мог набрать второе число.
     * Строка вида показывает вид **правила** ([shownScheduleKind]), а блок параметров —
     * этот.
     */
    val scheduleKind: ScheduleKind = ScheduleKind.of(draft.schedule),
    /**
     * Есть ли доступ «Про» прямо сейчас (Р-22, Р-28).
     *
     * Читается у кэша подписки при открытии формы и перечитывается, когда закрывается
     * витрина ([MedicationFormViewModel.refreshProAccess]): от него зависит только
     * завершение создания сложного расписания, и ничего больше.
     */
    val proIncluded: Boolean = false,
    /** Нарушенные правила последней попытки сохранения; до неё список пуст. */
    val violations: List<DraftViolation> = emptyList(),
    /**
     * Нарушения **текущего** нормализованного черновика: то, по чему решается, будет ли
     * нажатие «Сохранить» отказом формы (см. `save()` в [MedicationFormViewModel]).
     *
     * Отличается от [violations] ровно одним: [violations] — **записанные** нарушения
     * последней попытки, и до первой попытки список пуст; по нему форма показывает
     * подсказки под полями, и он же гасит их, как только правило выполнено. Здесь же
     * считается всегда и по тому же черновику, что и в сохранении, поэтому подсказка
     * над кнопкой называет то, что произойдёт при нажатии, а не то, что случилось
     * когда-то (§5.10): «имя и вид „Циклами“ без времени приёма» — это отказ формы,
     * а не повод покупать. Списки не смешиваются: править один вместо другого значит
     * вернуть либо подсказку не о том, либо молчание формы о нарушении.
     */
    val draftViolations: List<DraftViolation> = DRAFT_VALIDATION(draft),
    /**
     * Идёт запись: кнопка сохранения занята, и повторное нажатие не проходит (SKL-141).
     *
     * Живёт в состоянии, а не в памяти экрана: форма собирается заново вместе с поворотом,
     * и признак «идёт запись» обязан пережить пересборку так же, как черновик.
     */
    val saving: Boolean = false,
) {

    /**
     * Кнопка сохранения недоступна, пока не заполнено название (правило макета).
     *
     * Прочие правила проверяются при нажатии: форма длинная, и ругаться на поле,
     * до которого человек ещё не дошёл, — худший из двух способов.
     */
    val saveEnabled: Boolean get() = draft.name.isNotBlank()

    /** Вид, которым представлено правило: строка вида называет **правило**, а не выбор. */
    val shownScheduleKind: ScheduleKind get() = ScheduleKind.of(draft.schedule)

    /**
     * Показывается ли блок «Доза на один приём» (§5.3, §5.10).
     *
     * У ступенчатого вида дозу несёт этап, и поле уступает место этапам; у «по потребности»
     * вида не действует (ИНВ-25), и доза нужна всегда.
     */
    val dosePerIntakeVisible: Boolean get() = draft.dosePerIntakeVisible

    /**
     * Над кнопкой названа платная причина закрытого создания (ПР-20, ПР-21, §5.10).
     *
     * Причина и причина недоступной кнопки вместе не показываются: пока не заполнено
     * название, кнопка недоступна и называет своё, а сложный вид ждёт следующего шага —
     * две подсказки подряд читаются как ошибка (§5.10). Отказы параметров вида тоже
     * идут первыми: незаполненный вид — отказ формы, а не повод покупать. Решает это
     * [draftViolations] — нарушения **текущего** черновика, те же, по которым сохранение
     * называет свои отказы, а не записанные нарушения прошлой попытки.
     */
    val proReasonVisible: Boolean
        get() = saveEnabled &&
            draftViolations.isEmpty() &&
            ComplexScheduleGate.isCreationOfComplex(
                original = draft.originalSchedule,
                next = draft.effectiveSchedule,
                included = proIncluded,
            )

    /** Блок, к которому прокручивается экран при отказе. */
    val firstViolationField: DraftField? get() = violations.firstOrNull()?.field

    /** Отказ по конкретному полю: поле показывает его под собой. */
    fun violationOf(field: DraftField): DraftViolation? = violations.firstOrNull { it.field == field }

    companion object {

        /**
         * Проверка черновика без состояния: один экземпляр на все состояния формы.
         *
         * Заводить её на каждый пересчёт значило бы создавать объект ради самой же проверки,
         * а держать в [MedicationFormViewModel] — терять доступ из поля по умолчанию, где
         * нарушения и считаются по текущему черновику.
         */
        private val DRAFT_VALIDATION = ValidateMedicationDraft()

        fun of(
            draft: MedicationDraft,
            mode: FormMode,
            inputs: FormInputs,
            scheduleKind: ScheduleKind = ScheduleKind.of(draft.schedule),
            proIncluded: Boolean = false,
        ): MedFormUiState = MedFormUiState(
            mode = mode,
            draft = draft,
            inputs = inputs,
            applicability = draft.applicability(),
            scheduleKind = scheduleKind,
            proIncluded = proIncluded,
        )
    }
}
