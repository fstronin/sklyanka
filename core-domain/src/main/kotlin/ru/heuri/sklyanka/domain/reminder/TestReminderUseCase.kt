package ru.heuri.sklyanka.domain.reminder

import ru.heuri.sklyanka.domain.reminder.port.AlarmGateway
import ru.heuri.sklyanka.domain.reminder.port.NotificationGateway
import ru.heuri.sklyanka.domain.reminder.port.ReminderJournal
import ru.heuri.sklyanka.domain.time.TimeSource
import java.time.Duration
import java.time.Instant

/**
 * Пробное напоминание через минуту (НАП-22, НАП-23).
 *
 * Проходит весь боевой путь — точный будильник, канал, полноэкранная карточка, — и при этом
 * не создаёт `Intake`, не меняет историю, приверженность и остаток. Его назначение: человек
 * своими глазами убеждается, что механизм работает, а в журнале появляется первая запись
 * с отклонением.
 *
 * Операция публичная: её вызывает и диагностика, и онбординг разрешений (SKL-14).
 */
class TestReminderUseCase(private val alarms: AlarmGateway, private val time: TimeSource) {

    /** @return момент, на который поставлено пробное напоминание. */
    suspend operator fun invoke(): Instant {
        val at = time.now().plus(DELAY)
        alarms.scheduleTest(at)
        return at
    }

    companion object {
        /** «Через минуту» из НАП-22 — ровно минута, а не «примерно». */
        val DELAY: Duration = Duration.ofMinutes(1)
    }
}

/**
 * Показ пробного напоминания.
 *
 * Записи приёма у него нет по построению: ни один порт этого сценария не умеет писать
 * в приёмы, поэтому «пробное напоминание не трогает историю» — свойство состава
 * зависимостей, а не дисциплины вызова.
 */
class ShowTestReminderUseCase(
    private val notifications: NotificationGateway,
    private val journal: ReminderJournal,
    private val time: TimeSource,
) {

    suspend operator fun invoke(plannedAt: Instant) {
        val now = time.now()
        notifications.showTest()
        journal.record(
            ReminderLogEntry(
                occurredAt = now,
                event = ReminderEvent.TEST,
                scheduledAt = plannedAt,
                firedAt = now,
                deviationSeconds = Duration.between(plannedAt, now).seconds,
                channel = ReminderChannel.REGULAR,
                delivered = notifications.areNotificationsEnabled(),
            ),
        )
    }
}
