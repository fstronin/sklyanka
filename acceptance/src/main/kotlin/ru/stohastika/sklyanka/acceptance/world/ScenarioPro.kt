package ru.stohastika.sklyanka.acceptance.world

import kotlinx.coroutines.runBlocking
import ru.stohastika.sklyanka.domain.pro.ObserveProStatusUseCase
import ru.stohastika.sklyanka.domain.pro.ProOffers
import ru.stohastika.sklyanka.domain.pro.ProOwned
import ru.stohastika.sklyanka.domain.pro.ProProduct
import ru.stohastika.sklyanka.domain.pro.ProProductId
import ru.stohastika.sklyanka.domain.pro.ProPurchase
import ru.stohastika.sklyanka.domain.pro.ProRestore
import ru.stohastika.sklyanka.domain.pro.ProSheetSession
import ru.stohastika.sklyanka.domain.pro.ProSheetState
import ru.stohastika.sklyanka.domain.pro.ProStatus
import ru.stohastika.sklyanka.domain.pro.ProStore
import ru.stohastika.sklyanka.domain.pro.ProStoreFailure
import ru.stohastika.sklyanka.domain.pro.ProSubscription
import ru.stohastika.sklyanka.domain.pro.ProSubscriptionCache
import ru.stohastika.sklyanka.domain.pro.StorePrice
import java.time.LocalDate

/**
 * Магазин «Склянка Про» в мире сценария (SKL-122).
 *
 * Здесь живут только **порты** платформы — магазин и кэш состояния. Правила не копируются:
 * переходы шторки, выбор активной подписки, проверка при запуске и запись ответа магазина
 * в кэш исполняют настоящие `ProSheetSession`, `RefreshProSubscriptionUseCase`
 * и `ProSubscriptionCache`. Это то же разделение, что у переноса данных: сценарий подставляет
 * магазин, а не пересказывает продукт (`docs/tasks/SKL-122/TDD.md` §2).
 *
 * **По умолчанию магазин ничего не подтверждает.** Сценарий, который говорит только
 * «человек нажал Оформить и в магазин ушёл товар», не утверждает, что человек заплатил:
 * покупка подтверждается отдельным словом предыстории. Иначе «до ответа магазина „Про“
 * не открывается» проверялось бы ответом, которого сценарий не называл.
 */
class ScenarioPro(private val clock: ScenarioClock, log: ScenarioLog) {

    private val store = Marketplace()
    private val cache = Cached()

    /** Шторка витрины — тот же объект, что у экрана: правила настоящие. */
    val session: ProSheetSession = ProSheetSession(store = store, cache = cache, log = log, time = clock.time)

    /** Состояние поверхности: то, что видит человек. */
    val state: ProSheetState get() = session.state.value

    /** Что магазин получил: идентификаторы товаров по порядку обращений. */
    val requestedProducts: List<ProProductId> get() = store.requested

    /** Сколько раз человек уходил в раздел подписок магазина. */
    val managementOpened: Int get() = store.managementOpened

    /** Сколько раз магазин спрашивали о владении подпиской (проверка при запуске). */
    val ownershipChecks: Int get() = store.ownershipChecks

    // --- Предыстория: что продаётся -------------------------------------------------------

    /**
     * Магазин продаёт месяц за [monthlyRubles] и год за [yearlyRubles] рублей.
     *
     * Имена товаров называет сценарий: они приходят из консоли магазина
     * (`docs/tasks/SKL-122/console.md`), и приёмка говорит теми же словами, что увидит человек.
     */
    fun sells(
        monthlyRubles: Long,
        yearlyRubles: Long,
        monthlyName: String = MONTHLY_NAME,
        yearlyName: String = YEARLY_NAME,
    ) {
        store.catalog = ProOffers.Available(
            listOf(
                product(ProProductId.MONTHLY, monthlyName, monthlyRubles),
                product(ProProductId.YEARLY, yearlyName, yearlyRubles),
            ),
        )
    }

    /** Магазин не отвечает: цен нет, покупка недоступна (Р-68 п. 4). */
    fun silent() {
        store.catalog = ProOffers.Unavailable
    }

    // --- Предыстория: состояние подписки --------------------------------------------------

    /** Подписка активна до [until]: доступ есть, состояние «активна». */
    fun owned(period: ProProductId?, until: LocalDate) {
        runBlocking { cache.write(ProSubscription.Active(period, until)) }
    }

    /** Подписки нет: не оформлена (то же состояние, что у истёкшей). */
    /** Экран подписок магазина на этом устройстве не открывается (нет служб, ушла активность). */
    fun managementDoesNotOpen() {
        store.managementOpens = false
    }

    fun none() {
        runBlocking { cache.write(ProSubscription.None) }
    }

    /** Устройство забыло состояние после переустановки: кэша нет вовсе. */
    fun forget() {
        cache.clear()
    }

    // --- Предыстория: исходы обращения к магазину -----------------------------------------

