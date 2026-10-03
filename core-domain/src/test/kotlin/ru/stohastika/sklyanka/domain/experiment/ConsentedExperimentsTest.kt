package ru.stohastika.sklyanka.domain.experiment

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.analytics.Analytics
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsent
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentDecision
import ru.stohastika.sklyanka.domain.analytics.AnalyticsConsentVersion
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEvent
import ru.stohastika.sklyanka.domain.analytics.AnalyticsLog
import ru.stohastika.sklyanka.domain.analytics.ExperimentExposed
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Гейт A/B: согласие, fail-safe, кэш и экспонирование (SKL-83, TDD §8).
 *
 * Проверка идёт на JVM с подменёнными вендором и кэшем: «до согласия — ноль запросов»
 * доказывается числом вызовов вендора, «ошибка не выходит наружу» — отсутствием
 * исключения и сохранённым снимком, а не чтением кода.
 */
class ConsentedExperimentsTest {

    private val vendor = FakeVendor()
    private val cache = FakeCache()
    private val analytics = RecordingAnalytics()
    private val log = RecordingLog()

    private fun gate(debuggable: Boolean = false, timeout: Duration = 1.seconds) =
        ConsentedExperiments(vendor, cache, analytics, debuggable, log, timeout)

    @Test
    fun `before the choice not a single variant is requested`() = runTest {
        val gate = gate()

        gate.applyConsent(AnalyticsConsent.NOT_ASKED)
        gate.refresh()

        assertEquals(0, vendor.calls.get())
        assertEquals(VariantKey.BASE, gate.variantOf(ExperimentId.TODAY_EMPTY_STATE))
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `a refusal keeps the base variant and the vendor silent`() = runTest {
        val gate = gate()

        gate.applyConsent(declined())
        gate.refresh()

        assertEquals(0, vendor.calls.get())
        assertEquals(VariantKey.BASE, gate.variantOf(ExperimentId.TODAY_EMPTY_STATE))
    }

    @Test
    fun `a debug build keeps the base variant and the vendor silent`() = runTest {
        val gate = gate(debuggable = true)

        gate.applyConsent(accepted())
        gate.refresh()

        assertEquals(0, vendor.calls.get())
        assertEquals(VariantKey.BASE, gate.variantOf(ExperimentId.TODAY_EMPTY_STATE))
    }

    @Test
    fun `after consent the cached snapshot applies before any refresh`() = runTest {
        val gate = gate()
        cache.write(mapOf(ExperimentId.TODAY_EMPTY_STATE to VariantKey.A))

        gate.applyConsent(accepted())

        assertEquals(0, vendor.calls.get())
        assertEquals(VariantKey.A, gate.variantOf(ExperimentId.TODAY_EMPTY_STATE))
    }

    @Test
    fun `refresh replaces the snapshot and persists it`() = runTest {
        val gate = gate()
        vendor.response = { mapOf(ExperimentId.TODAY_EMPTY_STATE to VariantKey.A) }

        gate.applyConsent(accepted())
        gate.refresh()

        assertEquals(VariantKey.A, gate.variantOf(ExperimentId.TODAY_EMPTY_STATE))
        assertEquals(mapOf(ExperimentId.TODAY_EMPTY_STATE to VariantKey.A), cache.writes.last())
        assertEquals(listOf(1), log.updated)
    }

    @Test
    fun `a new gate over the same cache survives a restart without a request`() = runTest {
        val first = gate()
        vendor.response = { mapOf(ExperimentId.TODAY_EMPTY_STATE to VariantKey.A) }
        first.applyConsent(accepted())
        first.refresh()

        val restarted = gate()
        restarted.applyConsent(accepted())

        assertEquals(1, vendor.calls.get())
        assertEquals(VariantKey.A, restarted.variantOf(ExperimentId.TODAY_EMPTY_STATE))
    }

    @Test
    fun `a timeout is invisible and keeps the base variant`() = runTest {
        val gate = gate()
        vendor.hang = true

        gate.applyConsent(accepted())
        gate.refresh()

        assertEquals(1, vendor.calls.get())
        assertEquals(VariantKey.BASE, gate.variantOf(ExperimentId.TODAY_EMPTY_STATE))
        assertTrue(log.unavailable.contains("timeout"))
    }

    @Test
    fun `a vendor failure is invisible and keeps the base variant`() = runTest {
        val gate = gate()
        vendor.failure = IllegalStateException("no service")

        gate.applyConsent(accepted())
        gate.refresh()

        assertEquals(VariantKey.BASE, gate.variantOf(ExperimentId.TODAY_EMPTY_STATE))
        assertTrue(log.unavailable.contains("IllegalStateException"))
    }

    @Test
    fun `a failure does not cancel an already received variant`() = runTest {
        val gate = gate()
        vendor.response = { mapOf(ExperimentId.TODAY_EMPTY_STATE to VariantKey.A) }
        gate.applyConsent(accepted())
        gate.refresh()

        vendor.failure = IllegalStateException("offline")
        gate.refresh()

        assertEquals(VariantKey.A, gate.variantOf(ExperimentId.TODAY_EMPTY_STATE))
        assertEquals(1, cache.writes.size)
    }

    @Test
    fun `turning analytics off clears the variant and stops the requests`() = runTest {
        val gate = gate()
        vendor.response = { mapOf(ExperimentId.TODAY_EMPTY_STATE to VariantKey.A) }
        gate.applyConsent(accepted())
        gate.refresh()
        assertEquals(VariantKey.A, gate.variantOf(ExperimentId.TODAY_EMPTY_STATE))

        gate.applyConsent(declined())
        val callsAfterDisabling = vendor.calls.get()
        gate.refresh()

        assertEquals(VariantKey.BASE, gate.variantOf(ExperimentId.TODAY_EMPTY_STATE))
        assertEquals(callsAfterDisabling, vendor.calls.get())
    }

    @Test
    fun `a variant outside the catalog is not applied`() = runTest {
        val gate = gate()
        vendor.response = { mapOf(ExperimentId.SETTINGS_TRANSFER_HINT to VariantKey.B) }

        gate.applyConsent(accepted())
        gate.refresh()

        assertEquals(VariantKey.BASE, gate.variantOf(ExperimentId.SETTINGS_TRANSFER_HINT))
        assertEquals(emptyMap<ExperimentId, VariantKey>(), cache.writes.last())
    }

    @Test
    fun `an unknown cached value is ignored`() = runTest {
        val gate = gate()
        cache.write(mapOf(ExperimentId.TODAY_EMPTY_STATE to VariantKey.C))

        gate.applyConsent(accepted())

        assertEquals(VariantKey.BASE, gate.variantOf(ExperimentId.TODAY_EMPTY_STATE))
    }

    @Test
    fun `exposure is sent once per non-base variant`() = runTest {
        val gate = gate()
        vendor.response = { mapOf(ExperimentId.TODAY_EMPTY_STATE to VariantKey.A) }
        gate.applyConsent(accepted())
        gate.refresh()

        gate.variantOf(ExperimentId.TODAY_EMPTY_STATE)
        gate.variantOf(ExperimentId.TODAY_EMPTY_STATE)

        assertEquals(
            listOf(ExperimentExposed(ExperimentId.TODAY_EMPTY_STATE, VariantKey.A)),
            analytics.events,
        )
    }

    @Test
    fun `the base variant is not exposed`() = runTest {
        val gate = gate()
        gate.applyConsent(accepted())

        gate.variantOf(ExperimentId.TODAY_EMPTY_STATE)

        assertTrue(analytics.events.isEmpty())
    }

    private fun accepted() = AnalyticsConsent(AnalyticsConsentDecision.ACCEPTED, AnalyticsConsentVersion.MATERIAL)

    private fun declined() = AnalyticsConsent(AnalyticsConsentDecision.DECLINED, AnalyticsConsentVersion.MATERIAL)
}

private class FakeVendor : ExperimentVendor {

