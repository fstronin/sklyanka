package ru.stohastika.sklyanka.acceptance.world

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Часы сценария: пояс и момент — разные вещи (НФТ-34), а граница суток считается
 * по местной полуночи (НФТ-37).
 */
class ScenarioClockTest {

    @Test
    fun `the flight changes the zone and keeps the moment`() {
        val clock = ScenarioClock()
        clock.moveTo(LocalDateTime.of(2026, 9, 25, 12, 0))
        val moment = clock.now()

        clock.shiftZone(3)

        assertEquals(moment, clock.now(), "a flight is not a time machine")
        assertEquals(ZoneId.ofOffset("", java.time.ZoneOffset.ofHours(6)), clock.zone)
        assertEquals(LocalDateTime.of(2026, 9, 25, 15, 0), clock.nowLocal(), "local time moves with the zone")
    }

    @Test
    fun `the day boundary is the local midnight of the current zone`() {
        val clock = ScenarioClock()
        clock.moveTo(LocalDateTime.of(2026, 9, 25, 23, 30))

        clock.shiftZone(2)

        assertEquals(LocalDate.of(2026, 9, 26), clock.today(), "past local midnight the day is the next one")
    }

    @Test
    fun `the default moment is the one the backgrounds name`() {
        val clock = ScenarioClock()

        assertEquals(LocalDateTime.of(2026, 9, 25, 7, 0), clock.nowLocal())
        assertEquals(ZoneId.of("Europe/Moscow"), clock.zone)
    }
}
