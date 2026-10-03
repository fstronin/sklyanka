package ru.stohastika.sklyanka.analytics

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.stohastika.sklyanka.domain.analytics.Analytics
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsent
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentDecision
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentStore
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentVersion
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEvent

/**
 * Старт приложения приводит контур к записанному решению (TDD §10).
 *
 * Задача bootstrap — ничего не решать: прочитать решение и передать его гейту. Решение
 * о том, отправлять или молчать, остаётся за [ConsentedAnalytics] и проверено там.
 */
class AnalyticsBootstrapTest {

    @Test
    fun `the stored decision is applied to the contour at start`() = runTest {
        val declined = AnalyticsConsent(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL)
        val store = FakeStore(declined)
        val analytics = RecordingAnalytics()

        AnalyticsBootstrap(store, analytics).restore()

        assertEquals(listOf(declined), analytics.applied)
    }

    @Test
    fun `an empty consent is applied as not asked`() = runTest {
        val store = FakeStore(AnalyticsConsent.NOT_ASKED)
        val analytics = RecordingAnalytics()

        AnalyticsBootstrap(store, analytics).restore()

        assertEquals(listOf(AnalyticsConsent.NOT_ASKED), analytics.applied)
    }

    private class FakeStore(private val current: AnalyticsConsent) : AnalyticsConsentStore {
        override fun observe(): Flow<AnalyticsConsent> = MutableStateFlow(current)

        override suspend fun read(): AnalyticsConsent = current

        override suspend fun record(decision: AnalyticsConsentDecision, materialVersion: Int) = Unit
    }

    private class RecordingAnalytics : Analytics {
        val applied = mutableListOf<AnalyticsConsent>()

        override fun track(event: AnalyticsEvent) = Unit

        override suspend fun applyConsent(consent: AnalyticsConsent) {
            applied += consent
        }
    }
}
