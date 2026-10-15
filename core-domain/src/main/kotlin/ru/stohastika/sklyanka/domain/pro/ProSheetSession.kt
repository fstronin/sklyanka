package ru.stohastika.sklyanka.domain.pro

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.time.TimeSource

/**
 * Шторка витрины: единственное место, где живут её переходы (Р-67, Р-68, Р-73).
 *
 * Здесь, а не в `ViewModel`, по двум причинам. Первая: приёмка SKL-122 исполняется
 * без Android (`docs/tasks/SKL-122/TDD.md §2`), и её шаги обязаны гонять настоящие правила,
 * а не их копию в сценарном мире. Вторая: правила шторки — продуктовые («кнопка ждёт выбора»,
 * «своя отмена не комментируется», «недоступный магазин не отнимает доступ»), и держать их
 * в модуле экрана значит проверять их только на устройстве.
 *
 * Сети внутри нет: всё общение с магазином идёт через [ProStore], кэш — через
 * [ProSubscriptionCache]. Отказ магазина не бросает исключение наружу: исход состояния —
 * это часть состояния поверхности, а не сбой.
 */
class ProSheetSession(
    private val store: ProStore,
    private val cache: ProSubscriptionCache,
    private val log: OperationLog,
    private val time: TimeSource,
) {

    private val mutableState = MutableStateFlow(ProSheetState.closed)

    /** Состояние поверхности для экрана; меняется только методами этой сессии. */
    val state: StateFlow<ProSheetState> = mutableState.asStateFlow()

    /**
     * Открыть витрину из точки выбора платной функции.
     *
     * Цены спрашиваются у магазина при каждом открытии: кэшированной цены у продукта нет,
     * а показать вчерашнюю как текущую запрещено (Р-68 п. 4). Доступ берётся из кэша —
     * открытие витрины не ходит в сеть за подпиской.
     */
    suspend fun open(point: ProPoint) {
        // Признак открытия встаёт до обращения к магазину: пока он думает, поверхность уже
        // «идёт» — возврат принадлежит ей, и окно открытия не уводит на чужой экран.
        mutableState.value = ProSheetState(opening = true, point = point)
        val subscription = cachedSubscription()
        val offers = store.offers()
        mutableState.value = ProSheetState(
            visible = true,
            point = point,
            subscription = subscription,
            offers = offers,
            selected = null,
            busy = false,
            expanded = false,
            notice = unavailableNotice(subscription, offers),
        )
    }

    /** Закрыть шторку: выход один — системный «Назад» и тап по затемнению (Р-54). */
    fun dismiss() {
        mutableState.value = ProSheetState.closed
    }

    /** Выбрать период: цена в карточке уже названа, вслепую выбрать нечего (Р-75 п. 1). */
    fun select(period: ProProductId) {
        val current = mutableState.value
        if (!current.visible || current.storeUnavailable || current.management) return
        mutableState.value = current.copy(selected = period, notice = null)
    }

    /** Развернуть обязательства магазина в той же шторке (Р-76 п. 3). */
    fun expandDisclosure() {
        val current = mutableState.value
        if (!current.visible || current.management) return
        mutableState.value = current.copy(expanded = true)
    }

    /**
     * Покупка выбранного периода.
     *
     * Три исхода — три разных поведения: подтверждение магазина открывает «Про», отказ
     * называется словами над кнопкой и оставляет шторку открытой, своя отмена окна покупки
     * не комментируется вовсе (Р-68 п. 6, П-7).
     */
    suspend fun buy() {
        val current = mutableState.value
        val period = current.selected ?: return
        if (!current.buyEnabled) return

        mutableState.value = current.copy(busy = true, notice = null)
        when (val outcome = store.purchase(period)) {
            is ProPurchase.Purchased -> {
                val subscription = ProSubscription.activeIfNotExpired(outcome.productId, outcome.until, time.today())
                cache.write(subscription)
                log.record(OpEvent.PRO_PURCHASED, detail = outcome.productId.storeId)
                mutableState.value = mutableState.value.copy(
                    busy = false,
                    subscription = subscription,
                    selected = null,
                    notice = null,
                )
            }

            is ProPurchase.Refused -> {
                log.record(OpEvent.PRO_PURCHASE_REFUSED, detail = outcome.failure.code)
                mutableState.value = mutableState.value.copy(
                    busy = false,
                    notice = ProNotice.ofPurchaseFailure(outcome.failure),
                )
            }

            ProPurchase.CancelledByPerson -> {
                // Человек сам закрыл окно магазина: сообщать об этом нечего (Р-68 п. 6).
                log.record(OpEvent.PRO_PURCHASE_CANCELLED, detail = period.storeId)
                mutableState.value = mutableState.value.copy(busy = false, notice = null)
            }
        }
    }

    /**
     * Восстановление покупки — только внутри своего магазина (Р-39).
     *
     * Живёт на шторке потому, что сюда человек попадает после переустановки: он уже
     * упёрся в покупку, и уводить его в настройки за восстановлением незачем (Р-67 п. 4).
     */
    suspend fun restore() {
        val current = mutableState.value
        if (!current.visible || current.busy) return

        mutableState.value = current.copy(busy = true, notice = null)
        when (val outcome = store.restore()) {
            is ProRestore.Restored -> {
                val subscription = ProSubscription.activeIfNotExpired(outcome.productId, outcome.until, time.today())
                cache.write(subscription)
                log.record(OpEvent.PRO_RESTORED, detail = outcome.productId?.storeId ?: "unknown")
                mutableState.value = mutableState.value.copy(
                    busy = false,
                    subscription = subscription,
                    selected = null,
                    notice = null,
                )
            }

            ProRestore.NothingFound -> {
                log.record(OpEvent.PRO_RESTORE_FAILED, detail = "nothing_found")
                mutableState.value = mutableState.value.copy(busy = false, notice = ProNotice.RESTORE_NOTHING_FOUND)
            }

            // Записи в магазине есть, действующей нет: вид поверхности и так называет правду
            // («Не оформлена»), а слов для этого случая в норме §10 пока нет — решение
            // дизайнера, долг назван в `TDD.md` §13. Молчание честнее ложного «покупок
            // не нашлось» (П-7).
            ProRestore.NotActive -> {
                log.record(OpEvent.PRO_RESTORE_FAILED, detail = "not_active")
                mutableState.value = mutableState.value.copy(
                    busy = false,
                    notice = ProNotice.RESTORE_NOT_ACTIVE,
                )
            }

            is ProRestore.Failed -> {
                log.record(OpEvent.PRO_RESTORE_FAILED, detail = outcome.failure.code)
                mutableState.value = mutableState.value.copy(busy = false, notice = ProNotice.RESTORE_FAILED)
            }
        }
    }

    /**
     * Экран управления подписками магазина.
     *
     * Отмену делает магазин, приложение только ведёт туда; своего экрана отмены нет (Р-73 п. 1).
     * Доступ при этом не меняется: снятие подписки приложение узнаёт следующей проверкой.
     */
    suspend fun manage() {
        if (!mutableState.value.visible) return
        // Журнал пишет то, что произошло: не открылось — записи нет, а человеку называются
        // слова. Иначе по журналу разбирали бы жалобу «отменить не даёт» как «человек ушёл
        // в магазин», а сам человек остался бы с мыслью, что отмена состоялась (П-7).
        if (store.openSubscriptionManagement()) {
            log.record(OpEvent.PRO_MANAGEMENT_OPENED)
        } else {
            mutableState.value = mutableState.value.copy(notice = ProNotice.MANAGE_FAILED)
        }
    }

    /** Доступ по кэшу; кэша нет — подписки нет (первый запуск ничего не выдумывает). */
    private suspend fun cachedSubscription(): ProSubscription {
        val active = cache.read() as? ProSubscription.Active ?: return ProSubscription.None
        return ProSubscription.activeIfNotExpired(active.productId, active.until, time.today())
    }

    /**
     * Причина словами над кнопкой при открытии.
     *
     * Магазин недоступен — причина называется, но только тому, у кого доступа нет: у имеющего
     * подписку шторка показывает активное состояние без тревожного вида (П-6, Р-67 п. 3).
     */
    private fun unavailableNotice(subscription: ProSubscription, offers: ProOffers): ProNotice? =
        if (offers is ProOffers.Unavailable && subscription == ProSubscription.None) {
            ProNotice.STORE_UNAVAILABLE
        } else {
            null
        }
}
