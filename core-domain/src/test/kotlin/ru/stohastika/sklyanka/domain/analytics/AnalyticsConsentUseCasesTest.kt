package ru.stohastika.sklyanka.domain.analytics

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Решения согласия: что записывается, что отправляется и в каком порядке (TDD §7.3).
 *
 * Проверяется на фейковом канале: до согласия и после выключения в нём не должно быть
 * ни одного вызова. Порядок «запись → активация → события» зафиксирован тестом, а не
 * комментарием: событие, ушедшее в выключенный контур, теряется молча.
 */
@DisplayName("Consent decisions record the choice and report only what the contour allows")
class AnalyticsConsentUseCasesTest {

    private val analytics = RecordingAnalytics()
    private val store = FakeAnalyticsConsentStore()

    private val grant = GrantAnalyticsConsentUseCase(store, analytics)
    private val decline = DeclineAnalyticsConsentUseCase(store, analytics)
    private val setEnabled = SetAnalyticsEnabledUseCase(store, analytics)
    private val observeEnabled = ObserveAnalyticsConsentUseCase(store)

    @Test
    fun `granting records the current version, activates the contour and reports the outcome`() = runTest {
        grant(ConsentSource.FIRST_LAUNCH)

        val expected = AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL)
        assertEquals(expected, store.read())
        assertEquals(
            listOf(
                "applyConsent:ACCEPTED:${AnalyticsConsentVersion.MATERIAL}",
                "track:ConsentShown",
                "track:ConsentChoice",
            ),
            analytics.calls,
        )
    }

    @Test
    fun `the first refusal records the decision and sends nothing at all`() = runTest {
        decline()

        val expected = AnalyticsConsent(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL)
        assertEquals(expected, store.read())
        assertEquals(
            listOf("applyConsent:DECLINED:${AnalyticsConsentVersion.MATERIAL}"),
            analytics.calls,
        )
    }

    @Test
    fun `a repeated refusal after a policy update reports not_now before the contour goes silent`() = runTest {
        store.seed(AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL))

        decline(policyUpdate = true)

        assertEquals(
            listOf(
                "track:ConsentShown",
                "track:ConsentChoice",
                "applyConsent:DECLINED:${AnalyticsConsentVersion.MATERIAL}",
            ),
            analytics.calls,
        )
    }

    @Test
    fun `enabling in the settings records the consent and reports the switch`() = runTest {
        setEnabled(true)

        val expected = AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL)
        assertEquals(expected, store.read())
        assertEquals(
            listOf(
                "applyConsent:ACCEPTED:${AnalyticsConsentVersion.MATERIAL}",
                "track:AnalyticsEnabled",
            ),
            analytics.calls,
        )
    }

    @Test
    fun `disabling in the settings stops sending without a sending event`() = runTest {
        store.seed(AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL))

        setEnabled(false)

        val expected = AnalyticsConsent(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL)
        assertEquals(expected, store.read())
        assertEquals(
            listOf("applyConsent:DECLINED:${AnalyticsConsentVersion.MATERIAL}"),
            analytics.calls,
        )
    }

    @Test
    fun `the switch asks the consent rule, not the raw decision`() = runTest {
        assertFalse(observeEnabled().first())

        store.seed(AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL - 1))
        assertFalse(observeEnabled().first(), "an outdated version must read as off")

        store.seed(AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL))
        assertTrue(observeEnabled().first())

        store.seed(AnalyticsConsent(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL))
        assertFalse(observeEnabled().first())
    }

    @Test
    fun `accepting from the settings records the decision and names the settings entry`() = runTest {
        val acceptFromSettings = AcceptAnalyticsFromSettingsUseCase(store, analytics)

        acceptFromSettings()

        val expected = AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL)
        assertEquals(expected, store.read())
        assertEquals(
            listOf(
                "applyConsent:ACCEPTED:${AnalyticsConsentVersion.MATERIAL}",
                "track:ConsentShown",
                "track:AnalyticsEnabled",
            ),
            analytics.calls,
        )
    }

    private class RecordingAnalytics : Analytics {
        val calls = mutableListOf<String>()

        override fun track(event: AnalyticsEvent) {
            calls += "track:${event::class.simpleName}"
        }

        override suspend fun applyConsent(consent: AnalyticsConsent) {
            calls += "applyConsent:${consent.decision}:${consent.materialVersion}"
        }
    }

    private class FakeAnalyticsConsentStore : AnalyticsConsentStore {
        private val state = MutableStateFlow(AnalyticsConsent.NOT_ASKED)

        fun seed(consent: AnalyticsConsent) {
            state.value = consent
        }

        override fun observe(): Flow<AnalyticsConsent> = state

        override suspend fun read(): AnalyticsConsent = state.value

        override suspend fun record(decision: AnalyticsConsentDecision, materialVersion: Int) {
            state.value = AnalyticsConsent(decision, materialVersion)
        }
    }
}
