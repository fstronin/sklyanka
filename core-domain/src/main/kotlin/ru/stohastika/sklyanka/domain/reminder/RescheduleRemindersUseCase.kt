package ru.stohastika.sklyanka.domain.reminder

import ru.stohastika.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.stohastika.sklyanka.domain.intake.SweepReason
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.reminder.port.AlarmGateway
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminder
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminders
import ru.stohastika.sklyanka.domain.reminder.port.NotificationGateway
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPlanSource
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Duration

/**
 * Пересборка окна будильников — **единственная** операция изменения набора напоминаний
 * (НАП-9, НАП-11, `TDD.md §9`).
 *
 * Точечных «поставить один будильник» и «снять один будильник» в контракте нет намеренно:
 * каждое частичное обновление добавляет класс ошибок «забыли снять» и порождает
 * осиротевшие напоминания о приёмах, которых больше нет. Все двенадцать поводов из таблицы
 * НАП-11 сводятся к одному действию: снять всё лишнее, посчитать окно заново, поставить
 * недостающее. Исключение одно — [SnoozeReminderUseCase], который состав окна не меняет.
 *
 * Идемпотентность: результат зависит только от состояния данных, а не от того, сколько раз
 * и в каком порядке операцию вызвали. Повторный вызов при неизменных данных не снимает
 * и не ставит ни одного будильника.
 */
class RescheduleRemindersUseCase(
    private val plan: ReminderPlanSource,
    private val alarms: AlarmGateway,
    private val notifications: NotificationGateway,
    private val journal: ReminderJournal,
    private val sweepMissed: SweepMissedIntakesUseCase,
    private val time: TimeSource,
) {

    suspend operator fun invoke(reason: RescheduleReason): RescheduleOutcome {
        val now = time.now()
        val zone = time.zone()

        // Сначала честный статус, потом отбор (НАП-34, `TDD.md §9`): иначе просроченные
        // ожидающиеся приёмы попали бы в окно и напомнили о себе задним числом.
        sweepMissed(SweepReason.REPLAN)

        val window = ReminderWindow.startingAt(now)
        // Нижняя граница выборки уходит в прошлое на сутки, а планируется только окно:
        // прошедшие ожидающиеся приёмы будильников не получают (НФТ-35), но их карточки,
        // если они висят в шторке, обязаны сняться вместе с переводом в пропущенные.
        val candidates = plan.candidates(
            from = now.atZone(zone).toLocalDateTime().minusDays(1),
            toInclusive = window.toInclusive.atZone(zone).toLocalDateTime(),
        )
        val live = WindowIntakeSelector.remindable(candidates)
        val wanted = WindowIntakeSelector.select(candidates, window, zone)

        val actual = alarms.scheduled()
        val wantedById = wanted.associateBy { it.intakeId }
        val liveIds = live.map { it.intakeId }.toSet()

        // Снимается не всё, чего нет в окне, а только то, чему больше нечего напоминать.
        // Разница принципиальная: у приёма, который уже прозвучал и был отложен человеком
        // или ждёт настойчивого повтора, время срабатывания ушло **вперёд**, а само
        // назначенное время осталось в прошлом и в окно не попадает (НАП-31). Снятие
        // «всего, чего нет в окне» отменяло бы ровно эти будильники — то есть откладывание
        // и повтор переставали бы работать при любой следующей пересборке.
        val stale = actual.filter { (intakeId, at) ->
            when {
                intakeId !in liveIds -> true
                intakeId in wantedById -> false
                else -> !at.isAfter(now)
            }
        }.keys
        // Переставляются и те будильники, чьё время изменилось: правка расписания обязана
        // унести напоминание со старого часа (НФТ-6).
        val toSchedule = wanted.filter { actual[it.intakeId] != it.at }

        stale.forEach { alarms.cancel(it) }
        toSchedule.forEach { alarms.schedule(it.intakeId, it.at) }

        notifications.retainOnly(live.map { it.intakeId }.toSet())

        // Якорь ставится на каждой пересборке: сутки без единого события — единственный
        // случай, когда пересобрать окно больше некому (НАП-8). Рядом с ним — будильник
        // границы суток: он снимает карточки приёмов, чьё окно истекло (НАП-34).
        alarms.scheduleAnchor(AnchorSchedule.next(now, zone))
        alarms.scheduleDayBoundary(DayBoundarySchedule.next(now, zone))

        val exact = alarms.canScheduleExact()
        val next = wanted.firstOrNull()
        journal.record(
            ReminderLogEntry(
                occurredAt = now,
                event = ReminderEvent.RESCHEDULED,
                reason = reason.code,
                alarmsCancelled = stale.size,
                alarmsScheduled = toSchedule.size,
                scheduledAt = next?.at,
                intakeId = next?.intakeId,
                delivered = exact,
            ),
        )

        return RescheduleOutcome(
            reason = reason,
            cancelled = stale.size,
            scheduled = toSchedule.size,
            total = wanted.size,
            nextAt = next?.at,
            nextIntakeId = next?.intakeId,
            exactAlarmsAllowed = exact,
            notificationsEnabled = notifications.areNotificationsEnabled(),
        )
    }
}

/**
 * Откладывание напоминания человеком (НАП-30).
 *
 * Единственная операция, которая трогает один будильник, а не весь набор: состав окна она
 * не меняет, а переносит одно срабатывание на интервал настройки «Отложить на».
 *
 * `scheduled_at` приёма при этом **не переписывается** (НАП-31). Это принципиально:
 * история «принято вовремя» считается по расхождению `acted_at` и `scheduled_at`,
 * и переписывание плана скрыло бы опоздания, сделав отчёт врачу ложным.
 */
class SnoozeReminderUseCase(
    private val plan: ReminderPlanSource,
    private val alarms: AlarmGateway,
    private val notifications: NotificationGateway,
    private val journal: ReminderJournal,
    private val deferred: DeferredReminders,
    private val time: TimeSource,
) {

    suspend operator fun invoke(intakeId: String, minutes: Int): Boolean {
        // Отмеченный приём откладывать нечего: карточка уже закрыта отметкой (НАП-32).
        val intake = plan.intake(intakeId)?.takeIf { it.status == IntakeStatus.PENDING } ?: return false
        val now = time.now()
        val at = now.plus(Duration.ofMinutes(minutes.toLong()))

        // Запись ставится **до** будильника: она и есть обещание, что повтор будет
        // восстановлен. Процесс, убитый между шагами, оставит след, по которому следующая
        // пересборка поставит будильник; лишняя запись безопасна, потерянная — потерянное
        // обещание (SKL-35, `TDD.md §9.1`).
        deferred.put(DeferredReminder(intakeId, at, attempt = 0))
        notifications.cancel(intake.slot)
        // Тот же адрес будильника — постановка заменяет предыдущий (НАП-9).
        alarms.schedule(intakeId, at)
        journal.record(
            ReminderLogEntry(
                occurredAt = now,
                event = ReminderEvent.SNOOZED,
                intakeId = intakeId,
                medicationId = intake.medicationId,
                profileId = intake.profileId,
                scheduledAt = intake.scheduledAt.atZone(time.zone()).toInstant(),
                firedAt = at,
                minutes = minutes,
            ),
        )
        return true
    }
}
