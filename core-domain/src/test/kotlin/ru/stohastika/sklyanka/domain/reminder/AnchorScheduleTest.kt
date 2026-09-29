package ru.stohastika.sklyanka.domain.reminder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

@DisplayName("The anchor alarm lands on the next quiet night hour of local time")
class AnchorScheduleTest {

    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    private fun anchorFor(local: LocalDateTime): LocalDateTime =
        AnchorSchedule.next(local.atZone(zone).toInstant(), zone).atZone(zone).toLocalDateTime()

    @Test
    fun `a morning moment gets the anchor of the coming night`() {
        assertEquals(LocalDateTime.of(2026, 9, 26, 3, 17), anchorFor(LocalDateTime.of(2026, 9, 25, 9, 0)))
    }

    @Test
    fun `a moment after midnight but before the anchor gets today's anchor`() {
        assertEquals(LocalDateTime.of(2026, 9, 25, 3, 17), anchorFor(LocalDateTime.of(2026, 9, 25, 1, 0)))
    }

    @Test
    fun `the anchor moment itself is never planned for right now`() {
        // Якорь, поставленный на «сейчас», сработал бы немедленно и пересобрал окно
        // второй раз подряд, ничего не изменив.
        assertEquals(LocalDateTime.of(2026, 9, 26, 3, 17), anchorFor(LocalDateTime.of(2026, 9, 25, 3, 17)))
    }

    @Test
    fun `the anchor hour is a quiet night hour and not a round one`() {
        assertTrue(AnchorSchedule.AT.hour in 1..4, "anchor must be at night")
        assertTrue(AnchorSchedule.AT.minute != 0, "a round hour collects the alarm queue of every application")
    }

    @Test
    fun `the anchor follows the zone of the person`() {
        val samara = ZoneId.of("Europe/Samara")
        val moment = LocalDateTime.of(2026, 9, 25, 9, 0).atZone(zone).toInstant()

        val local = AnchorSchedule.next(moment, samara).atZone(samara).toLocalDateTime()

        assertEquals(LocalDateTime.of(2026, 9, 26, 3, 17), local)
    }
}
