package ru.stohastika.sklyanka.billing

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.stohastika.sklyanka.domain.pro.ProOwned
import ru.stohastika.sklyanka.domain.pro.ProProductId
import ru.stohastika.sklyanka.domain.pro.ProPurchase
import ru.stohastika.sklyanka.domain.pro.ProRestore
import ru.stohastika.sklyanka.domain.pro.ProStoreFailure
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Решения контура без магазина (SKL-122).
 *
 * Проверяется то, что иначе проверялось бы только в песочнице руками: выбор активной
 * подписки из ответа магазина, перевод момента магазина в дату пояса человека и чтение
 * кодов отказа. Имена кодов берутся из самого SDK, поэтому тест ловит и подмену смысла
 * кода, а не только числа.
 */
class AppGalleryDecisionsTest {

    private val moscow = ZoneId.of("Europe/Moscow")

    private val november13 = Instant.parse("2026-11-13T00:00:00Z").toEpochMilli()

    @Test
    fun `a cancelled purchase is its own outcome and not a refusal`() {
        assertEquals(ProStoreFailure.NETWORK, AppGalleryDecisions.failureOfCode(60005))
        assertEquals(ProStoreFailure.PRODUCT_UNAVAILABLE, AppGalleryDecisions.failureOfCode(60003))
        assertEquals(ProStoreFailure.PRODUCT_UNAVAILABLE, AppGalleryDecisions.failureOfCode(60007))
        assertEquals(ProStoreFailure.STORE_ERROR, AppGalleryDecisions.failureOfCode(60002))
    }

    @Test
    fun `a missing or too old store client is not a missing connection`() {
        val missingStore = com.huawei.hms.common.ApiException(
            com.huawei.hms.support.api.client.Status(AppGalleryDecisions.CLIENT_API_INVALID),
        )

        assertEquals(ProStoreFailure.STORE_UNAVAILABLE, AppGalleryDecisions.failureOf(missingStore))
    }

    @Test
    fun `any other exception is an error of the store`() {
        assertEquals(ProStoreFailure.STORE_ERROR, AppGalleryDecisions.failureOf(IllegalStateException("boom")))
    }

    @Test
    fun `the latest paid and running record wins`() {
        val records = listOf(
            record(productId = "sklyanka.pro.monthly", expirationMillis = october13),
            record(productId = "sklyanka.pro.yearly", expirationMillis = november13),
        )

        val owned = AppGalleryDecisions.owned(records, moscow, now())

        assertEquals(ProOwned.Owned(ProProductId.YEARLY, LocalDate.of(2026, 11, 13)), owned)
    }

    @Test
    fun `the last day of the subscription is still access`() {
        // Срок кончился утром 13 ноября, а обещан день: «активна до 13 ноября» — весь день.
        val morningOfNovember13 = Instant.parse("2026-11-13T06:00:00Z").toEpochMilli()
        val records = listOf(record(productId = "sklyanka.pro.monthly", expirationMillis = morningOfNovember13))

        assertEquals(
            ProOwned.Owned(ProProductId.MONTHLY, LocalDate.of(2026, 11, 13)),
            AppGalleryDecisions.owned(records, moscow, now()),
        )
    }

    @Test
    fun `a record whose day has passed gives no access`() {
        // Срок кончился вчера по датам человека: день 12 октября прошёл, доступа нет.
        val yesterday = Instant.parse("2026-10-12T00:00:00Z").toEpochMilli()
        val records = listOf(record(productId = "sklyanka.pro.monthly", expirationMillis = yesterday))

        // Магазин о записи знает, действующей она не является: это не «покупок не нашлось».
        assertEquals(ProOwned.NotActive, AppGalleryDecisions.owned(records, moscow, now()))
    }

    @Test
    fun `a not paid record gives no access`() {
        val records = listOf(
            record(productId = "sklyanka.pro.monthly", expirationMillis = november13, purchaseState = 1),
        )

        assertEquals(ProOwned.NotActive, AppGalleryDecisions.owned(records, moscow, now()))
    }

    @Test
    fun `a product the product does not know keeps the access without the price`() {
        val records = listOf(record(productId = "sklyanka.pro.lifetime", expirationMillis = november13))

        assertEquals(
            ProOwned.Owned(null, LocalDate.of(2026, 11, 13)),
            AppGalleryDecisions.owned(records, moscow, now()),
        )
    }

