package ru.stohastika.sklyanka.domain.analytics

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * Запись и отправка событий (АН-1, АН-2, АН-5, TDD §7.1–§7.2).
 *
 * Гейт согласия, глотание отказов, пачка не больше лимита и очередь, которая не растёт
 * бесконечно, — четыре свойства, без которых «аналитика по согласию» превращается
 * в отправку того, чего человек не разрешал, или в потерянные метрики.
 */
class AnalyticsRecorderTest {

    @Test
    fun `without consent nothing is written and nothing is sent`() = runTest {
        val world = AnalyticsWorld(consent = AnalyticsConsentState.NOT_ASKED)
        val recorder = world.recorder(this)

        recorder.record(ScreenView(AnalyticsScreen.TODAY))
        advanceUntilIdle()

        assertEquals(0, world.queue.size)
        assertTrue(world.transport.batches.isEmpty())
        assertEquals(0, world.transport.starts)
    }

    @Test
    fun `a refusal writes and sends nothing`() = runTest {
        val world = AnalyticsWorld(consent = AnalyticsConsentState.DECLINED)
        val recorder = world.recorder(this)

        recorder.record(ReportExported(OperationResult.SUCCESS))
        advanceUntilIdle()

        assertEquals(0, world.queue.size)
        assertTrue(world.transport.batches.isEmpty())
    }

    @Test
    fun `a product event cannot be written through the consent path`() = runTest {
        val world = AnalyticsWorld(consent = AnalyticsConsentState.NOT_ASKED)
        val recorder = world.recorder(this)

        recorder.recordConsent(ScreenView(AnalyticsScreen.TODAY))
        advanceUntilIdle()

        assertEquals(0, world.queue.size, "a product event is not written without consent")
    }

    @Test
    fun `a consent event does not bypass its own path`() = runTest {
        val world = AnalyticsWorld(consent = AnalyticsConsentState.ALLOWED)
        val recorder = world.recorder(this)

        recorder.record(ConsentShown(ConsentShownSource.FIRST_LAUNCH))
        advanceUntilIdle()

        assertEquals(0, world.queue.size, "a consent show is written only through the consent path")
        assertTrue(world.transport.batches.isEmpty())
    }

    @Test
    fun `with consent the event is written and delivered`() = runTest {
        val world = AnalyticsWorld(consent = AnalyticsConsentState.ALLOWED)
        val recorder = world.recorder(this)

        recorder.record(IntakeAction(IntakeActionKind.TAKEN, ActionSource.PHONE))
        advanceUntilIdle()

        assertEquals(listOf(AnalyticsEventName.INTAKE_ACTION), world.transport.deliveredNames())
        assertEquals(0, world.queue.size, "delivered records leave the queue")
        assertTrue(world.transport.starts >= 1, "the transport is opened before delivery")
    }

    @Test
    fun `a refused delivery keeps the records for the next attempt`() = runTest {
        val world = AnalyticsWorld(consent = AnalyticsConsentState.ALLOWED, accepts = false)
        val recorder = world.recorder(this)

        recorder.record(ScreenView(AnalyticsScreen.SETTINGS))
        advanceUntilIdle()

        assertEquals(1, world.transport.batches.size, "the attempt happened")
        assertEquals(1, world.queue.size, "but the record stayed queued")
        assertTrue(world.transport.delivered.isEmpty())
    }

    @Test
    fun `an unavailable provider keeps the records queued`() = runTest {
        val world = AnalyticsWorld(consent = AnalyticsConsentState.ALLOWED, available = false)
        val recorder = world.recorder(this)

        recorder.record(ScreenView(AnalyticsScreen.ARCHIVE))
        advanceUntilIdle()

        assertEquals(1, world.queue.size)
        assertTrue(world.transport.batches.isEmpty(), "without a key the provider is never called")
        assertEquals(0, world.transport.starts)
    }

    @Test
    fun `revoking consent stops the transport and empties the queue`() = runTest {
        val world = AnalyticsWorld(consent = AnalyticsConsentState.ALLOWED, available = false)
        val recorder = world.recorder(this)

        recorder.record(ScreenView(AnalyticsScreen.TODAY))
        recorder.record(ScreenView(AnalyticsScreen.SETTINGS))
        advanceUntilIdle()
        assertEquals(2, world.queue.size)

        recorder.onConsentDeclined()
        advanceUntilIdle()

        assertEquals(1, world.transport.stops, "the transport closes immediately")
        assertEquals(0, world.queue.size, "the queue is emptied entirely")
        assertEquals(1, world.queue.clears)
    }

    @Test
    fun `a batch is never bigger than the limit`() = runTest {
        val world = AnalyticsWorld(
            consent = AnalyticsConsentState.ALLOWED,
            limits = AnalyticsLimits(batchSize = 2),
        )
        val recorder = world.recorder(this)

        repeat(5) { recorder.record(ScreenView(AnalyticsScreen.TODAY)) }
        advanceUntilIdle()

        assertTrue(world.transport.batches.all { it.size <= 2 }, "a batch is never bigger than the limit")
        assertEquals(5, world.transport.delivered.size)
        assertEquals(0, world.queue.size)
    }

    @Test
    fun `the queue never grows over the cap`() = runTest {
        val world = AnalyticsWorld(
            consent = AnalyticsConsentState.ALLOWED,
            available = false,
            limits = AnalyticsLimits(maxEvents = 3),
        )
        val recorder = world.recorder(this)

        repeat(10) { recorder.record(ScreenView(AnalyticsScreen.TODAY)) }
        advanceUntilIdle()

        assertEquals(3, world.queue.size)
    }

    @Test
    fun `the default queue cap is five hundred records`() = runTest {
        val world = AnalyticsWorld(consent = AnalyticsConsentState.ALLOWED, available = false)
        val recorder = world.recorder(this)

        repeat(520) { recorder.record(ScreenView(AnalyticsScreen.TODAY)) }
        advanceUntilIdle()

        assertEquals(500, world.queue.size)
    }

    @Test
    fun `records older than the retention period are dropped first`() = runTest {
        val world = AnalyticsWorld(consent = AnalyticsConsentState.ALLOWED)
        val recorder = world.recorder(this)
        val stale = Instant.parse("2026-04-20T08:00:00Z")

        world.queue.append(ScreenView(AnalyticsScreen.TODAY).toRecord(stale))
        recorder.record(ScreenView(AnalyticsScreen.SETTINGS))
        advanceUntilIdle()

        val delivered = world.transport.delivered.single()
        assertEquals(mapOf(AnalyticsProperty.SCREEN to "settings"), delivered.properties)
        assertEquals(0, world.queue.size)
    }

    @Test
    fun `the retention period is seven days`() {
        assertEquals(Duration.ofDays(7), AnalyticsLimits().ttl)
        assertEquals(500, AnalyticsLimits().maxEvents)
        assertEquals(100, AnalyticsLimits().batchSize)
    }

    @Test
    fun `a repeated drain does not send anything twice`() = runTest {
        val world = AnalyticsWorld(consent = AnalyticsConsentState.ALLOWED)
        val recorder = world.recorder(this)

        recorder.record(ScreenView(AnalyticsScreen.REPORT))
        advanceUntilIdle()
        recorder.drain()
        recorder.drain()
        advanceUntilIdle()

        assertEquals(1, world.transport.batches.size)
        assertEquals(1, world.transport.delivered.size)
    }
}
