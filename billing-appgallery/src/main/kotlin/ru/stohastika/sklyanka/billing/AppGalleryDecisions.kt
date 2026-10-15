package ru.stohastika.sklyanka.billing

import com.huawei.hms.common.ApiException
import com.huawei.hms.iap.entity.OrderStatusCode
import ru.stohastika.sklyanka.domain.pro.ProOwned
import ru.stohastika.sklyanka.domain.pro.ProProductId
import ru.stohastika.sklyanka.domain.pro.ProPurchase
import ru.stohastika.sklyanka.domain.pro.ProRestore
import ru.stohastika.sklyanka.domain.pro.ProStoreFailure
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Решения контура, которые проверяются без магазина (SKL-122).
 *
 * Разделение простое и намеренное: вендорские вызовы живут в [AppGalleryProStore] и требуют
 * устройства, а решения — выбор активной подписки из ответа магазина, перевод момента
 * в дату, чтение кодов — чистые функции, и они проверяются обычным модульным тестом.
 * Иначе единственной проверкой контура осталась бы песочница, то есть ручная (Р-73 п. 5).
 *
 * Коды берутся из самого SDK, а не из чисел, записанных по памяти: `OrderStatusCode`
 * и `ApiException.statusCode` приезжают вместе с клиентом магазина.
 */
object AppGalleryDecisions {

    /** Код успеха в ответах магазина (`OrderStatusCode.ORDER_STATE_SUCCESS`). */
    const val SUCCESS: Int = OrderStatusCode.ORDER_STATE_SUCCESS

    /**
     * Отказ магазина по коду ответа.
     *
     * Различаются только те случаи, у которых разные слова для человека: отмена — молчание,
     * нет связи — «попробуйте позже», снятый с продажи товар — своё. Всё прочее называется
     * ошибкой магазина честно, без догадок о причине (П-7).
     */
    fun failureOfCode(returnCode: Int): ProStoreFailure = when (returnCode) {
        OrderStatusCode.ORDER_STATE_NET_ERROR -> ProStoreFailure.NETWORK
        OrderStatusCode.ORDER_STATE_PRODUCT_INVALID,
        OrderStatusCode.ORDER_STATE_PRODUCT_COUNTRY_NOT_SUPPORTED,
        -> ProStoreFailure.PRODUCT_UNAVAILABLE

        else -> ProStoreFailure.STORE_ERROR
    }

    /**
     * Отказ обращения к магазину, закончившегося исключением.
     *
     * `907135003` — клиент магазина недействителен: службы магазина на устройстве нет или она
     * слишком старая. Это не «нет связи»: ждать нечего, покупка на этом устройстве
     * не состоится, и слова для человека другие.
     */
    fun failureOf(error: Throwable): ProStoreFailure = when {
        // Магазин не ответил за срок — это «нет связи, попробуйте позже», а не отказ
        // и не пустой ответ: слова у них разные (Р-68 п. 6).
        error is StoreTimeout -> ProStoreFailure.NETWORK
        (error as? ApiException)?.statusCode == CLIENT_API_INVALID -> ProStoreFailure.STORE_UNAVAILABLE
        else -> ProStoreFailure.STORE_ERROR
    }

    /** Магазин не ответил за [STORE_TIMEOUT_MS]: обращение не состоялось, а не отказало. */
    class StoreTimeout : Exception("the store did not answer in time")

    /**
     * Магазин сказал «успех», а записи в его ответе не было.
     *
     * Отказом это называть нельзя: деньги могли быть списаны, а подписка — живой, и слова
     * «покупка не прошла» были бы ложью в ту сторону, где человек платит дважды (П-7).
     * Поэтому покупка не отрицается, а подтверждается тем, что магазин отдаёт о владении:
     * живая запись — покупка состоялась. Оставшийся случай (подтвердил, живой записи нет)
     * ждёт своего исхода и своих слов от дизайнера — долг назван в `TDD.md` §13.
     */
    fun reconciled(owned: ProOwned, productId: ProProductId): ProPurchase = when (owned) {
        is ProOwned.Owned -> ProPurchase.Purchased(owned.productId ?: productId, owned.until)
        else -> ProPurchase.Refused(ProStoreFailure.CONFIRMED_UNREADABLE)
    }

