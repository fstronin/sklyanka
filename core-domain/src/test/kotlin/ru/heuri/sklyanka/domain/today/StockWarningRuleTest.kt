package ru.heuri.sklyanka.domain.today

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalTime

/**
 * Отбор предупреждения об остатке (ПР-13): показывается одно, самое срочное.
 *
 * Расход считается по расписанию: «Магний B6» с дозой 2 таблетки дважды в сутки тратит
 * четыре таблетки в день, и девяти таблеток ему хватает на два дня.
 */
class StockWarningRuleTest {

    @Test
    fun `without a triggered threshold there is no warning`() {
        val plenty = Today.medication("m-plenty", stockLeft = "100", thresholdDays = 5)

        assertNull(StockWarningRule(listOf(plenty)))
    }

    @Test
    fun `a medication without a threshold never warns`() {
        val noThreshold = Today.medication("m-no-threshold", stockLeft = "1", thresholdDays = null)

        assertNull(StockWarningRule(listOf(noThreshold)))
    }

    @Test
    fun `a medication without a stock count never warns`() {
        val noStock = Today.medication("m-no-stock", stockLeft = null, thresholdDays = 5)

        assertNull(StockWarningRule(listOf(noStock)))
    }

    @Test
    fun `a single triggered threshold gives the warning`() {
        val magnesium = Today.medication(
            id = "m-magnesium",
            times = listOf(LocalTime.of(14, 0)),
            dose = "2",
            stockLeft = "9",
            thresholdDays = 5,
        )

        val warning = StockWarningRule(listOf(magnesium))

        assertEquals("m-magnesium", warning?.medicationId)
        assertEquals(4, warning?.daysLeft)
    }

    @Test
    fun `of two triggered thresholds the most urgent one wins`() {
        val urgent = Today.medication("m-urgent", dose = "1", stockLeft = "2", thresholdDays = 7)
        val calmer = Today.medication("m-calmer", dose = "1", stockLeft = "6", thresholdDays = 7)

        val warning = StockWarningRule(listOf(calmer, urgent))

        assertEquals("m-urgent", warning?.medicationId)
        assertEquals(2, warning?.daysLeft)
    }

    @Test
    fun `an archived course does not warn about its stock`() {
        val archived = Today.medication("m-archived", stockLeft = "1", thresholdDays = 5, archived = true)

        assertNull(StockWarningRule(listOf(archived)))
    }
}
