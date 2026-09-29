package ru.stohastika.sklyanka.domain.medication

import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.TabletShape
import java.math.BigDecimal

/**
 * Подпись под названием лекарства в карточке: «Таблетка круглая с риской · 48 мг · до еды» (ЭКР-7).
 *
 * Не строка, а состав подписи. Собрать её строкой здесь нельзя вовсе: русского текста
 * в домене нет (НФТ-42), а склонения и разделитель живут в ресурсах (НФТ-44). Зато
 * правило «что входит в подпись, а что нет» предметное и проверяется на JVM — именно
 * оно и живёт здесь.
 */
data class MedicationSubtitle(
    val form: MedicationForm,
    /** Вид таблетки — только там, где он применим (ПР-9, ИНВ-16); иначе подпись короче. */
    val shape: TabletShape?,
    /** Дозировка препарата и её единица: показываются только вместе (ИНВ-5). */
    val strength: BigDecimal?,
    val strengthUnit: String?,
    /**
     * Связь с едой; `null` — говорить нечего.
     *
     * Решение исполнителя: [FoodRelation.ANY] («неважно») из подписи выпадает. ЭКР-7
     * перечисляет четыре сведения, но «неважно» — это отсутствие связи с едой, а не
     * сведение о ней, и «Таблетка · 48 мг · Неважно» сообщает человеку ровно ничего.
     * Требует подтверждения дизайна.
     */
    val foodRelation: FoodRelation?,
) {

    companion object {

        fun of(medication: Medication): MedicationSubtitle = MedicationSubtitle(
            form = medication.form,
            shape = medication.shape?.takeIf { medication.form.allowsShape },
            strength = medication.strength?.takeIf { medication.strengthUnit != null },
            strengthUnit = medication.strengthUnit?.takeIf { medication.strength != null },
            foodRelation = medication.foodRelation.takeIf { it != FoodRelation.ANY },
        )
    }
}
