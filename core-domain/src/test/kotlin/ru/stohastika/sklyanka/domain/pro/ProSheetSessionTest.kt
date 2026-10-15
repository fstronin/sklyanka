package ru.stohastika.sklyanka.domain.pro

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.schedule.RecordingOperationLog
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Переходы шторки витрины — сценарии `SKL-122.feature` и решения Р-67, Р-68, Р-73, Р-75, Р-78.
 *
 * Прогон идёт без Android намеренно: это те же правила, которыми живёт поверхность,
 * и приёмка сценариев исполняет их, а не их пересказ.
 */
class ProSheetSessionTest {

    private val clock = FixedTimeSource(Instant.parse("2026-10-13T09:00:00Z"), ZoneId.of("Europe/Moscow"))
    private val store = FakeProStore()
    private val cache = InMemoryProSubscriptionCache()
    private val log = RecordingOperationLog()
    private val session = ProSheetSession(store, cache, log, clock)

    private val november = LocalDate.of(2026, 11, 13)

    @Test
    fun `the showcase opens from the settings row without a preselected period`() = runTest {
        store.catalog = proCatalog()

        session.open(ProPoint.SETTINGS)

        val state = session.state.value
        assertTrue(state.visible)
        assertEquals(6, state.benefits.size)
        assertEquals(2, state.periods.size)
        assertEquals(null, state.selected)
        assertFalse(state.buyEnabled)
    }

    @Test
    fun `while the store thinks, the surface is already on its way`() = runTest {
        store.catalog = proCatalog()
        var openingWhileStoreThinks = false
        store.onOffers = { openingWhileStoreThinks = session.state.value.opening }

        session.open(ProPoint.SETTINGS)

        // Возврат в окне открытия принадлежит витрине, а не экрану под ней (TDD §8).
        assertTrue(openingWhileStoreThinks, "the opening flag must be set before the store answers")
        assertFalse(session.state.value.opening, "an open sheet is not 'opening' anymore")
        assertTrue(session.state.value.visible)
    }

    @Test
    fun `the yearly benefit is stated while it is true and is not stated when it is not`() = runTest {
        store.catalog = proCatalog(yearlyRubles = 990)
        session.open(ProPoint.SETTINGS)
        assertTrue(session.state.value.yearlyCheaper)

        store.catalog = proCatalog(yearlyRubles = 1990)
        session.open(ProPoint.SETTINGS)
        assertFalse(session.state.value.yearlyCheaper)
    }

    @Test
    fun `an unavailable store leaves the benefits without prices and the purchase closed`() = runTest {
        store.catalog = ProOffers.Unavailable

        session.open(ProPoint.SETTINGS)

        val state = session.state.value
        assertEquals(6, state.benefits.size)
        assertTrue(state.periods.isEmpty())
        assertFalse(state.buyEnabled)
        assertEquals(ProNotice.STORE_UNAVAILABLE, state.notice)
    }

    @Test
    fun `the store receives the identifier of the chosen product`() = runTest {
        store.catalog = proCatalog()
        store.purchaseOutcome = ProPurchase.Purchased(ProProductId.YEARLY, november)

        session.open(ProPoint.SETTINGS)
        session.select(ProProductId.YEARLY)
        session.buy()

        assertEquals(listOf(ProProductId.YEARLY), store.requestedProducts)
    }

    @Test
    fun `a store refusal keeps the showcase open and names the reason`() = runTest {
        store.catalog = proCatalog()
        store.purchaseOutcome = ProPurchase.Refused(ProStoreFailure.NETWORK)

        session.open(ProPoint.SETTINGS)
        session.select(ProProductId.MONTHLY)
        session.buy()

        val state = session.state.value
        assertTrue(state.visible)
        assertFalse(state.management)
        assertEquals(ProNotice.PURCHASE_FAILED_NETWORK, state.notice)
    }

    @Test
    fun `a declined payment is named differently from a missing connection`() = runTest {
        store.catalog = proCatalog()
        store.purchaseOutcome = ProPurchase.Refused(ProStoreFailure.PAYMENT_DECLINED)

        session.open(ProPoint.SETTINGS)
        session.select(ProProductId.MONTHLY)
        session.buy()

        assertEquals(ProNotice.PURCHASE_FAILED_DECLINED, session.state.value.notice)
    }

    @Test
    fun `the person's own cancellation is not commented at all`() = runTest {
        store.catalog = proCatalog()
        store.purchaseOutcome = ProPurchase.CancelledByPerson

        session.open(ProPoint.SETTINGS)
        session.select(ProProductId.MONTHLY)
        session.buy()

        val state = session.state.value
        assertTrue(state.visible)
        assertNull(state.notice)
        assertFalse(state.management)
    }

    @Test
    fun `a confirmed purchase opens pro until the date the store named`() = runTest {
        store.catalog = proCatalog()
        store.purchaseOutcome = ProPurchase.Purchased(ProProductId.MONTHLY, november)

        session.open(ProPoint.SETTINGS)
        session.select(ProProductId.MONTHLY)
        session.buy()

        val state = session.state.value
        assertTrue(state.management)
        assertEquals(ProSubscription.Active(ProProductId.MONTHLY, november), state.subscription)
        assertEquals(listOf<ProSubscription>(ProSubscription.Active(ProProductId.MONTHLY, november)), cache.writes)
        assertTrue(log.events().contains(OpEvent.PRO_PURCHASED))
    }

