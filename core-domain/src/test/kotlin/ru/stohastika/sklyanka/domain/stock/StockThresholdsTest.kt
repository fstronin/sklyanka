package ru.stohastika.sklyanka.domain.stock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Набор порогов напоминания о покупке и умолчание (ПМ-13, SKL-74).
 *
 * Состав и порядок — контракт: форма раскладывает [StockThresholds.Options] по чипам,
 * и «3, 5, 7, 10, 14» из `acceptance/src/main/resources/features/SKL-74.feature` обязан совпадать с кодом.
 */
class StockThresholdsTest {

    @Test
    fun `the options are the working set in order`() {
        assertEquals(listOf(3, 5, 7, 10, 14), StockThresholds.Options)
    }

    @Test
    fun `the default is seven days`() {
        assertEquals(7, StockThresholds.DEFAULT_DAYS)
    }
}
