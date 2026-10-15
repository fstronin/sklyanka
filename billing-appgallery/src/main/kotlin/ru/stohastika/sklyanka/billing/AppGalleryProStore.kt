package ru.stohastika.sklyanka.billing

import android.content.Context
import com.huawei.hmf.tasks.Task
import com.huawei.hms.iap.Iap
import com.huawei.hms.iap.IapClient
import com.huawei.hms.iap.entity.InAppPurchaseData
import com.huawei.hms.iap.entity.OrderStatusCode
import com.huawei.hms.iap.entity.OwnedPurchasesReq
import com.huawei.hms.iap.entity.ProductInfoReq
import com.huawei.hms.iap.entity.PurchaseIntentReq
import com.huawei.hms.iap.entity.StartIapActivityReq
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.stohastika.sklyanka.domain.pro.ProOffers
import ru.stohastika.sklyanka.domain.pro.ProOwned
import ru.stohastika.sklyanka.domain.pro.ProProduct
import ru.stohastika.sklyanka.domain.pro.ProProductId
import ru.stohastika.sklyanka.domain.pro.ProPurchase
import ru.stohastika.sklyanka.domain.pro.ProRestore
import ru.stohastika.sklyanka.domain.pro.ProStore
import ru.stohastika.sklyanka.domain.pro.StorePrice
import ru.stohastika.sklyanka.domain.time.TimeSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Магазин AppGallery за портом `ProStore` (SKL-122, `ADR-0010` п. 2, `ADR-0015`).
 *
 * Модуль знает вендора и не знает продукта: он переводит ответы магазина в исходы домена
 * и больше ничего. Все решения, которые можно проверить без магазина, вынесены
 * в [AppGalleryDecisions] и покрыты модульным тестом; здесь остаются сами вызовы,
 * и их полная проверка — песочница с тестовым Huawei ID (Р-49, Р-73 п. 5).
 *
 * **Успех не имитируется нигде** (П-7): каждый метод возвращает то, что ответил магазин,
 * а исключение клиента превращается в исход, а не в пустой ответ — иначе витрина показала бы
 * «нет цен» там, где на самом деле «нет магазина», и слова были бы другие.
 *
 * Вызовы идут в [Dispatchers.IO]: клиент магазина ходит в службу устройства, и держать это
 * в главном потоке нельзя — шторка обязана оставаться живой.
 */