    fun refusesPurchase(failure: ProStoreFailure) {
        store.purchaseOutcome = ProPurchase.Refused(failure)
    }

    /** Магазин подтверждает покупку периода [period] до [until]. */
    fun confirmsPurchase(period: ProProductId, until: LocalDate) {
        store.purchaseOutcome = ProPurchase.Purchased(period, until)
    }

    /** Человек закрыл окно магазина, не заплатив (Р-68 п. 6). */
    fun personClosedTheWindow() {
        store.purchaseOutcome = ProPurchase.CancelledByPerson
    }

    fun restoreFinds(period: ProProductId?, until: LocalDate) {
        store.restoreOutcome = ProRestore.Restored(period, until)
    }

    fun restoreFindsNothing() {
        store.restoreOutcome = ProRestore.NothingFound
    }

    /**
     * Записи в магазине есть, а действующей среди них нет (истекла, возвращена, отменена).
     *
     * Отличается от [restoreFindsNothing] намеренно: «покупок не нашлось» и «подписка есть,
     * но не действует» — разные новости, и слова у них разные (П-7).
     */
    fun restoreFindsRecordsThatAreNotActive() {
        store.restoreOutcome = ProRestore.NotActive
    }

    /**
     * Магазин подтвердил покупку, а живой записи о ней не отдал.
     *
     * Так выглядит исход переспроса `AppGalleryDecisions.reconciled`: подтверждать нечем,
     * но и отказом это не называется (П-7).
     */
    fun confirmsPurchaseWithoutARecord() {
        store.purchaseOutcome = ProPurchase.Refused(ProStoreFailure.CONFIRMED_UNREADABLE)
    }

    fun restoreFails(failure: ProStoreFailure) {
        store.restoreOutcome = ProRestore.Failed(failure)
    }

    /** Магазин подтверждает владение при проверке при запуске. */
    fun ownsOnCheck(period: ProProductId?, until: LocalDate) {
        store.ownership = ProOwned.Owned(period, until)
    }

    fun ownsNothingOnCheck() {
        store.ownership = ProOwned.None
    }

    /**
     * Чтение состояния подписки тем же сценарием, что у экрана настроек (Р-67 п. 6).
     *
     * Открыто наружу, а не спрятано в [status]: стенд настроек собирает настоящую модель
     * экрана и обязан получить тот же сценарий, каким живёт продукт.
     */
    val statusUseCase: ObserveProStatusUseCase = ObserveProStatusUseCase(cache, clock.time)

    /** Состояние подписки так, как его читает строка настроек и гейты состава. */
    fun status(): ProStatus = runBlocking { statusUseCase() }

    private fun product(id: ProProductId, title: String, rubles: Long): ProProduct = ProProduct(
        id = id,
        title = title,
        // Строка цены — та, что называет магазин; рублей на экран не попадает ни одного
        // числа из кода (Р-68 п. 3).
        price = StorePrice(text = "$rubles ₽", amountMicros = rubles * MICROS_IN_RUBLE, currencyCode = "RUB"),
    )

    /** Магазин в памяти: отвечает тем, чем его настроил сценарий, и помнит, что у него спросили. */
    private class Marketplace : ProStore {

        var catalog: ProOffers = ProOffers.Unavailable

        var ownership: ProOwned = ProOwned.None

        var purchaseOutcome: ProPurchase = ProPurchase.CancelledByPerson

        var restoreOutcome: ProRestore = ProRestore.NothingFound

        val requested: MutableList<ProProductId> = mutableListOf()

        /** Открылся ли экран подписок: на устройстве без магазина он не открывается. */
        var managementOpens: Boolean = true

        var managementOpened: Int = 0
            private set

        var ownershipChecks: Int = 0
            private set

        override suspend fun offers(): ProOffers = catalog

        override suspend fun owned(): ProOwned {
            ownershipChecks++
            return ownership
        }

        override suspend fun purchase(productId: ProProductId): ProPurchase {
            requested += productId
            return purchaseOutcome
        }

        override suspend fun restore(): ProRestore = restoreOutcome

        override suspend fun openSubscriptionManagement(): Boolean {
            if (!managementOpens) return false
            managementOpened++
            return true
        }
    }

    /** Кэш состояния в памяти: помнит последний ответ магазина и всё, что в него записали. */
    private class Cached : ProSubscriptionCache {

        private var stored: ProSubscription? = null

        val writes: MutableList<ProSubscription> = mutableListOf()

        override suspend fun read(): ProSubscription? = stored

        override suspend fun write(subscription: ProSubscription) {
            stored = subscription
            writes += subscription
        }

        fun clear() {
            stored = null
        }
    }

    private companion object {
        const val MICROS_IN_RUBLE = 1_000_000L

        /** Имена товаров из консоли AppGallery: сценарий, который их не называет, берёт эти. */
        const val MONTHLY_NAME = "Склянка Про · месяц"

        const val YEARLY_NAME = "Склянка Про · год"
    }
}