    /** Исход восстановления по ответу о владении. */
    fun restoreOf(owned: ProOwned): ProRestore = when (owned) {
        is ProOwned.Owned -> ProRestore.Restored(owned.productId, owned.until)
        ProOwned.None -> ProRestore.NothingFound
        ProOwned.NotActive -> ProRestore.NotActive
        ProOwned.Unavailable -> ProRestore.Failed(ProStoreFailure.STORE_UNAVAILABLE)
    }

    /**
     * Запись подписки в том виде, в каком её отдаёт магазин после разбора его строки.
     *
     * Модель нужна, чтобы выбор активной подписки проверялся тестом: правило «берём самую
     * позднюю действующую» — правило продукта (доступ один, периодов два), а не деталь
     * вендорского разбора.
     */
    data class SubscriptionRecord(val productId: String, val expirationMillis: Long, val purchaseState: Int)

    /** Состояние «оплачено» в записи магазина (`InAppPurchaseData.getPurchaseState`). */
    const val PURCHASE_STATE_PURCHASED: Int = 0

    /**
     * Сколько ждём магазин, прежде чем считать, что он не ответил.
     *
     * Клиент магазина ходит в службу устройства, а та — в сеть: без срока витрина открывалась бы
     * всё время, пока служба думает, и возврат в окне открытия уводил бы на чужой экран
     * (`docs/tasks/SKL-122/TDD.md` §8). Десять секунд — заметно больше обычного ответа службы
     * и заметно меньше терпения человека перед кнопкой «Оформить».
     */
    const val STORE_TIMEOUT_MS: Long = 10_000L

    /**
     * Владение подпиской по записям магазина.
     *
     * Действующими считаются только оплаченные записи, чей срок не истёк **по дате**;
     * из них берётся самая поздняя. Просроченная запись не даёт доступа — иначе подписка
     * была бы вечной, а продукт обещает только офлайн-доступ к оплаченному (П-6, Р-68 п. 7).
     *
     * Сравнение идёт датами, а не моментами: магазин называет срок моментом (миллисекунды),
     * а человеку обещан день — «активна до 13 ноября» значит весь день 13 ноября
     * (`ProSubscription.activeIfNotExpired`, `docs/tasks/SKL-122/TDD.md` §7). Фильтр
     * по моменту отнял бы последний день подписки за часы до названной даты.
     */
    fun owned(records: List<SubscriptionRecord>, zone: ZoneId, now: Instant): ProOwned {
        val today = now.atZone(zone).toLocalDate()
        val latest = records
            .filter { record -> isLive(record, today, zone) }
            .maxByOrNull { it.expirationMillis }
        if (latest != null) {
            return ProOwned.Owned(
                productId = ProProductId.ofStoreId(latest.productId),
                until = until(latest.expirationMillis, zone),
            )
        }
        // Пустой ответ и ответ с недействующими записями — разные новости: во втором случае
        // магазин о подписке знает (истекла, возвращена, отменена), и говорить «покупок
        // не нашлось» значило бы утверждать о магазине то, чего он не говорил (П-7).
        return if (records.isEmpty()) ProOwned.None else ProOwned.NotActive
    }

    /** Оплачена ли запись и не истёк ли её срок **по дате** дня человека. */
    private fun isLive(record: SubscriptionRecord, today: LocalDate, zone: ZoneId): Boolean =
        record.purchaseState == PURCHASE_STATE_PURCHASED && !until(record.expirationMillis, zone).isBefore(today)

    /**
     * Момент магазина — в дату пояса человека.
     *
     * Магазин называет срок моментом (миллисекунды UTC), а продукт показывает дату и живёт
     * датами (НФТ-43а, `TDD.md §7`): «активна до 13 ноября» — это день в поясе человека,
     * и считать его надо там же, где считаются сутки приёма.
     */
    fun until(expirationMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(expirationMillis).atZone(zone).toLocalDate()

    /**
     * Код «клиент магазина недействителен» (907135003).
     *
     * По документации вендора — недействительный объект клиента, по факту в отчётах
     * разработчиков — отсутствующие или слишком старые службы магазина на устройстве.
     * Число записано здесь явно и с этим пояснением: в самом SDK константы нет, а ссылаться
     * на код без объяснения — значит оставить следующему читателю загадку.
     */
    const val CLIENT_API_INVALID: Int = 907135003
}