@Singleton
class AppGalleryProStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activities: AppGalleryActivities,
    private val time: TimeSource,
) : ProStore {

    override suspend fun offers(): ProOffers = withContext(Dispatchers.IO) {
        val request = ProductInfoReq().apply {
            priceType = IapClient.PriceType.IN_APP_SUBSCRIPTION
            productIds = ProProductId.entries.map { it.storeId }
        }
        val result = attempt { client().obtainProductInfo(request) }.getOrNull()
            ?: return@withContext ProOffers.Unavailable
        if (result.returnCode != AppGalleryDecisions.SUCCESS) return@withContext ProOffers.Unavailable
        val products = result.productInfoList.orEmpty().mapNotNull { info ->
            val id = ProProductId.ofStoreId(info.productId) ?: return@mapNotNull null
            ProProduct(
                id = id,
                title = info.productName.orEmpty(),
                price = StorePrice(
                    text = info.price.orEmpty(),
                    amountMicros = info.microsPrice,
                    currencyCode = info.currency.orEmpty(),
                ),
            )
        }
        // Пустой каталог — это не «магазин недоступен»: товары есть, но их не отдали.
        // Для человека разницы нет (цен нет, покупать нечего), поэтому исход один.
        if (products.isEmpty()) ProOffers.Unavailable else ProOffers.Available(products)
    }

    override suspend fun owned(): ProOwned = withContext(Dispatchers.IO) {
        val result = attempt { client().obtainOwnedPurchases(subscriptionRequest()) }.getOrNull()
            ?: return@withContext ProOwned.Unavailable
        if (result.returnCode != AppGalleryDecisions.SUCCESS) return@withContext ProOwned.Unavailable
        AppGalleryDecisions.owned(records(result.inAppPurchaseDataList.orEmpty()), time.zone(), time.now())
    }

    override suspend fun purchase(productId: ProProductId): ProPurchase = withContext(Dispatchers.IO) {
        val request = PurchaseIntentReq().apply {
            setProductId(productId.storeId)
            setPriceType(IapClient.PriceType.IN_APP_SUBSCRIPTION)
        }
        val outcome = attempt { client().createPurchaseIntent(request) }.getOrElse { error ->
            // Истёк срок ожидания окна магазина — это тот же случай, что «успех без читаемой
            // записи»: покупка могла состояться, и отрицать её нельзя (П-7). Отказ магазина
            // называется словами, как и раньше.
            return@withContext if (error is AppGalleryDecisions.StoreTimeout) {
                AppGalleryDecisions.reconciled(owned(), productId)
            } else {
                ProPurchase.Refused(AppGalleryDecisions.failureOf(error))
            }
        }
        purchaseOutcome(outcome.returnCode, outcome.paymentData, productId)
    }

    override suspend fun restore(): ProRestore = withContext(Dispatchers.IO) {
        AppGalleryDecisions.restoreOf(owned())
    }

    override suspend fun openSubscriptionManagement(): Boolean = withContext(Dispatchers.IO) {
        val request = StartIapActivityReq().apply {
            type = StartIapActivityReq.TYPE_SUBSCRIBE_MANAGER_ACTIVITY
        }
        val result = attempt { client().startIapActivity(request) }.getOrNull() ?: return@withContext false
        // Экран подписок магазина — его экран, и открыть его может только активность.
        // Активности нет (приложение ушло в фон) — открывать нечем и некому.
        val activity = activities.resumed() ?: return@withContext false
        runCatching { result.startActivity(activity) }.isSuccess
    }

    private suspend fun purchaseOutcome(returnCode: Int, paymentData: String?, productId: ProProductId): ProPurchase =
        when {
            // Своя отмена окна покупки — молчание, а не сообщение о неудаче (Р-68 п. 6).
            returnCode == OrderStatusCode.ORDER_STATE_CANCEL -> ProPurchase.CancelledByPerson
            returnCode != AppGalleryDecisions.SUCCESS -> ProPurchase.Refused(
                AppGalleryDecisions.failureOfCode(returnCode),
            )
            else -> confirmed(paymentData, productId)
        }

    /**
     * Подтверждение покупки.
     *
     * Дату берём из ответа магазина, а не считаем сами: срок подписки — его данные, и наша
     * арифметика «месяц от сегодня» разошлась бы с ним в первый же день продления.
     * Ответ без даты переспрашивает магазин, а не отрицает покупку: см. [reconciled].
     */
    private suspend fun confirmed(paymentData: String?, productId: ProProductId): ProPurchase {
        val data = paymentData?.takeIf { it.isNotBlank() }
            ?.let { text -> runCatching { InAppPurchaseData(text) }.getOrNull() }
        val expiration = data?.expirationDate ?: 0L
        return if (data == null || expiration <= 0L) {
            AppGalleryDecisions.reconciled(owned(), productId)
        } else {
            ProPurchase.Purchased(
                productId = ProProductId.ofStoreId(data.productId) ?: productId,
                until = AppGalleryDecisions.until(expiration, time.zone()),
            )
        }
    }

    private fun subscriptionRequest(): OwnedPurchasesReq = OwnedPurchasesReq().apply {
        priceType = IapClient.PriceType.IN_APP_SUBSCRIPTION
    }

    private fun records(serialized: List<String>): List<AppGalleryDecisions.SubscriptionRecord> =
        serialized.mapNotNull { entry -> runCatching { InAppPurchaseData(entry) }.getOrNull() }
            .map { data ->
                AppGalleryDecisions.SubscriptionRecord(
                    productId = data.productId.orEmpty(),
                    expirationMillis = data.expirationDate,
                    purchaseState = data.purchaseState,
                )
            }

    /** Активность, которой можно показать окно магазина; фон уходит на контекст приложения. */
    private fun client(): IapClient {
        val activity = activities.resumed()
        return if (activity != null) Iap.getIapClient(activity) else Iap.getIapClient(context)
    }

    /**
     * Ответ магазина как исход: `failure` — обращение не состоялось.
     *
     * Вендорский клиент бросает и проверяемые, и непроверяемые отказы сразу на двух уровнях
     * (задача и служба), и перечислить их типами нельзя: любой отказ здесь — исход поверхности,
     * а не сбой. Поэтому ловится исключение вообще, но **не теряется**: оно уезжает в отказ,
     * и по нему читается причина (`failureOf`).
     */
    private suspend fun <T> attempt(call: () -> Task<T>): Result<T> = try {
        val outcome = withTimeoutOrNull(AppGalleryDecisions.STORE_TIMEOUT_MS) { awaitStore(call()) }
        if (outcome == null) Result.failure(AppGalleryDecisions.StoreTimeout()) else Result.success(outcome)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
        Result.failure(error)
    }

    /**
     * Ответ магазина, не занимающий поток.
     *
     * Вместо блокирующего `Tasks.await` — ожидание на продолжении: поток возвращается
     * диспетчеру, а срок ожидания обрывает само ожидание, а не только его результат
     * (заблокированный поток `withTimeout` не прервал бы).
     */
    private suspend fun <T> awaitStore(task: Task<T>): T = suspendCancellableCoroutine { continuation ->
        task.addOnCompleteListener { done ->
            if (!continuation.isActive) return@addOnCompleteListener
            val error = done.exception
            if (error == null) continuation.resume(done.result) else continuation.resumeWithException(error)
        }
    }
}
