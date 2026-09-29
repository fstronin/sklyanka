package ru.stohastika.sklyanka.domain.intake

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.stohastika.sklyanka.domain.model.Fixtures
import java.time.LocalDateTime

@DisplayName("The intake window is a domain parameter, not a constant")
class IntakeWindowTest {

    private val window = EndOfDayIntakeWindow()

    @Test
    fun `the deadline of a morning intake is the next midnight`() {
        assertEquals(
            LocalDateTime.of(2026, 9, 26, 0, 0),
            window.deadlineFor(LocalDateTime.of(2026, 9, 25, 8, 0)),
        )
    }

    @ParameterizedTest(name = "at {0} the intake is still pending: {1}")
    @CsvSource(
        "2026-09-25T08:01, true",
        "2026-09-25T18:00, true",
        "2026-09-25T23:59, true",
        "2026-09-26T00:00, false",
        "2026-09-26T09:00, false",
    )
    fun `an intake stays markable until the end of its own day`(now: String, stillPending: Boolean) {
        val intake = Fixtures.pendingIntake()

        assertEquals(!stillPending, IntakeTransition.isExpired(intake, window, LocalDateTime.parse(now)))
    }

    @Test
    fun `intakes on both sides of midnight expire on different days`() {
        val late = Fixtures.pendingIntake().copy(scheduledAt = LocalDateTime.of(2026, 9, 25, 23, 55))
        val early = Fixtures.pendingIntake().copy(scheduledAt = LocalDateTime.of(2026, 9, 26, 0, 5))
        val midnight = LocalDateTime.of(2026, 9, 26, 0, 0)

        assertTrue(IntakeTransition.isExpired(late, window, midnight))
        assertFalse(IntakeTransition.isExpired(early, window, midnight))
    }

    @Test
    fun `the candidate bound of the end of day window covers whole past days only`() {
        assertEquals(
            LocalDateTime.of(2026, 9, 26, 0, 0),
            window.candidateBound(LocalDateTime.of(2026, 9, 26, 9, 0)),
        )
    }

    /**
     * Замена значения ОВ-4 на фиксированный интервал — новая реализация интерфейса
     * и ноль правок в переходах. Тест и есть доказательство этого утверждения.
     */
    @Test
    fun `replacing the window with a fixed interval changes no transition code`() {
        val threeHours = IntakeWindow { scheduledAt -> scheduledAt.plusHours(3) }
        val intake = Fixtures.pendingIntake()

        assertFalse(IntakeTransition.isExpired(intake, threeHours, LocalDateTime.of(2026, 9, 25, 10, 59)))
        assertTrue(IntakeTransition.isExpired(intake, threeHours, LocalDateTime.of(2026, 9, 25, 11, 0)))
    }
}
