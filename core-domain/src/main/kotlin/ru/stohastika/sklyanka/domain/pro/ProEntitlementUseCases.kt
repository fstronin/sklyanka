package ru.stohastika.sklyanka.domain.pro

import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.LocalDate

/**
 * Подпись и состояние подписки для строки блока «Склянка Про» в настройках (Р-67 п. 6).
 *
 * Дата в словах — забота экрана: `%1$s` подставляет платформенный форматтер с локалью
 * интерфейса (НФТ-43а), а не склейка строк. Здесь — только сама подпись и дата, которую
 * нужно в неё подставить.
 */
data class ProStatus(val subscription: ProSubscription) {

    /** Ключ значения строки: «Активна до <дата>» или «Не оформлена». */
    val notice: ProNotice
        get() = if (subscription is ProSubscription.Active) ProNotice.STATE_ACTIVE_UNTIL else ProNotice.STATE_INACTIVE

    val until: LocalDate? get() = (subscription as? ProSubscription.Active)?.until

    /** Есть ли доступ «Про» прямо сейчас — то, чем живут гейты состава (Р-22, Р-28, Р-32). */
    val included: Boolean get() = subscription is ProSubscription.Active
}

/**
 * Состояние подписки для интерфейса и гейтов: читается из кэша и не ходит в магазин.
 *
 * Гейты состава читают это, а не магазин (`docs/tasks/SKL-122/task.md`): проверка подписки
 * не имеет права оказаться между человеком и приёмом лекарства (Р-7, П-6).
 */
class ObserveProStatusUseCase(private val cache: ProSubscriptionCache, private val time: TimeSource) {

    /**
     * Состояние подписки по кэшу.
     *
     * Кэш — не «примерно» и не «с погрешностью»: это последний ответ магазина, у которого
     * срока давности нет (Р-68 п. 7). Истечение считается здесь, а не в магазине, — иначе
     * доступ отнимался бы только у того, кто в сеть вышел.
     */
    suspend operator fun invoke(): ProStatus {
        val cached = cache.read() as? ProSubscription.Active ?: return ProStatus(ProSubscription.None)
        return ProStatus(ProSubscription.activeIfNotExpired(cached.productId, cached.until, time.today()))
    }
}

/**
 * Пора ли переспросить магазин о подписке.
 *
 * Проверка идёт при запуске процесса, а процесс живёт долго: магазин мог молча продлить
 * подписку, и запись в кэше к этому моменту просрочена — по ней доступ уже не даётся,
 * и платящий человек увидел бы витрину вместо управления подпиской. Условие узкое намеренно:
 * спрашиваем только тогда, когда в кэше **лежит** просроченная запись. Пустой кэш — это
 * «человек ничего не покупал», и дёргать магазин на каждом возвращении в приложение
 * незачем: учёт работает без сети (П-6, Р-68 п. 7).
 */
class ProSubscriptionRefreshDueUseCase(private val cache: ProSubscriptionCache, private val time: TimeSource) {

    suspend operator fun invoke(): Boolean {
        val record = cache.read() as? ProSubscription.Active ?: return false
        return ProSubscription.activeIfNotExpired(record.productId, record.until, time.today()) ==
            ProSubscription.None
    }
}

/**
 * Проверка подписки при запуске с сетью (`ADR-0015` п. 1).
 *
 * Отказ проверки ничего не меняет: кэш остаётся как был, доступ сохраняется, и это
 * не «до N дней», а бессрочно (П-6, Р-68 п. 7). Принятый риск назван в решении честно:
 * человек, отменивший подписку и не выходящий в сеть, остаётся с доступом.
 */
class RefreshProSubscriptionUseCase(
    private val store: ProStore,
    private val cache: ProSubscriptionCache,
    private val log: OperationLog,
    private val time: TimeSource,
) {

    suspend operator fun invoke() {
        when (val outcome = store.owned()) {
            is ProOwned.Owned -> {
                cache.write(ProSubscription.activeIfNotExpired(outcome.productId, outcome.until, time.today()))
                log.record(OpEvent.PRO_SUBSCRIPTION_CHECKED, detail = "owned")
            }

            ProOwned.None -> {
                cache.write(ProSubscription.None)
                log.record(OpEvent.PRO_SUBSCRIPTION_CHECKED, detail = "none")
            }

            // Магазин о подписке знает, действующей записи нет (истекла, возвращена,
            // отменена): доступ кончается так же, как без записи, но след в журнале
            // называет случай своим именем — по нему разбирают жалобы (П-7).
            ProOwned.NotActive -> {
                cache.write(ProSubscription.None)
                log.record(OpEvent.PRO_SUBSCRIPTION_CHECKED, detail = "not_active")
            }

            ProOwned.Unavailable -> {
                // Ничего не пишем: неудачная проверка не отменяет прошлый ответ магазина.
                log.record(OpEvent.PRO_SUBSCRIPTION_CHECKED, detail = "unavailable")
            }
        }
    }
}
