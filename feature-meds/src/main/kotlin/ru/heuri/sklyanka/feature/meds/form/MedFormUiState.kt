package ru.heuri.sklyanka.feature.meds.form

import androidx.compose.runtime.Immutable
import ru.heuri.sklyanka.domain.medication.DraftField
import ru.heuri.sklyanka.domain.medication.DraftViolation
import ru.heuri.sklyanka.domain.medication.FieldApplicability
import ru.heuri.sklyanka.domain.medication.MedicationDraft
import ru.heuri.sklyanka.domain.medication.applicability

/**
 * Что делает форма: заводит курс или правит существующий.
 *
 * Правка отличается не только заголовком: в шапке стоит название лекарства, а нижняя
 * кнопка подписана «Сохранить» (`docs/spec/03-screens.md`). Профиль в правке не
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
 */
@Immutable
data class FormInputs(val doseAmount: String = "", val strength: String = "", val stockLeft: String = "")

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
    /** Нарушенные правила последней попытки сохранения; до неё список пуст. */
    val violations: List<DraftViolation> = emptyList(),
    /** Сохранение состоялось: маршрут закрывает форму и возвращает в список. */
    val saved: Boolean = false,
) {

    /**
     * Кнопка сохранения недоступна, пока не заполнено название (правило макета).
     *
     * Прочие правила проверяются при нажатии: форма длинная, и ругаться на поле,
     * до которого человек ещё не дошёл, — худший из двух способов.
     */
    val saveEnabled: Boolean get() = draft.name.isNotBlank()

    /** Блок, к которому прокручивается экран при отказе. */
    val firstViolationField: DraftField? get() = violations.firstOrNull()?.field

    /** Отказ по конкретному полю: поле показывает его под собой. */
    fun violationOf(field: DraftField): DraftViolation? = violations.firstOrNull { it.field == field }

    companion object {

        fun of(draft: MedicationDraft, mode: FormMode, inputs: FormInputs): MedFormUiState = MedFormUiState(
            mode = mode,
            draft = draft,
            inputs = inputs,
            applicability = draft.applicability(),
        )
    }
}
