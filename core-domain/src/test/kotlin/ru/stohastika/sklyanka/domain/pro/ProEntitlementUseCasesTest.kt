package ru.stohastika.sklyanka.domain.pro

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.schedule.RecordingOperationLog
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Проверка подписки при запуске и чтение состояния по кэшу (`ADR-0015` п. 1–2, Р-68 п. 7).
 */
class ProEntitlementUseCasesTest {

    private val clock = FixedTimeSource(Instant.parse("2026-10-13T09:00:00Z"), ZoneId.of("Europe/Moscow"))
    private val store = FakeProStore()
    private val log = RecordingOperationLog()

    private val november = LocalDate.of(2026, 11, 13)

    @Test
    fun `the store answer about ownership lands in the cache`() = runTest {
        val cache = InMemoryProSubscriptionCache()
        store.ownership = ProOwned.Owned(ProProductId.MONTHLY, november)

        RefreshProSubscriptionUseCase(store, cache, log, clock)()

        assertEquals(listOf<ProSubscription>(ProSubscription.Active(ProProductId.MONTHLY, november)), cache.writes)
        assertTrue(log.events().contains(OpEvent.PRO_SUBSCRIPTION_CHECKED))
    }

    @Test
    fun `no purchase is an answer too and it is written`() = runTest {
        val cache = InMemoryProSubscriptionCache(ProSubscription.Active(ProProductId.MONTHLY, november))
        store.ownership = ProOwned.None

        RefreshProSubscriptionUseCase(store, cache, log, clock)()

        assertEquals(listOf<ProSubscription>(ProSubscription.None), cache.writes)
    }

    @Test
    fun `records that are not active end the access and the case is named`() = runTest {
        val cache = InMemoryProSubscriptionCache(ProSubscription.Active(ProProductId.MONTHLY, november))
        store.ownership = ProOwned.NotActive

        RefreshProSubscriptionUseCase(store, cache, log, clock)()

        assertEquals(listOf<ProSubscription>(ProSubscription.None), cache.writes)
        assertTrue(log.records.any { it.detail == "not_active" })
    }

    @Test
    fun `an expired record in the cache asks the store again on the way back`() = runTest {
        val expired = InMemoryProSubscriptionCache(
            ProSubscription.Active(ProProductId.MONTHLY, LocalDate.of(2026, 10, 12)),
        )

        assertTrue(ProSubscriptionRefreshDueUseCase(expired, clock)())
    }

    @Test
    fun `an active record and an empty cache do not ask the store again`() = runTest {
        val active = InMemoryProSubscriptionCache(ProSubscription.Active(ProProductId.MONTHLY, november))

        assertFalse(ProSubscriptionRefreshDueUseCase(active, clock)())
        assertFalse(ProSubscriptionRefreshDueUseCase(InMemoryProSubscriptionCache(), clock)())
    }

    @Test
    fun `a failed check writes nothing and keeps the access`() = runTest {
        val cache = InMemoryProSubscriptionCache(ProSubscription.Active(ProProductId.MONTHLY, november))
        store.ownership = ProOwned.Unavailable

        RefreshProSubscriptionUseCase(store, cache, log, clock)()

        assertTrue(cache.writes.isEmpty())
        assertTrue(ObserveProStatusUseCase(cache, clock)().included)
    }

    @Test
    fun `the status counts the expiry by the days of the interface`() = runTest {
        val cache =
            InMemoryProSubscriptionCache(ProSubscription.Active(ProProductId.MONTHLY, LocalDate.of(2026, 10, 12)))
        val status = ObserveProStatusUseCase(cache, clock)()

        assertFalse(status.included)
        assertEquals(ProNotice.STATE_INACTIVE, status.notice)
    }

    @Test
    fun `the last day of the subscription is still included`() = runTest {
        val cache = InMemoryProSubscriptionCache(ProSubscription.Active(ProProductId.MONTHLY, clock.today()))

        val status = ObserveProStatusUseCase(cache, clock)()

        assertTrue(status.included)
        assertEquals(ProNotice.STATE_ACTIVE_UNTIL, status.notice)
        assertEquals(clock.today(), status.until)
    }

    @Test
    fun `without a check the subscription is simply absent`() = runTest {
        val status = ObserveProStatusUseCase(InMemoryProSubscriptionCache(), clock)()

        assertFalse(status.included)
        assertEquals(ProNotice.STATE_INACTIVE, status.notice)
    }
}
