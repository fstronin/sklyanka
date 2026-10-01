package ru.stohastika.sklyanka.domain.reminder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * Граница порога показа обычного срабатывания (ПМ-22, ОВ-109).
 *
 * Чистый JVM-тест без портов: правило — функция двух моментов. Ровно 30 минут —
 * ещё показывается, секундой больше — уже нет; будущее плановое время порога
 * не превышает.
 */
@DisplayName("An ordinary firing is shown only within thirty minutes of lateness")
class FiredShowPolicyTest {

    private val scheduledAt: Instant = Instant.parse("2026-09-25T11:00:00Z")

    @Test
    fun `a planned moment still in the future is shown`() {
        assertTrue(FiredShowPolicy.shouldShow(now = scheduledAt.minusSeconds(60), scheduledAt = scheduledAt))
    }

    @Test
    fun `lateness of one second under the threshold is shown`() {
        assertTrue(FiredShowPolicy.shouldShow(now = scheduledAt.plusSeconds(1799), scheduledAt = scheduledAt))
    }

    @Test
    fun `lateness of exactly the threshold is still shown`() {
        assertTrue(
            FiredShowPolicy.shouldShow(now = scheduledAt.plus(Duration.ofMinutes(30)), scheduledAt = scheduledAt),
            "exactly 1800 seconds is a show, not a suppression",
        )
    }

    @Test
    fun `lateness of one second over the threshold is suppressed`() {
        assertFalse(FiredShowPolicy.shouldShow(now = scheduledAt.plusSeconds(1801), scheduledAt = scheduledAt))
    }

    /**
     * Одна граница на продукт (ПМ-22, НАП-41): порог обычного срабатывания обязан
     * совпадать с порогом восстановленного повтора, иначе решение разъедется молча.
     */
    @Test
    fun `the threshold is the same as the restored repeat one`() {
        assertEquals(
            DeferredShowPolicy.MAX_LATENESS,
            FiredShowPolicy.MAX_LATENESS,
            "one product-wide boundary of showing",
        )
    }
}
