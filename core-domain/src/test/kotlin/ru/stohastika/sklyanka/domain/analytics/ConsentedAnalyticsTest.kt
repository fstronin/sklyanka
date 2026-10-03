package ru.stohastika.sklyanka.domain.analytics

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Гейт согласия: до решения и после выключения в sink не уходит ни одного вызова
 * (SKL-82). Правило доменное и живёт рядом с каталогом; подменяется только sink.
 *
 * Проверка идёт на JVM: «до согласия — ничего» доказывается числом вызовов активации
 * и отправки, а не чтением кода.
 */
class ConsentedAnalyticsTest {

    private val sink = FakeSink()
    private val log = RecordingLog()

    private fun analytics(debuggable: Boolean = false) = ConsentedAnalytics(sink, debuggable, log)

    private fun accepted() = AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL)

    @Test
    fun `before the choice nothing reaches the sink`() = runTest {
        val analytics = analytics()

        analytics.applyConsent(AnalyticsConsent.NOT_ASKED)
        analytics.track(AnalyticsEvent.ScreenView(ScreenName.TODAY))

        assertEquals(0, sink.activations)
        assertEquals(0, sink.events.size)
    }

    @Test
    fun `a refusal never activates the sink`() = runTest {
        val analytics = analytics()
        val declined = AnalyticsConsent(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL)

        analytics.applyConsent(declined)
        analytics.track(AnalyticsEvent.ScreenView(ScreenName.TODAY))

        assertEquals(0, sink.activations)
        assertEquals(0, sink.events.size)
    }

    @Test
    fun `an outdated consent keeps the contour silent`() = runTest {
        val analytics = analytics()
        val outdated = AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL - 1)

        analytics.applyConsent(outdated)
        analytics.track(AnalyticsEvent.ScreenView(ScreenName.TODAY))

        assertEquals(0, sink.activations)
        assertEquals(0, sink.events.size)
    }

    @Test
    fun `the accepted current consent activates the sink and passes events`() = runTest {
        val analytics = analytics()

        analytics.applyConsent(accepted())
        analytics.track(AnalyticsEvent.ScreenView(ScreenName.TODAY))

        assertEquals(1, sink.activations)
        assertEquals(listOf(AnalyticsEvent.ScreenView(ScreenName.TODAY)), sink.events)
    }

    @Test
    fun `disabling in the settings stops sending immediately`() = runTest {
        val analytics = analytics()
        analytics.applyConsent(accepted())
        analytics.track(AnalyticsEvent.ScreenView(ScreenName.TODAY))

        analytics.applyConsent(AnalyticsConsent(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL))
        analytics.track(AnalyticsEvent.ScreenView(ScreenName.MEDICATIONS))

        assertEquals(1, sink.deactivations)
        assertEquals(listOf(AnalyticsEvent.ScreenView(ScreenName.TODAY)), sink.events)
    }

    @Test
    fun `a debug build never activates the sink even with consent`() = runTest {
        val analytics = analytics(debuggable = true)

        analytics.applyConsent(accepted())
        analytics.track(AnalyticsEvent.ScreenView(ScreenName.TODAY))

        assertEquals(0, sink.activations)
        assertEquals(0, sink.events.size)
        assertTrue(log.debugSuppressed)
    }

    @Test
    fun `a failing activation leaves the contour silent and leaves a trace`() = runTest {
        sink.failActivation = true
        val analytics = analytics()

        analytics.applyConsent(accepted())
        analytics.track(AnalyticsEvent.ScreenView(ScreenName.TODAY))

        assertEquals(0, sink.events.size)
        assertEquals(1, log.unavailableReasons.size)
    }

    @Test
    fun `a failing send does not escape to the caller and leaves a trace`() = runTest {
        sink.failSend = true
        val analytics = analytics()
        analytics.applyConsent(accepted())

        analytics.track(AnalyticsEvent.ScreenView(ScreenName.TODAY))

        assertEquals(0, sink.events.size)
        assertEquals(1, log.sendFailureReasons.size)
    }

    private class FakeSink : AnalyticsSink {
        val events = mutableListOf<AnalyticsEvent>()
        var activations = 0
        var deactivations = 0
        var failActivation = false
        var failSend = false

        override suspend fun activate() {
            if (failActivation) error("no HMS Core")
            activations++
        }

        override suspend fun deactivate() {
            deactivations++
        }

        override fun send(event: AnalyticsEvent) {
            if (failSend) error("send failed")
            events += event
        }
    }

    private class RecordingLog : AnalyticsLog {
        var debugSuppressed = false
        val unavailableReasons = mutableListOf<String>()
        val sendFailureReasons = mutableListOf<String>()

        override fun activated() = Unit

        override fun deactivated() = Unit

        override fun unavailable(reason: String) {
            unavailableReasons += reason
        }

        override fun debugSuppressed() {
            debugSuppressed = true
        }

        override fun sendFailed(reason: String) {
            sendFailureReasons += reason
        }
    }
}
