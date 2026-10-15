package ru.stohastika.sklyanka.domain.pro

/**
 * Подстановки для тестов платного контура: магазин и кэш в памяти.
 *
 * Магазин отвечает тем, чем его настроил тест, и запоминает, что у него спросили:
 * «в магазин ушёл идентификатор выбранного товара» — утверждение о запросе, а не
 * о результате, и без записи запросов его не проверить.
 */
class FakeProStore : ProStore {

    var catalog: ProOffers = ProOffers.Unavailable

    var ownership: ProOwned = ProOwned.None

    var purchaseOutcome: ProPurchase = ProPurchase.CancelledByPerson

    var restoreOutcome: ProRestore = ProRestore.NothingFound

    val requestedProducts: MutableList<ProProductId> = mutableListOf()

    /** Взгляд в состояние поверхности в момент обращения: им проверяется признак открытия. */
    var onOffers: (() -> Unit)? = null

    /** Открылся ли экран подписок: на устройстве без магазина он не открывается. */
    var managementOpens: Boolean = true

    var managementOpened: Int = 0
        private set

    override suspend fun offers(): ProOffers {
        onOffers?.invoke()
        return catalog
    }

    override suspend fun owned(): ProOwned = ownership

    override suspend fun purchase(productId: ProProductId): ProPurchase {
        requestedProducts += productId
        return purchaseOutcome
    }

    override suspend fun restore(): ProRestore = restoreOutcome

    override suspend fun openSubscriptionManagement(): Boolean {
        if (!managementOpens) return false
        managementOpened++
        return true
    }
}

/** Кэш состояния подписки в памяти; хранит всё, что в него записали. */
class InMemoryProSubscriptionCache(private var stored: ProSubscription? = null) : ProSubscriptionCache {

    val writes: MutableList<ProSubscription> = mutableListOf()

    override suspend fun read(): ProSubscription? = stored

    override suspend fun write(subscription: ProSubscription) {
        stored = subscription
        writes += subscription
    }
}

/** Каталог магазина: месяц за 149 ₽ и год за [yearlyRubles] ₽ — цена года меняется тестом. */
fun proCatalog(yearlyRubles: Long = 990): ProOffers = ProOffers.Available(
    listOf(
        ProProduct(
            id = ProProductId.MONTHLY,
            title = "Sklyanka Pro · monthly",
            price = StorePrice(text = "149 ₽", amountMicros = 149_000_000, currencyCode = "RUB"),
        ),
        ProProduct(
            id = ProProductId.YEARLY,
            title = "Sklyanka Pro · yearly",
            price = StorePrice(text = "$yearlyRubles ₽", amountMicros = yearlyRubles * 1_000_000, currencyCode = "RUB"),
        ),
    ),
)
