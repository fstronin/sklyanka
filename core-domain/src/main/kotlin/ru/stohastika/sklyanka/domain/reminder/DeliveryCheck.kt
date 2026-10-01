package ru.stohastika.sklyanka.domain.reminder

import java.time.Duration
import java.time.Instant

/**
 * Исход самопроверки доставки (ПМ-21, ОВ-108).
 *
 * Проверка — не приём: она не создаёт записей приёма, истории и остатка. Её предмет —
 * дошло ли тестовое напоминание до человека, и исход называет это словами.
 */
enum class DeliveryCheckOutcome {
    /** Уложилось в допуск НАП-2: 0…+120 секунд. */
    ON_TIME,

    /** Опоздало больше допуска; число минут называет блок и баннер. */
    LATE,

    /** Срабатывания не было или показа не было — тестовое напоминание не дошло. */
    NOT_DELIVERED,

    /** Приложение было открыто в момент срабатывания: фоновый путь не проверялся. */
    NOT_COMPLETED,
}

/**
 * Состояние самопроверки: блок на «Проверке напоминаний» и баннер при входе читают одно
 * и то же (ОВ-108).
 *
 * Ни одно состояние не молчит (НФТ-8): «идёт», «дошло», «не дошло» и «не состоялась» —
 * разные ответы, и человеку не приходится отличать «ещё не сработало» от «сломалось».
 */
sealed interface DeliveryCheckState {

    /** Проверок ещё не было или последняя отменена. */
    data object None : DeliveryCheckState

    /** Проверка идёт: тестовое срабатывание назначено, но ещё не обработано. */
    data class Running(val dueAt: Instant) : DeliveryCheckState

    /**
     * Проверка завершена.
     *
     * @param dueAt назначенный момент срабатывания
     * @param resolvedAt момент, в который исход стал известен
     * @param lateMinutes целые минуты опоздания для слов; `null` — опоздания нет
     */
    data class Finished(
        val outcome: DeliveryCheckOutcome,
        val dueAt: Instant,
        val resolvedAt: Instant,
        val lateMinutes: Long?,
    ) : DeliveryCheckState
}

/**
 * Правила самопроверки: запас до срабатывания и чтение состояния из журнала (ОВ-108).
 *
 * Состояние живёт в журнале, а не в памяти процесса: приложение на EMUI выгружается,
 * и результат проверки обязан пережить смерть процесса. Отдельного хранилища состояния
 * поэтому нет — есть записи [ReminderEvent.DELIVERY_CHECK], отличимые от боевых
 * срабатываний по построению (НАП-4, НФТ-50).
 *
 * Порог подавления SKL-88 к проверке не применяется: она показывается и при опоздании
 * больше 30 минут, иначе «не дошло» не отличить от «дошло слишком поздно» (ОВ-109).
 */
object DeliveryCheckRule {

    /** Запас до тестового срабатывания: 1–2 минуты по инструкции, с местом закрыть приложение. */
    val DELAY: Duration = Duration.ofSeconds(90)

    /**
     * Состояние по последней записи проверки.
     *
     * `SCHEDULED` и `FIRED` — ещё не завершена: `FIRED` без `SHOWN` означает, что процесс
     * мог умереть между вызовом показа и отметкой предъявления, и решение достроит вход
     * (НАП-4). Отменённая, отсутствующая и незнакомая запись — «проверки нет».
     */
    fun stateOf(last: ReminderLogEntry?): DeliveryCheckState {
        if (last == null || last.event != ReminderEvent.DELIVERY_CHECK) return DeliveryCheckState.None
        val dueAt = last.scheduledAt ?: last.occurredAt
        return when (last.reason) {
            DeliveryCheckReason.SCHEDULED, DeliveryCheckReason.FIRED -> DeliveryCheckState.Running(dueAt)

            DeliveryCheckReason.SHOWN -> {
                val outcome = outcomeOf(last.deviationSeconds ?: 0L)
                DeliveryCheckState.Finished(
                    outcome = outcome,
                    dueAt = dueAt,
                    resolvedAt = last.occurredAt,
                    lateMinutes = if (outcome == DeliveryCheckOutcome.LATE) {
                        lateMinutes(last.deviationSeconds ?: 0L)
                    } else {
                        null
                    },
                )
            }

            DeliveryCheckReason.NOT_DELIVERED -> DeliveryCheckState.Finished(
                outcome = DeliveryCheckOutcome.NOT_DELIVERED,
                dueAt = dueAt,
                resolvedAt = last.occurredAt,
                lateMinutes = null,
            )

            DeliveryCheckReason.NOT_COMPLETED -> DeliveryCheckState.Finished(
                outcome = DeliveryCheckOutcome.NOT_COMPLETED,
                dueAt = dueAt,
                resolvedAt = last.occurredAt,
                lateMinutes = null,
            )

            else -> DeliveryCheckState.None
        }
    }

    /** «Вовремя» — тот же критерий, что у боевого напоминания: 0…+120 с (НАП-2). */
    fun outcomeOf(deviationSeconds: Long): DeliveryCheckOutcome = if (ReminderOnTimeRule.isOnTime(deviationSeconds)) {
        DeliveryCheckOutcome.ON_TIME
    } else {
        DeliveryCheckOutcome.LATE
    }

    /** Целые минуты опоздания для слов; правило то же, что в `ReminderDeviationWords`. */
    fun lateMinutes(deviationSeconds: Long): Long = ReminderDeviationWords.lateMinutes(deviationSeconds)

    /**
     * Что объявить баннером при входе, или `null` — объявлять нечего (ОВ-108).
     *
     * «Проверка не состоялась» баннера не даёт: человек в приложении, и исход показывается
     * прямо в блоке. Уже объявленный результат не повторяется — иначе провалившаяся
     * проверка возвращалась бы баннером вечно.
     */
    fun announcementOf(state: DeliveryCheckState, announced: Instant?): DeliveryCheckState.Finished? =
        (state as? DeliveryCheckState.Finished)?.takeIf {
            it.outcome != DeliveryCheckOutcome.NOT_COMPLETED && it.dueAt != announced
        }
}
