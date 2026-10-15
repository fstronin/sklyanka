package ru.stohastika.sklyanka.domain.pro

import java.math.BigDecimal

/**
 * Правило выгоды года (Р-68 п. 5).
 *
 * «Год дешевле» — утверждение о ценах магазина, а не украшение карточки: оно показывается,
 * только пока верно при текущих ценах, и не показывается вовсе, когда цены неизвестны.
 * Поэтому правило живёт в домене рядом с ценой, а не в разметке.
 *
 * Ни процентов, ни пересчёта «сколько это в месяц» здесь нет: наша арифметика из чужой цены
 * разошлась бы при промо и потребовала бы округления в шести локалях (Р-78 п. 3).
 */
object ProPriceRules {

    /** Двенадцать месяцев в году: сравнение годовой цены с двенадцатью месячными. */
    private val MONTHS_IN_YEAR = BigDecimal.valueOf(12)

    /**
     * Верно ли утверждение «год дешевле».
     *
     * Сравнение идёт по величине цены в валюте магазина. Цены в разных валютах не сравниваются:
     * их в одной витрине быть не может, а курса у продукта нет — утверждение о выгоде из двух
     * валют было бы выдумкой.
     */
    fun yearlyIsCheaper(monthly: ProProduct?, yearly: ProProduct?): Boolean {
        val month = monthly?.price
        val year = yearly?.price
        return month != null &&
            year != null &&
            month.currencyCode == year.currencyCode &&
            BigDecimal.valueOf(year.amountMicros) < BigDecimal.valueOf(month.amountMicros).multiply(MONTHS_IN_YEAR)
    }
}
