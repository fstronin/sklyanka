package ru.stohastika.sklyanka.domain.reminder

import ru.stohastika.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.stohastika.sklyanka.domain.intake.SweepReason
import ru.stohastika.sklyanka.domain.reminder.port.AlarmGateway
import ru.stohastika.sklyanka.domain.reminder.port.NotificationGateway
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPlanSource
import ru.stohastika.sklyanka.domain.time.TimeSource

/**
 * Граница суток: приёмы истекли — карточки сняты (НАП-33, НАП-34, ПР-1).
 *
 * Единственное место подсистемы, которое срабатывает по времени, а не по действию человека
 * или по системному событию. Нужно оно ровно для одного: пока телефон включён и приложение
 * не открывают, неотмеченный приём обязан стать пропущенным в полночь, а его карточка —
 * исчезнуть. Ждать до якорного будильника нельзя: до 03:17 в шторке висело бы уведомление
 * о приёме, который уже пропущен, и человек отметил бы его задним числом.
 *
 * Новых уведомлений проход не порождает ни одного — этого не может сделать ни один его порт
 * (НАП-34). Телефон, включённый после суток простоя, не выдаёт очередь карточек о том,
 * что всё пропущено (испытание И-10).
 */
class SweepAtDayBoundaryUseCase(
    private val sweepMissed: SweepMissedIntakesUseCase,
    private val plan: ReminderPlanSource,
    private val notifications: NotificationGateway,
    private val alarms: AlarmGateway,
    private val journal: ReminderJournal,
    private val time: TimeSource,
) {

    suspend operator fun invoke(): Int {
        val now = time.now()
        val zone = time.zone()
        val swept = sweepMissed(SweepReason.TIMER)

        // Что ещё ожидается: по этому набору снимаются карточки приёмов, которые
        // ожидающимися быть перестали.
        val live = WindowIntakeSelector.remindable(
            plan.candidates(
                from = now.atZone(zone).toLocalDateTime().minusDays(1),
                toInclusive = now.atZone(zone).toLocalDateTime().plusHours(HORIZON_HOURS),
            ),
        )
        notifications.retainOnly(live.map { it.intakeId }.toSet())

        // Следующая граница ставится сразу: будильник одноразовый, а сутки — нет.
        alarms.scheduleDayBoundary(DayBoundarySchedule.next(now, zone))

        if (swept.expired > 0) {
            journal.record(
                ReminderLogEntry(
                    occurredAt = now,
                    event = ReminderEvent.MISSED_SWEEP,
                    reason = SweepReason.TIMER.code,
                    // Сколько приёмов истекло — число, а не идентификаторы: в журнале
                    // срабатываний важен масштаб, а разбор по приёмам идёт по журналу
                    // операций (SKL-07).
                    alarmsCancelled = swept.expired,
                    delivered = false,
                ),
            )
        }
        return swept.expired
    }

    private companion object {
        /** Тот же горизонт, что у окна планирования: набор ожидающихся считается одинаково. */
        const val HORIZON_HOURS = 48L
    }
}
