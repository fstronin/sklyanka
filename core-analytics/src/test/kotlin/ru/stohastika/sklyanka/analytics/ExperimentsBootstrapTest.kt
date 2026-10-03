package ru.stohastika.sklyanka.analytics

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsent
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentDecision
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentStore
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentVersion
import ru.stohastika.sklyanka.domain.experiment.ExperimentId
import ru.stohastika.sklyanka.domain.experiment.Experiments
import ru.stohastika.sklyanka.domain.experiment.VariantKey

/**
 * Синхронизация A/B с согласием (SKL-83, TDD §11).
 *
 * Проверяется на JVM подменённым портом экспериментов: каждое решение применяется,
 * обновление следует за применением, а смена решения отменяет незавершённое обновление.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExperimentsBootstrapTest {

    @Test
    fun `every decision is applied and refreshed`() = runTest {
        val consent = FakeConsentStore(AnalyticsConsent.NOT_ASKED)
        val experiments = RecordingExperiments()
        val bootstrap = ExperimentsBootstrap(consent, experiments)

        backgroundScope.launch { bootstrap.run() }
        runCurrent()

        assertEquals(listOf(AnalyticsConsent.NOT_ASKED), experiments.applied)
        assertEquals(1, experiments.refreshes)

        consent.emit(accepted())
        runCurrent()

        assertEquals(listOf(AnalyticsConsent.NOT_ASKED, accepted()), experiments.applied)
        assertEquals(2, experiments.refreshes)
    }

    @Test
    fun `a new decision cancels the refresh in flight`() = runTest {
        val consent = FakeConsentStore(accepted())
        val experiments = HangingExperiments()
        val bootstrap = ExperimentsBootstrap(consent, experiments)

        backgroundScope.launch { bootstrap.run() }
        runCurrent()
        assertTrue("the refresh must start after consent is accepted", experiments.refreshStarted)
        assertFalse(experiments.refreshCancelled)

        consent.emit(declined())
        runCurrent()

        assertTrue("the previous refresh must be cancelled", experiments.refreshCancelled)
        assertEquals(declined(), experiments.applied.last())
    }

    private fun accepted() = AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL)

    private fun declined() = AnalyticsConsent(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL)
}

private class FakeConsentStore(initial: AnalyticsConsent) : AnalyticsConsentStore {

    private val state = MutableStateFlow(initial)

    fun emit(consent: AnalyticsConsent) {
        state.value = consent
    }

    override fun observe(): Flow<AnalyticsConsent> = state

    override suspend fun read(): AnalyticsConsent = state.value

    override suspend fun record(decision: AnalyticsConsentDecision, materialVersion: Int) {
        state.value = AnalyticsConsent(decision, materialVersion)
    }
}

private class RecordingExperiments : Experiments {

    val applied = mutableListOf<AnalyticsConsent>()
    var refreshes = 0
        private set

    override fun variantOf(experiment: ExperimentId): VariantKey = experiment.base

    override suspend fun applyConsent(consent: AnalyticsConsent) {
        applied += consent
    }

    override suspend fun refresh() {
        refreshes++
    }
}

private class HangingExperiments : Experiments {

    val applied = mutableListOf<AnalyticsConsent>()
    var refreshStarted = false
        private set
    var refreshCancelled = false
        private set

    override fun variantOf(experiment: ExperimentId): VariantKey = experiment.base

    override suspend fun applyConsent(consent: AnalyticsConsent) {
        applied += consent
    }

    override suspend fun refresh() {
        refreshStarted = true
        try {
            delay(Long.MAX_VALUE)
        } finally {
            refreshCancelled = true
        }
    }
}
