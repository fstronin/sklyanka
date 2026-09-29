package ru.stohastika.sklyanka.domain.time

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

@DisplayName("TimeSource is substitutable and drives the domain clock")
class TimeSourceTest {

    @Test
    fun `fixed time source returns the configured instant and zone`() {
        val moment = Instant.parse("2026-10-03T05:00:00Z")
        val source: TimeSource = FixedTimeSource(moment, ZoneId.of("Europe/Moscow"))

        assertEquals(moment, source.now())
        assertEquals(ZoneId.of("Europe/Moscow"), source.zone())
    }

    @Test
    fun `advancing the clock crosses the local midnight boundary`() {
        val source = FixedTimeSource(
            instant = Instant.parse("2026-10-03T20:59:00Z"),
            zone = ZoneId.of("Europe/Moscow"),
        )
        val before = source.now().atZone(source.zone()).toLocalDate()

        source.advanceTo(Instant.parse("2026-10-03T21:01:00Z"))
        val after = source.now().atZone(source.zone()).toLocalDate()

        assertEquals(before.plusDays(1), after)
    }

    @Test
    fun `moving the zone keeps the instant but changes the local day`() {
        val source = FixedTimeSource(
            instant = Instant.parse("2026-10-03T22:30:00Z"),
            zone = ZoneId.of("Europe/Moscow"),
        )
        val moscowDay = source.now().atZone(source.zone()).toLocalDate()

        source.moveTo(ZoneId.of("UTC"))
        val utcDay = source.now().atZone(source.zone()).toLocalDate()

        assertEquals(moscowDay.minusDays(1), utcDay)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `turbine sees every emitted instant`() = runTest {
        val source = FixedTimeSource(Instant.parse("2026-10-03T05:00:00Z"))
        val ticks = flow {
            emit(source.now())
            source.advanceTo(source.now().plusSeconds(SECONDS_IN_MINUTE))
            emit(source.now())
        }

        ticks.test {
            assertEquals(Instant.parse("2026-10-03T05:00:00Z"), awaitItem())
            assertEquals(Instant.parse("2026-10-03T05:01:00Z"), awaitItem())
            awaitComplete()
        }
    }

    private companion object {
        const val SECONDS_IN_MINUTE = 60L
    }
}
