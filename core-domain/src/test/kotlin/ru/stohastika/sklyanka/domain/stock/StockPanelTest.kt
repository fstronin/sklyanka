package ru.stohastika.sklyanka.domain.stock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.model.Medication
import java.math.BigDecimal
import java.time.LocalTime

/**
 * Блок «Остаток» в карточке лекарства: что именно экран получает от домена.
 *
 * Сценарии группы `@ui` из `docs/tasks/SKL-12/acceptance.feature` в части чисел
 * и признаков; тексты и склонения — ресурсы SKL-08 и SKL-10.
 */
class StockPanelTest {

    @Test
    fun `a medication without stock tracking has no block at all`() {
        val medication = Fixtures.scheduledMedication().copy(stockLeft = null)

        assertEquals(StockPanel.Hidden, StockPanel.of(medication))
    }

    @Test
    fun `a fired threshold shows the value and the caption`() {
        val panel = StockPanel.of(twiceADay(stock = "9", threshold = FIVE_DAYS))

        assertEquals(StockPanel.Visible(BigDecimal("9"), FOUR_DAYS, warning = true), panel)
        assertEquals(FOUR_DAYS, (panel as StockPanel.Visible).captionDays)
    }

    @Test
    fun `a threshold that has not fired shows the value without the caption`() {
        val panel = StockPanel.of(twiceADay(stock = "30", threshold = FIVE_DAYS)) as StockPanel.Visible

        assertEquals(BigDecimal("30"), panel.unitsLeft)
        assertTrue(!panel.warning, "fifteen days against a threshold of five is not a warning")
        assertNull(panel.captionDays, "the caption belongs to the warning, not to the block")
    }

    @Test
    fun `an on-demand medication shows the stock without promising days`() {
        // ОВ-39: остаток виден и списывается, число дней не обещается.
        val medication = Fixtures.prnMedication().copy(
            stockLeft = BigDecimal("2"),
            stockThresholdDays = FIVE_DAYS,
        )

        val panel = StockPanel.of(medication) as StockPanel.Visible

        assertEquals(BigDecimal("2"), panel.unitsLeft)
        assertNull(panel.daysLeft)
        assertTrue(!panel.warning)
        assertNull(panel.captionDays)
    }

    @Test
    fun `stock tracking without a threshold keeps the block and drops the warning`() {
        val medication = twiceADay(stock = "1", threshold = FIVE_DAYS).copy(stockThresholdDays = null)

        val panel = StockPanel.of(medication) as StockPanel.Visible

        assertEquals(BigDecimal.ONE, panel.unitsLeft)
        assertTrue(!panel.warning)
    }

    private fun twiceADay(stock: String, threshold: Int): Medication = Fixtures.scheduledMedication().copy(
        doseAmount = BigDecimal.ONE,
        times = listOf(Fixtures.MORNING, EVENING),
        stockLeft = BigDecimal(stock),
        stockThresholdDays = threshold,
    )

    private companion object {
        val EVENING: LocalTime = LocalTime.of(20, 0)
        const val FIVE_DAYS = 5
        const val FOUR_DAYS = 4
    }
}
