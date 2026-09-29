package ru.stohastika.sklyanka.domain.reminder

import java.time.Duration
import java.time.Instant

/**
 * Состояние, от которого зависит решение о повторе (НАП-25).
 *
 * [shownAttempt] — номер уже состоявшегося показа: 0 — первое напоминание, 1 — первый повтор.
 */
data class RepeatState(
    val shownAttempt: Int,
    val stillPending: Boolean,
    val persistentEnabled: Boolean,
    val snoozeMinutes: Int,
)

/** Почему повтора не будет. Код уходит в журнал служебной строкой (НФТ-42). */
enum class RepeatStop(val code: String) {
    /** Настройка «Настойчивое напоминание» выключена — повторов нет ни одного (НАП-25). */
    DISABLED("disabled"),

    /** Приём отмечен: напоминать больше не о чем (НАП-32). */
    MARKED("marked"),

    /** Повторы исчерпаны (НАП-26). */
    LIMIT_REACHED("limit_reached"),
}

/** Что делать после показа напоминания. */
sealed interface RepeatDecision {

    /** Поставить повтор: момент, номер показа и канал. */
    data class Repeat(val at: Instant, val attempt: Int, val channel: ReminderChannel) : RepeatDecision

    /** Повтора не будет; приём остаётся ожидающимся и истечёт по общему правилу (НАП-33). */
    data class Stop(val reason: RepeatStop) : RepeatDecision
}

/**
 * Правило настойчивости (НАП-25, НАП-26).
 *
 * Условий ровно два, и оба продуктовые: настройка включена и приём остался неотмеченным
 * через интервал «Отложить на». От лекарства, профиля и времени суток правило не зависит —
 * любое усложнение пришлось бы объяснять человеку в интерфейсе, а объяснить его там негде.
 *
 * Интервал повтора — тот же, что у кнопки откладывания: два разных интервала в одном экране
 * настроек человек не разведёт (`task.md`, разбор расхождения макета).
 */
object PersistentRepeatPolicy {

    /**
     * Предел числа повторов (НАП-26, ОВ по числу повторов не закрыт).
     *
     * Значение на согласовании; предложение постановки — не более двух. Держится одной
     * именованной константой именно поэтому: смена решения не должна разъехаться по коду.
     * Бесконечный повтор запрещён — он приводит к отключению уведомлений целиком,
     * то есть к полному отказу.
     */
    const val MAX_REPEATS: Int = 2

    /** Первое напоминание — всегда обычный канал; повтор — настойчивый (НАП-25). */
    fun channelFor(attempt: Int): ReminderChannel =
        if (attempt <= 0) ReminderChannel.REGULAR else ReminderChannel.PERSISTENT

    fun decide(now: Instant, state: RepeatState): RepeatDecision = when {
        !state.stillPending -> RepeatDecision.Stop(RepeatStop.MARKED)
        !state.persistentEnabled -> RepeatDecision.Stop(RepeatStop.DISABLED)
        state.shownAttempt >= MAX_REPEATS -> RepeatDecision.Stop(RepeatStop.LIMIT_REACHED)
        else -> {
            val attempt = state.shownAttempt + 1
            RepeatDecision.Repeat(
                at = now.plus(Duration.ofMinutes(state.snoozeMinutes.toLong())),
                attempt = attempt,
                channel = channelFor(attempt),
            )
        }
    }
}
