package ru.heuri.sklyanka.domain.stock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.heuri.sklyanka.domain.intake.StockEffect
import ru.heuri.sklyanka.domain.model.IntakeStatus
import java.math.BigDecimal

/**
 * Правило списания остатка (ПР-5, ИНВ-8, ИНВ-19).
 *
 * Сценарии группы «Списание при отметке» и «Ноль и граница» из
 * `docs/tasks/SKL-12/acceptance.feature`, снятые до уровня чистой функции.
 */
class StockLedgerTest {

    @Test
    fun `a taken intake spends one dose`() {
        val change = StockLedger.applyIntakeResult(stock("30"), DOSE, IntakeStatus.TAKEN)

        assertEquals(stock("29"), change?.after)
        assertEquals(stock("30"), change?.before)
        assertFalse(change?.clamped == true, "a full dose was available")
    }

    @Test
    fun `a deliberate skip leaves the stock alone`() {
        assertNull(StockLedger.applyIntakeResult(stock("30"), DOSE, IntakeStatus.SKIPPED))
    }

    @Test
    fun `an automatic miss leaves the stock alone`() {
        // Приложение не знает, принял человек лекарство или нет; списание по догадке
        // рано или поздно даёт ложное «пора покупать» (решение SKL-12).
        assertNull(StockLedger.applyIntakeResult(stock("30"), DOSE, IntakeStatus.MISSED))
    }

    @Test
    fun `a medication without stock tracking is not subject to the rule`() {
        assertNull(StockLedger.applyIntakeResult(null, DOSE, IntakeStatus.TAKEN))
    }

    @Test
    fun `the last unit brings the stock down to zero`() {
        val change = StockLedger.applyIntakeResult(stock("1"), DOSE, IntakeStatus.TAKEN)

        assertEquals(BigDecimal.ZERO, change?.after)
        assertFalse(change?.clamped == true, "exactly one unit was there")
    }

    @Test
    fun `a dose larger than the stock never goes below zero`() {
        val change = StockLedger.applyIntakeResult(stock("1"), BigDecimal("2"), IntakeStatus.TAKEN)

        assertEquals(BigDecimal.ZERO, change?.after)
        assertTrue(change?.clamped == true, "the shortage has to stay visible in the log")
    }

    @Test
    fun `marking an intake on an empty package keeps the stock at zero`() {
        val change = StockLedger.applyIntakeResult(BigDecimal.ZERO, DOSE, IntakeStatus.TAKEN)

        assertEquals(BigDecimal.ZERO, change?.after)
        assertTrue(change?.clamped == true)
    }

    @Test
    fun `a half tablet dose spends a half`() {
        val change = StockLedger.applyIntakeResult(stock("9"), BigDecimal("0.5"), IntakeStatus.TAKEN)

        assertEquals(BigDecimal("8.5"), change?.after)
    }

    @Test
    fun `a superseded mark gives the dose back`() {
        // Замещение отметки не имеет права списать дозу второй раз (ЧАС-23): остаток
        // приводится к результату победившей отметки, а не накапливает списания.
        val change = StockLedger.apply(stock("29"), DOSE, StockEffect.RESTORE)

        assertEquals(stock("30"), change?.after)
    }

    @Test
    fun `an unchanged result leaves the stock alone`() {
        assertNull(StockLedger.apply(stock("29"), DOSE, StockEffect.NONE))
    }

    @Test
    fun `a pending intake spends nothing`() {
        assertEquals(StockEffect.NONE, StockLedger.effectOf(IntakeStatus.PENDING))
    }

    private fun stock(value: String) = BigDecimal(value)

    private companion object {
        val DOSE: BigDecimal = BigDecimal.ONE
    }
}
