package ru.stohastika.sklyanka.reminder

import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.port.DirectBootTraceStore
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Перенос следов locked-фазы в журнал срабатываний после разблокировки (SKL-87).
 *
 * Вынесен в отдельную операцию, потому что вызывается из двух мест — приёмник по
 * `USER_UNLOCKED` и старт приложения как страховка, если процесс убили между разблокировкой
 * и записью, — и должен одинаково отвечать за «забрали — записали — очистили».
 * `drain()` очищает очередь одной операцией, поэтому двойной записи не бывает.
 *
 * Запись — о пересборке, а не о приёме: `intakeId`, `medicationId` и `profileId` не
 * заполняются, медицинских данных в записи нет по построению (НФТ-19). Различие «снимка
 * не было / устарел / не читается» остаётся в DE-трассе, а в журнал идут только числа.
 */
@Singleton
class DirectBootTraceDrain @Inject constructor(
    private val traces: DirectBootTraceStore,
    private val journal: ReminderJournal,
) {

    suspend fun drainIntoJournal() {
        traces.drain().forEach { trace ->
            journal.record(
                ReminderLogEntry(
                    occurredAt = trace.occurredAt,
                    event = ReminderEvent.RESCHEDULED,
                    reason = RescheduleReason.LOCKED_BOOT.code,
                    alarmsScheduled = trace.replayed,
                    alarmsCancelled = trace.skipped + trace.failed,
                ),
            )
        }
    }
}
