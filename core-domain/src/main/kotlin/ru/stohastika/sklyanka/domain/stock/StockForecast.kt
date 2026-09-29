package ru.stohastika.sklyanka.domain.stock

import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.math.BigDecimal

/**
 * На сколько дней хватит остатка упаковки.
 *
 * Три исхода, а не число с особыми значениями: «не ведётся» и «неизвестно» — разные
 * состояния, и экран показывает для них разное. Ноль дней — это полноценный ответ
 * («кончилось»), а не отсутствие ответа.
 */
sealed interface StockForecast {

    /** Запаса хватит ровно на столько полных суток. */
    data class Days(val value: Int) : StockForecast

    /**
     * Суточный расход не определён — приём по потребности (ОВ-39).
     *
     * Приложение не знает, сколько раз в сутки человек примет лекарство, и не вправе
     * обещать число дней. Оценка по лимиту `max_per_day` дала бы вечное предупреждение
     * тому, кто принимает лекарство раз в неделю; средний фактический расход требует
     * истории, которой в начале курса нет (`task.md`, «На сколько дней хватит»).
     */
    data object Unknown : StockForecast

    /** Учёт остатка не ведётся: `stock_left` пуст, и блока остатка у лекарства нет. */
    data object NotTracked : StockForecast

    companion object {

        /**
         * Прогноз запаса: целая часть от деления остатка на суточный расход.
         *
         * Целая часть — сознательно: обещать «хватит на полдня» нельзя, а округление
         * вверх сдвинуло бы предупреждение на день позже, чем нужно.
         */
        fun of(medication: Medication): StockForecast {
            val stock = medication.stockLeft ?: return NotTracked
            val daily = dailyUse(medication)
            return if (daily == null) Unknown else Days(stock.divideToIntegralValue(daily).min(MAX_DAYS).toInt())
        }

        /**
         * Суточный расход по расписанию: число приёмов в сутки на дозу одного приёма.
         *
         * `null` означает «неизвестен»: приём по потребности (ОВ-39) и вырожденное
         * расписание без единого времени приёма. Делить на такой расход нечем, и число
         * дней в этих случаях не существует, а не равно бесконечности.
         */
        fun dailyUse(medication: Medication): BigDecimal? {
            // Приём по потребности приравнивается к расписанию без времён: делить не на что.
            val perDay = medication.times.size.takeIf { medication.scheduleMode == ScheduleMode.SCHEDULED } ?: 0
            return (medication.doseAmount * BigDecimal(perDay)).takeIf { it > BigDecimal.ZERO }
        }

        /** Остаток бессрочного курса с крошечной дозой в Int не влезает; предел — не ошибка данных. */
        private val MAX_DAYS = BigDecimal(Int.MAX_VALUE)
    }
}
