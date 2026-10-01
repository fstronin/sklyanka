package ru.stohastika.sklyanka.domain.reminder

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * Граница порога показа восстановленного повтора (НАП-41, ОВ-99).
 *
 * Чистый JVM-тест без портов: правило — функция двух моментов. Ровно 30 минут —
 * ещё показывается, секундой больше — уже нет; будущий момент порога не превышает.
 */
@DisplayName("A restored repeat is shown only within thirty minutes of lateness")
class DeferredShowPolicyTest {

    private val fireAt: Instant = Instant.parse("2026-09-25T11:16:00Z")

    @Test
    fun `a moment still in the future is shown`() {
        assertTrue(DeferredShowPolicy.shouldShow(now = fireAt.minusSeconds(60), fireAt = fireAt))
    }

    @Test
    fun `lateness of one second under the threshold is shown`() {
        assertTrue(DeferredShowPolicy.shouldShow(now = fireAt.plusSeconds(1799), fireAt = fireAt))
    }

    @Test
    fun `lateness of exactly the threshold is still shown`() {
        assertTrue(
            DeferredShowPolicy.shouldShow(now = fireAt.plus(Duration.ofMinutes(30)), fireAt = fireAt),
            "exactly 1800 seconds is a show, not a drop",
        )
    }

    @Test
    fun `lateness of one second over the threshold is dropped`() {
        assertFalse(DeferredShowPolicy.shouldShow(now = fireAt.plusSeconds(1801), fireAt = fireAt))
    }
}