    @Test
    fun `an active subscription keeps access when the store is silent, without an alarming view`() = runTest {
        cache.write(ProSubscription.Active(ProProductId.YEARLY, november))
        store.catalog = ProOffers.Unavailable

        session.open(ProPoint.SETTINGS)

        val state = session.state.value
        assertTrue(state.management)
        assertNull(state.notice)
        assertNull(state.renewalPrice)
        assertTrue(state.disclosureRows.isEmpty())
    }

    @Test
    fun `an active subscription names the renewal price from the store`() = runTest {
        cache.write(ProSubscription.Active(ProProductId.YEARLY, november))
        store.catalog = proCatalog(yearlyRubles = 990)

        session.open(ProPoint.SETTINGS)

        assertEquals("990 ₽", session.state.value.renewalPrice?.text)
    }

    @Test
    fun `an expired subscription shows the showcase again without reproaches`() = runTest {
        cache.write(ProSubscription.Active(ProProductId.MONTHLY, LocalDate.of(2026, 10, 12)))
        store.catalog = proCatalog()

        session.open(ProPoint.SETTINGS)

        val state = session.state.value
        assertFalse(state.management)
        assertEquals(6, state.benefits.size)
        assertEquals(2, state.periods.size)
        assertNull(state.notice)
    }

    @Test
    fun `restoring a purchase returns access`() = runTest {
        store.catalog = proCatalog()
        store.restoreOutcome = ProRestore.Restored(ProProductId.YEARLY, november)

        session.open(ProPoint.SETTINGS)
        session.restore()

        assertTrue(session.state.value.management)
        assertEquals(listOf<ProSubscription>(ProSubscription.Active(ProProductId.YEARLY, november)), cache.writes)
        assertTrue(log.events().contains(OpEvent.PRO_RESTORED))
    }

    @Test
    fun `nothing found in the store is named in words`() = runTest {
        store.catalog = proCatalog()
        store.restoreOutcome = ProRestore.NothingFound

        session.open(ProPoint.SETTINGS)
        session.restore()

        assertEquals(ProNotice.RESTORE_NOTHING_FOUND, session.state.value.notice)
    }

    @Test
    fun `a subscription that exists but is not active is not called a missing purchase`() = runTest {
        store.catalog = proCatalog()
        store.restoreOutcome = ProRestore.NotActive

        session.open(ProPoint.SETTINGS)
        session.restore()

        // Слова случая — свои (`pro_restore_not_active`), а не «покупок не нашлось»:
        // магазин о подписке знает, и утверждать обратное нельзя (П-7, норма §5.9).
        assertEquals(ProNotice.RESTORE_NOT_ACTIVE, session.state.value.notice)
        assertTrue(log.events().contains(OpEvent.PRO_RESTORE_FAILED))
        assertTrue(log.records.any { it.detail == "not_active" })
    }

    @Test
    fun `a store page that did not open is not written into the journal as a visit`() = runTest {
        cache.write(ProSubscription.Active(ProProductId.MONTHLY, november))
        store.catalog = proCatalog()
        store.managementOpens = false

        session.open(ProPoint.SETTINGS)
        session.manage()

        // Не открылось — об этом сказано человеку, а в журнал небывшее не пишется (П-7).
        assertEquals(ProNotice.MANAGE_FAILED, session.state.value.notice)
        assertEquals(0, store.managementOpened)
        assertFalse(log.events().contains(OpEvent.PRO_MANAGEMENT_OPENED))
    }

    @Test
    fun `the disclosure names renewal and cancellation and expands in the same sheet`() = runTest {
        store.catalog = proCatalog()

        session.open(ProPoint.SETTINGS)
        // Период ещё не выбран: цену продления называть нечем, и слова идут без неё.
        assertEquals(
            listOf(ProNotice.RENEW_AUTO, ProNotice.RENEW_CANCEL),
            session.state.value.disclosureRows,
        )
        assertEquals(ProNotice.DISCLOSURE_MORE, session.state.value.disclosureMore)

        session.select(ProProductId.MONTHLY)
        assertEquals(
            listOf(ProNotice.RENEW_AUTO, ProNotice.RENEW_TERMS_SHORT),
            session.state.value.disclosureRows,
        )
        assertEquals(StorePrice("149 ₽", 149_000_000, "RUB"), session.state.value.disclosurePrice)

        session.expandDisclosure()

        assertEquals(4, session.state.value.disclosureRows.size)
        assertNull(session.state.value.disclosureMore)
    }

    @Test
    fun `without prices the disclosure keeps the words it can fill`() = runTest {
        store.catalog = ProOffers.Unavailable

        session.open(ProPoint.SETTINGS)
        // Выбора периода в этом состоянии нет вовсе, значит и цены продления нет.
        assertFalse(session.state.value.buyEnabled)

        val state = session.state.value
        assertEquals(listOf(ProNotice.RENEW_AUTO, ProNotice.RENEW_CANCEL), state.disclosureRows)
        assertNull(state.disclosurePrice)

        session.expandDisclosure()

        assertTrue(session.state.value.disclosureRows.contains(ProNotice.RENEW_CHARGE))
        assertFalse(session.state.value.disclosureRows.contains(ProNotice.RENEW_PRICE))
    }

    @Test
    fun `management leads to the store subscription page and cancels nothing by itself`() = runTest {
        cache.write(ProSubscription.Active(ProProductId.MONTHLY, november))
        store.catalog = proCatalog()

        session.open(ProPoint.SETTINGS)
        session.manage()

        assertEquals(1, store.managementOpened)
        assertTrue(session.state.value.management)
        assertTrue(log.events().contains(OpEvent.PRO_MANAGEMENT_OPENED))
    }
}
