package ru.stohastika.sklyanka.feature.meds.form

import androidx.compose.runtime.Immutable
import ru.stohastika.sklyanka.domain.medication.DraftField
import ru.stohastika.sklyanka.domain.medication.DraftViolation
import ru.stohastika.sklyanka.domain.medication.FieldApplicability
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.medication.applicability
import ru.stohastika.sklyanka.domain.medication.dosePerIntakeVisible

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
    /** Нарушенные правила последней попытки сохранения; до неё список пуст. */
    val violations: List<DraftViolation> = emptyList(),
    /**
     * Идёт запись: кнопка сохранения занята, и повторное нажатие не проходит (SKL-141).
     *
     * Живёт в состоянии, а не в памяти экрана: форма собирается заново вместе с поворотом,
     * и признак «идёт запись» обязан пережить пересборку так же, как черновик.
     */
    val saving: Boolean = false,
    /**
     * Черновик и текст полей на момент открытия формы — точка отсчёта «грязности» (RT-1, Р-87 п. 1).
     *
     * Задаётся умолчанием «как сейчас» и потому схватывается один раз в [of] при открытии,
     * а дальше живёт неизменно: `copy` в [MedicationFormViewModel.with] его не трогает. По
     * нему системная «Назад» отличает форму, в которую человек что-то ввёл, от чистой и от
     * правки без изменений — только первая спрашивает подтверждение сброса.
     */
    val baselineDraft: MedicationDraft = draft,
    val baselineInputs: FormInputs = inputs,
) {

    /**
     * Кнопка сохранения недоступна, пока не заполнено название (правило макета).
     *
     * Прочие правила проверяются при нажатии: форма длинная, и ругаться на поле,
     * до которого человек ещё не дошёл, — худший из двух способов.
     */
    val saveEnabled: Boolean get() = draft.name.isNotBlank()

    /**
     * Отличается ли черновик от загруженного при открытии (RT-1, Р-87 п. 1).
     *
     * Грязная форма теряет ввод при уходе, поэтому системная «Назад» спрашивает подтверждение;
     * чистая и правка без изменений закрываются молча. Сравниваются и доменное значение, и
     * набранный текст числовых полей: человек мог ввести «0,5» и стереть обратно — и то, и
     * другое обязано вернуть форму в чистое состояние.
     */
    val isDirty: Boolean get() = draft != baselineDraft || inputs != baselineInputs

    /** Вид, которым представлено правило: строка вида называет **правило**, а не выбор. */
    val shownScheduleKind: ScheduleKind get() = ScheduleKind.of(draft.schedule)

    /**
     * Показывается ли блок «Доза на один приём» (§5.3, §5.10).
     *
     * У ступенчатого вида дозу несёт этап, и поле уступает место этапам; у «по потребности»
     * вида не действует (ИНВ-25), и доза нужна всегда.
     */
    val dosePerIntakeVisible: Boolean get() = draft.dosePerIntakeVisible

    /** Блок, к которому прокручивается экран при отказе. */
    val firstViolationField: DraftField? get() = violations.firstOrNull()?.field

    /** Отказ по конкретному полю: поле показывает его под собой. */
    fun violationOf(field: DraftField): DraftViolation? = violations.firstOrNull { it.field == field }

    companion object {

        fun of(
            draft: MedicationDraft,
            mode: FormMode,
            inputs: FormInputs,
            scheduleKind: ScheduleKind = ScheduleKind.of(draft.schedule),
        ): MedFormUiState = MedFormUiState(
            mode = mode,
            draft = draft,
            inputs = inputs,
            applicability = draft.applicability(),
            scheduleKind = scheduleKind,
        )
    }
}