    @Test
    fun `the store moment becomes a date of the person's zone`() {
        // 13 ноября 22:30 UTC — в Москве уже 14 ноября: срок показывается днём человека.
        val lateNovember13 = Instant.parse("2026-11-13T22:30:00Z").toEpochMilli()

        assertEquals(LocalDate.of(2026, 11, 14), AppGalleryDecisions.until(lateNovember13, moscow))
    }

    @Test
    fun `restoring with nothing found is named, and an unanswerable check is a failure`() {
        assertEquals(ProRestore.NothingFound, AppGalleryDecisions.restoreOf(ProOwned.None))
        assertEquals(ProRestore.NotActive, AppGalleryDecisions.restoreOf(ProOwned.NotActive))
        assertEquals(
            ProRestore.Failed(ProStoreFailure.STORE_UNAVAILABLE),
            AppGalleryDecisions.restoreOf(ProOwned.Unavailable),
        )
        assertEquals(
            ProRestore.Restored(ProProductId.YEARLY, LocalDate.of(2026, 11, 13)),
            AppGalleryDecisions.restoreOf(ProOwned.Owned(ProProductId.YEARLY, LocalDate.of(2026, 11, 13))),
        )
    }

    @Test
    fun `a store that did not answer in time is a missing connection, not a refusal`() {
        assertEquals(
            ProStoreFailure.NETWORK,
            AppGalleryDecisions.failureOf(AppGalleryDecisions.StoreTimeout()),
        )
    }

    @Test
    fun `a success with an unreadable record is confirmed by what the store says about ownership`() {
        assertEquals(
            ProPurchase.Purchased(ProProductId.YEARLY, LocalDate.of(2026, 11, 13)),
            AppGalleryDecisions.reconciled(
                ProOwned.Owned(ProProductId.YEARLY, LocalDate.of(2026, 11, 13)),
                ProProductId.MONTHLY,
            ),
        )
        assertEquals(
            ProPurchase.Purchased(ProProductId.MONTHLY, LocalDate.of(2026, 11, 13)),
            AppGalleryDecisions.reconciled(
                ProOwned.Owned(null, LocalDate.of(2026, 11, 13)),
                ProProductId.MONTHLY,
            ),
        )
    }

    @Test
    fun `a success with an unreadable record is never denied when there is nothing to confirm it with`() {
        assertEquals(
            ProPurchase.Refused(ProStoreFailure.CONFIRMED_UNREADABLE),
            AppGalleryDecisions.reconciled(ProOwned.None, ProProductId.MONTHLY),
        )
        assertEquals(
            ProPurchase.Refused(ProStoreFailure.CONFIRMED_UNREADABLE),
            AppGalleryDecisions.reconciled(ProOwned.Unavailable, ProProductId.MONTHLY),
        )
    }

    @Test
    fun `an answer with records that are not active is named apart from an empty answer`() {
        val yesterday = Instant.parse("2026-10-12T00:00:00Z").toEpochMilli()
        val refunded = Instant.parse("2026-11-13T00:00:00Z").toEpochMilli()

        assertEquals(ProOwned.None, AppGalleryDecisions.owned(emptyList(), moscow, now()))
        assertEquals(
            ProOwned.NotActive,
            AppGalleryDecisions.owned(
                listOf(
                    record(productId = "sklyanka.pro.monthly", expirationMillis = yesterday),
                    record(productId = "sklyanka.pro.yearly", expirationMillis = refunded, purchaseState = 1),
                ),
                moscow,
                now(),
            ),
        )
    }

    private fun now(): Instant = Instant.parse("2026-10-13T09:00:00Z")

    private val october13 = Instant.parse("2026-10-13T00:00:00Z").toEpochMilli()

    private fun record(
        productId: String,
        expirationMillis: Long,
        purchaseState: Int = AppGalleryDecisions.PURCHASE_STATE_PURCHASED,
    ) = AppGalleryDecisions.SubscriptionRecord(
        productId = productId,
        expirationMillis = expirationMillis,
        purchaseState = purchaseState,
    )
}
