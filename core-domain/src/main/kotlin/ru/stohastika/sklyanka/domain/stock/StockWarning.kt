package ru.stohastika.sklyanka.domain.stock

import ru.stohastika.sklyanka.domain.model.Medication
import java.math.BigDecimal

/**
 * Предупреждение о покупке: что именно заканчивается и насколько срочно (ПР-13).
 *
 * Название нужно плашке на «Сегодня» — она называет лекарство человеку. В журнал
 * название не уходит: там остаются только идентификаторы и числа (НФТ-19).
 */
data class StockWarning(
    val medicationId: String,
    val medicationName: String,
    val unitsLeft: BigDecimal,
    val daysLeft: Int,
    val thresholdDays: Int,
) {

    companion object {

        /**
         * Предупреждение лекарства или `null`, если предупреждать не о чем.
         *
         * Предупреждения нет, когда не заданы остаток или порог: пустой остаток означает
         * «человек его не ведёт», пустой порог — «напоминать о покупке не просили».
         * Нулевой остаток — не то же самое: ноль означает «кончилось» и предупреждает
         * всегда.
         *
         * Сравнение с порогом строгое (ПР-13: «хватает **менее чем** на N дней»):
         * запаса ровно на порог — ещё не повод предупреждать.
         */
        fun of(medication: Medication): StockWarning? {
            val threshold = thresholdOf(medication) ?: return null
            val stock = medication.stockLeft
            val days = (StockForecast.of(medication) as? StockForecast.Days)?.value
            return if (stock == null || days == null || days >= threshold) {
                null
            } else {
                StockWarning(
                    medicationId = medication.id,
                    medicationName = medication.name,
                    unitsLeft = stock,
                    daysLeft = days,
                    thresholdDays = threshold,
                )
            }
        }

        /**
         * Порог действующего курса.
         *
         * Пустой порог означает «напоминать о покупке не просили», архивированный курс
         * не предупреждает вовсе: покупать для него нечего (ПР-11).
         */
        private fun thresholdOf(medication: Medication): Int? =
            medication.stockThresholdDays?.takeUnless { medication.isArchived }

        /**
         * Самое срочное предупреждение из нескольких.
         *
         * Экран «Сегодня» показывает ровно одно (`docs/v2/10-behavior.md`): список
         * предупреждений превращает срочное в фон. Порядок — меньше дней, затем меньше
         * единиц, затем название: без последнего сравнения два одинаково срочных
         * лекарства менялись бы местами от запроса к запросу.
         */
        fun mostUrgent(medications: List<Medication>): StockWarning? = medications
            .mapNotNull { of(it) }
            .minWithOrNull(
                compareBy<StockWarning> { it.daysLeft }
                    .thenBy { it.unitsLeft }
                    .thenBy { it.medicationName },
            )
    }
}
