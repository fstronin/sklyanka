package ru.stohastika.sklyanka.domain.analytics

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Согласие как продуктовое правило (ПМ-18, ПМ-25, АН-2, TDD §7.3–§7.4).
 *
 * Показ согласия пишется до согласия и уходит только вместе с ним; отказ не отправляет
 * ничего и очищает очередь; `not_now` не уходит никогда; повторный вход из настроек —
 * отдельный факт (Р-96), а повторный показ того же источника дублей не плодит.
 */
class AnalyticsConsentTest {

    @Test
    fun `a first launch show waits for the decision and leaves with the accepted choice`() = runTest {
        val world = AnalyticsWorld(consent = AnalyticsConsentState.NOT_ASKED)
        val recorder = world.recorder(this)

        recorder.recordConsent(ConsentShown(ConsentShownSource.FIRST_LAUNCH))
        advanceUntilIdle()
        assertEquals(1, world.queue.size, "the show is written before consent")
        assertTrue(world.transport.batches.isEmpty(), "and nothing leaves without consent")

        DecideAnalyticsConsentUseCase(world.store, recorder)(AnalyticsConsentState.ALLOWED)
        advanceUntilIdle()

        assertEquals(1, world.transport.batches.size, "the accumulated show leaves with the choice")
        assertEquals(
            listOf(AnalyticsEventName.CONSENT_SHOWN, AnalyticsEventName.CONSENT_CHOICE),
            world.transport.deliveredNames(),
        )
        assertEquals(AnalyticsConsentState.ALLOWED, world.store.current)
    }

    @Test
    fun `a repeated show of the same source is not duplicated`() = runTest {
        val world = AnalyticsWorld(consent = AnalyticsConsentState.NOT_ASKED)
        val recorder = world.recorder(this)

        recorder.recordConsent(ConsentShown(ConsentShownSource.FIRST_LAUNCH))
        recorder.recordConsent(ConsentShown(ConsentShownSource.FIRST_LAUNCH))
        advanceUntilIdle()

        assertEquals(1, world.queue.size)
    }

    @Test
    fun `a show from another source is a new fact`() = runTest {
        val world = AnalyticsWorld(consent = AnalyticsConsentState.NOT_ASKED)
        val recorder = world.recorder(this)

        recorder.recordConsent(ConsentShown(ConsentShownSource.FIRST_LAUNCH))
        recorder.recordConsent(ConsentShown(ConsentShownSource.SETTINGS))
        advanceUntilIdle()

        assertEquals(2, world.queue.size, "R-96: a show from the settings is another show")
    }

    @Test
    fun `a refusal stops the transport and empties the queue`() = runTest {
        val world = AnalyticsWorld(consent = AnalyticsConsentState.NOT_ASKED, available = false)
        val recorder = world.recorder(this)

        recorder.recordConsent(ConsentShown(ConsentShownSource.FIRST_LAUNCH))
        advanceUntilIdle()

        DecideAnalyticsConsentUseCase(world.store, recorder)(AnalyticsConsentState.DECLINED)
        advanceUntilIdle()

        assertEquals(AnalyticsConsentState.DECLINED, world.store.current)
        assertEquals(1, world.transport.stops)
        assertEquals(0, world.queue.size)
        assertTrue(world.transport.batches.isEmpty(), "nothing leaves after a refusal")
    }

    @Test
    fun `the not now answer is never sent`() = runTest {
        val world = AnalyticsWorld(consent = AnalyticsConsentState.NOT_ASKED)
        val recorder = world.recorder(this)

        DecideAnalyticsConsentUseCase(world.store, recorder)(AnalyticsConsentState.DECLINED)
        advanceUntilIdle()

        val choices = world.transport.batches.flatten().filter { it.name == AnalyticsEventName.CONSENT_CHOICE }
        assertTrue(choices.isEmpty(), "a refusal produces no choice record")
        assertFalse(choices.any { it.properties[AnalyticsProperty.CHOICE] == ConsentAnswer.NOT_NOW.wire })
    }

    @Test
    fun `enabling from the settings writes the show, the choice and the enablement`() = runTest {
        val world = AnalyticsWorld(consent = AnalyticsConsentState.NOT_ASKED)
        val recorder = world.recorder(this)

        DecideAnalyticsConsentUseCase(world.store, recorder)(
            AnalyticsConsentState.ALLOWED,
            AnalyticsConsentSource.SETTINGS,
        )
        advanceUntilIdle()

        assertEquals(
            listOf(
                AnalyticsEventName.CONSENT_SHOWN,
                AnalyticsEventName.CONSENT_CHOICE,
                AnalyticsEventName.ANALYTICS_ENABLED,
            ),
            world.transport.deliveredNames(),
        )
        assertEquals(
            ConsentShownSource.SETTINGS.wire,
            world.transport.delivered.first().properties[AnalyticsProperty.SOURCE],
        )
    }

    @Test
    fun `disabling from the settings writes nothing and clears the queue`() = runTest {
        val world = AnalyticsWorld(consent = AnalyticsConsentState.ALLOWED, available = false)
        val recorder = world.recorder(this)

        recorder.record(ScreenView(AnalyticsScreen.TODAY))
        advanceUntilIdle()
        assertEquals(1, world.queue.size)

        DecideAnalyticsConsentUseCase(world.store, recorder)(
            AnalyticsConsentState.DECLINED,
            AnalyticsConsentSource.SETTINGS,
        )
        advanceUntilIdle()

        assertEquals(0, world.queue.size)
        assertTrue(world.transport.batches.isEmpty())
        assertEquals(1, world.transport.stops)
    }

    @Test
    fun `the screen is shown until a decision exists`() = runTest {
        val notAsked = AnalyticsWorld(consent = AnalyticsConsentState.NOT_ASKED)
        val allowed = AnalyticsWorld(consent = AnalyticsConsentState.ALLOWED)
        val declined = AnalyticsWorld(consent = AnalyticsConsentState.DECLINED)

        assertTrue(ShouldShowAnalyticsConsentUseCase(notAsked.store)())
        assertFalse(ShouldShowAnalyticsConsentUseCase(allowed.store)())
        assertFalse(ShouldShowAnalyticsConsentUseCase(declined.store)())
    }

    @Test
    fun `observing the consent shows a decision without a second screen`() = runTest {
        val world = AnalyticsWorld(consent = AnalyticsConsentState.NOT_ASKED)
        val observe = ObserveAnalyticsConsentUseCase(world.store)

        assertEquals(AnalyticsConsentState.NOT_ASKED, observe().first())
        world.store.decide(AnalyticsConsentState.DECLINED)
        assertEquals(AnalyticsConsentState.DECLINED, observe().first())
    }

    @Test
    fun `not asked is not a decision`() = runTest {
        val world = AnalyticsWorld()
        val decide = DecideAnalyticsConsentUseCase(world.store, world.recorder(this))

        val failure = runCatching { decide(AnalyticsConsentState.NOT_ASKED) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException, "not asked is not a decision")
        assertEquals(AnalyticsConsentState.NOT_ASKED, world.store.current)
        assertEquals(0, world.store.writes)
    }
}