    val calls = AtomicInteger()
    var response: () -> Map<ExperimentId, VariantKey> = { emptyMap() }
    var failure: Throwable? = null
    var hang = false

    override suspend fun fetchVariants(): Map<ExperimentId, VariantKey> {
        calls.incrementAndGet()
        if (hang) delay(Long.MAX_VALUE)
        failure?.let { throw it }
        return response()
    }
}

private class FakeCache(initial: Map<ExperimentId, VariantKey> = emptyMap()) : ExperimentCacheStore {

    private var stored = initial
    val writes = mutableListOf<Map<ExperimentId, VariantKey>>()

    override suspend fun read(): Map<ExperimentId, VariantKey> = stored

    override suspend fun write(variants: Map<ExperimentId, VariantKey>) {
        stored = variants
        writes += variants
    }
}

private class RecordingAnalytics : Analytics {

    val events = mutableListOf<AnalyticsEvent>()

    override fun track(event: AnalyticsEvent) {
        events += event
    }

    override suspend fun applyConsent(consent: AnalyticsConsent) = Unit
}

private class RecordingLog : AnalyticsLog {

    val updated = mutableListOf<Int>()
    val unavailable = mutableListOf<String>()

    override fun activated() = Unit

    override fun deactivated() = Unit

    override fun unavailable(reason: String) = Unit

    override fun debugSuppressed() = Unit

    override fun sendFailed(reason: String) = Unit

    override fun experimentsUpdated(count: Int) {
        updated += count
    }

    override fun experimentsUnavailable(reason: String) {
        unavailable += reason
    }
}
