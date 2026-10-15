package ru.stohastika.sklyanka.domain.pro

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Утверждение «год дешевле» — по текущим ценам магазина (Р-68 п. 5, Р-78 п. 1).
 */
class ProPriceRulesTest {

    private val monthly = ProProduct(
        id = ProProductId.MONTHLY,
        title = "Sklyanka Pro · monthly",
        price = StorePrice(text = "149 ₽", amountMicros = 149_000_000, currencyCode = "RUB"),
    )

    @Test
    fun `a year cheaper than twelve months is stated`() {
        val yearly = product(micros = 990_000_000)

        assertTrue(ProPriceRules.yearlyIsCheaper(monthly, yearly))
    }

    @Test
    fun `a year more expensive than twelve months is not stated`() {
        val yearly = product(micros = 1_990_000_000)

        assertFalse(ProPriceRules.yearlyIsCheaper(monthly, yearly))
    }

    @Test
    fun `a year equal to twelve months is not cheaper`() {
        val yearly = product(micros = 1_788_000_000)

        assertFalse(ProPriceRules.yearlyIsCheaper(monthly, yearly))
    }

    @Test
    fun `prices in different currencies are not compared`() {
        val yearly = ProProduct(
            id = ProProductId.YEARLY,
            title = "Sklyanka Pro · yearly",
            price = StorePrice(text = "€10.56", amountMicros = 10_560_000, currencyCode = "EUR"),
        )

        assertFalse(ProPriceRules.yearlyIsCheaper(monthly, yearly))
    }

    @Test
    fun `without a price of one period the claim cannot be made`() {
        assertFalse(ProPriceRules.yearlyIsCheaper(monthly, null))
        assertFalse(ProPriceRules.yearlyIsCheaper(null, product(micros = 990_000_000)))
    }

    private fun product(micros: Long) = ProProduct(
        id = ProProductId.YEARLY,
        title = "Sklyanka Pro · yearly",
        price = StorePrice(text = "990 ₽", amountMicros = micros, currencyCode = "RUB"),
    )
}
