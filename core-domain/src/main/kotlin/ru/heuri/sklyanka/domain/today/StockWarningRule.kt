package ru.heuri.sklyanka.domain.today

import ru.heuri.sklyanka.domain.medication.MedicationListRules
import ru.heuri.sklyanka.domain.model.Medication

/**
 * Выбор предупреждения об остатке для плашки экрана (ПР-13).
 *
 * Показывается **одно**, самое срочное: макет рисует одну плашку, и правило
 * «показывать одно» зафиксировано спецификацией (`docs/spec/03-screens.md`). Вторая
 * плашка не помещается в шапку и превращает предупреждение в ленту, которую перестают
 * читать — а перестав читать предупреждение об остатке, человек остаётся без лекарства.
 *
 * Отбор и расчёт «на сколько дней хватит» берутся из правил списка лекарств: тем же
 * порогом пользуется карточка списка, и своё, второе определение «порог сработал»
 * разошлось бы с ним молча.
 */
object StockWarningRule {

    operator fun invoke(medications: List<Medication>): StockWarning? = medications
        .asSequence()
        .filterNot { it.isArchived }
        .mapNotNull { medication -> warningFor(medication) }
        // Самое срочное — с наименьшим запасом дней; при равенстве побеждает меньший
        // остаток, а затем имя: порядок обязан быть устойчивым, иначе плашка меняется
        // местами между перерисовками при одних и тех же данных.
        .sortedWith(compareBy({ it.daysLeft }, { it.stockLeft }, { it.medicationName }))
        .firstOrNull()

    private fun warningFor(medication: Medication): StockWarning? {
        if (!MedicationListRules.stockThresholdReached(medication)) return null
        val left = medication.stockLeft ?: return null
        val days = MedicationListRules.daysOfStockLeft(medication) ?: return null
        return StockWarning(
            medicationId = medication.id,
            medicationName = medication.name,
            stockLeft = left,
            doseUnitCode = medication.doseUnit,
            daysLeft = days,
        )
    }
}
