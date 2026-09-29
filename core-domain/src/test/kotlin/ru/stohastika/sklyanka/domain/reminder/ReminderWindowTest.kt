package ru.stohastika.sklyanka.domain.reminder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

@DisplayName("The planning window covers 48 hours and both of its edges are decided")
class ReminderWindowTest {

    private val now: Instant = Instant.parse("2026-09-25T06:00:00Z")
    private val window = ReminderWindow.startingAt(now)

    @Test
    fun `the horizon is exactly 48 hours`() {
        assertEquals(Duration.ofHours(48), ReminderWindow.HORIZON)
        assertEquals(now.plus(Duration.ofHours(48)), window.toInclusive)
    }

    @Test
    fun `an intake due right now is inside the window`() {
        assertTrue(now in window)
    }

    @Test
    fun `an intake on the far edge is planned and a second later is not`() {
        assertTrue(now.plus(Duration.ofHours(48)) in window)
        assertFalse(now.plus(Duration.ofHours(48)).plusSeconds(1) in window)
    }

    @Test
    fun `a moment already past is outside the window`() {
        // Прошедший приём не напоминает о себе задним числом (НФТ-35): он остаётся
        // ожидающимся до конца суток и переводится проходом по просроченным.
        assertFalse(now.minusSeconds(1) in window)
    }
}
