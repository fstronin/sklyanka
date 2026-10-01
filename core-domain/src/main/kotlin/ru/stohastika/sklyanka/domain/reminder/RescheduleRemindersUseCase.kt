package ru.stohastika.sklyanka.domain.reminder

import ru.stohastika.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.stohastika.sklyanka.domain.intake.SweepReason
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.reminder.port.AlarmGateway
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminder
import ru.stohastika.sklyanka.domain.reminder.port.DeferredReminders
import ru.stohastika.sklyanka.domain.reminder.port.DeviceEnvironmentSource
import ru.stohastika.sklyanka.domain.reminder.port.NotificationGateway
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPlanSource
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

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
 *
 * Кроме окна приёмов пересборка видит постоянные записи отложенных срабатываний
 * (SKL-35, ОВ-75): отложенный приём ждёт повтора, но его `scheduled_at` уже в прошлом
 * и в окно планирования не попадает. Записи читаются по идентификатору приёма — приём
 * мог выпасть из окна выборки, — а живучесть каждой решает то же правило
 * `WindowIntakeSelector.remindable`, что пускает приёмы в план.
 *
 * Будильник ставится не для любой живой записи, а только для уместной по времени:
 * слишком опоздавший повтор снимается без показа ([DeferredShowPolicy], НАП-41, ОВ-99).
 */
@Suppress("LongParameterList")
class RescheduleRemindersUseCase(
    private val plan: ReminderPlanSource,
    private val alarms: AlarmGateway,
    private val notifications: NotificationGateway,
    private val journal: ReminderJournal,
    private val deferred: DeferredReminders,
    private val sweepMissed: SweepMissedIntakesUseCase,
    private val environment: DeviceEnvironmentSource,
    private val time: TimeSource,
) {

    suspend operator fun invoke(reason: RescheduleReason): RescheduleOutcome {
        val now = time.now()
        val zone = time.zone()

        // Сначала честный статус, потом отбор (НАП-34, `TDD.md §9`): иначе просроченные
        // ожидающиеся приёмы попали бы в окно и напомнили о себе задним числом.
        sweepMissed(SweepReason.REPLAN)

        val deferredPlan = reconcileDeferred(now, zone)

        val window = ReminderWindow.startingAt(now)
        // Нижняя граница выборки уходит в прошлое на сутки, а планируется только окно:
        // прошедшие ожидающиеся приёмы будильников не получают (НФТ-35), но их карточки,
        // если они висят в шторке, обязаны сняться вместе с переводом в пропущенные.
        val candidates = plan.candidates(
            from = now.atZone(zone).toLocalDateTime().minusDays(1),
            toInclusive = window.toInclusive.atZone(zone).toLocalDateTime(),
        )
        val live = WindowIntakeSelector.remindable(candidates)
        val wantedWindow = WindowIntakeSelector.select(candidates, window, zone)

        // Отложенное срабатывание важнее окна: после перелёта на запад приём может снова
        // попасть в окно, и для одного `intake_id` оказались бы два намерения — плановое
        // и отложенное. Выбирается отложенное: человек (или правило повтора) уже решил
        // сдвинуть именно его, а `scheduled_at` остаётся прежним (НАП-31).
        val planned = merge(wantedWindow, deferredPlan.wanted)
        val plannedById = planned.associateBy { it.intakeId }
        val liveIds = live.map { it.intakeId }.toSet() + deferredPlan.liveIds

        val actual = alarms.scheduled()

        // Снимается не всё, чего нет в окне, а только то, чему больше нечего напоминать.
        // Разница принципиальная: у приёма, который уже прозвучал и был отложен человеком
        // или ждёт настойчивого повтора, время срабатывания ушло **вперёд**, а само
        // назначенное время осталось в прошлом и в окно не попадает (НАП-31). Снятие
        // «всего, чего нет в окне» отменяло бы ровно эти будильники — то есть откладывание
        // и повтор переставали бы работать при любой следующей пересборке.
        val stale = actual.filter { (intakeId, at) ->
            when {
                intakeId !in liveIds -> true
                intakeId in plannedById -> false
                else -> !at.isAfter(now)
            }
        }.keys
        // Переставляются и те будильники, чьё время изменилось: правка расписания обязана
        // унести напоминание со старого часа (НФТ-6).
        val toSchedule = planned.filter { actual[it.intakeId] != it.at }

        stale.forEach { alarms.cancel(it) }
        toSchedule.forEach { alarms.schedule(it.intakeId, it.at, it.attempt) }
        alarms.syncMirror(planned.map { DirectBootAlarm(it.intakeId, it.at, it.attempt) })

        notifications.retainOnly(liveIds)

        // Якорь ставится на каждой пересборке: сутки без единого события — единственный
        // случай, когда пересобрать окно больше некому (НАП-8). Рядом с ним — будильник
        // границы суток: он снимает карточки приёмов, чьё окно истекло (НАП-34).
        alarms.scheduleAnchor(AnchorSchedule.next(now, zone))
        alarms.scheduleDayBoundary(DayBoundarySchedule.next(now, zone))

        val exact = alarms.canScheduleExact()
        val next = planned.firstOrNull()
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
        journalEnvironment(now)

        // След восстановления — только на действительно поставленное заново: иначе журнал
        // превратился бы в журнал пересборок, а не изменений (SKL-35, `TDD.md §5.3`).
        toSchedule.filter { it.intakeId in deferredPlan.liveIds }.forEach { journalRestored(now, it, zone) }

        return RescheduleOutcome(
            reason = reason,
            cancelled = stale.size,
            scheduled = toSchedule.size,
            total = planned.size,
            nextAt = next?.at,
            nextIntakeId = next?.intakeId,
            exactAlarmsAllowed = exact,
            notificationsEnabled = notifications.areNotificationsEnabled(),
        )
    }

    /**
     * Среда измерения пишется после причины пересборки (SKL-85, `TDD.md §9.2`).
     *
     * Её запись — граница окна A/B-прогона, и она обязана попасть на каждый путь
     * пересборки. Механизм приходит из того же порта, что ставит будильники; внутри
     * сравнение с последней записью: неизменная среда молчит (SKL-47).
     */
    private suspend fun journalEnvironment(now: Instant) {
        val actual = ReminderEnvironment(alarms.alarmKind(), environment.environment())
        journal.recordEnvironment(
            ReminderLogEntry(
                occurredAt = now,
                event = ReminderEvent.ENVIRONMENT,
                reason = actual.encode(),
            ),
        )
    }

    /**
     * Живучесть постоянных записей и план отложенных срабатываний (`TDD.md §9.3`, шаги 2–4, 6).
     *
     * Записи читаются по приёму, а не через окно: `scheduled_at` отложенного приёма уже
     * в прошлом, и в выборке кандидатов его бы не было. Живучесть решает то же правило
     * `WindowIntakeSelector.remindable`, что пускает приёмы в план.
     *
     * Запись снимается по двум поводам, и оба ведут к одному журнальному следу
     * `DEFERRED{dropped}`: приём больше не ожидается (отмечен, истёк, в архиве,
     * «по потребности», в чужом профиле без дублирования, удалён) **или** показ
     * восстановленного повтора уже неуместен по времени (`DeferredShowPolicy`, НАП-41,
     * ОВ-99). Порог — только про показ: приём при этом остаётся `pending` до конца суток
     * (ОВ-4), а не переводится в пропущенные.
     */
    private suspend fun reconcileDeferred(now: Instant, zone: ZoneId): DeferredPlan {
        val records = deferred.all()
        val paired = records.mapNotNull { record -> plan.intake(record.intakeId)?.let { it to record } }
        val liveIds = WindowIntakeSelector.remindable(paired.map { it.first }).map { it.intakeId }.toSet()
        val pairedById = paired.associateBy { (intake, _) -> intake.intakeId }

        // Один проход по записям разводит два исхода: поставить будильник или снять запись.
        // Снятие идемпотентно по факту: журнальная строка появляется только тогда, когда
        // запись действительно была снята (принцип SKL-47 «один факт — одна запись»).
        val wanted = mutableListOf<PlannedReminder>()
        records.forEach { record ->
            val intake = pairedById[record.intakeId]?.first
            val tooLate = !DeferredShowPolicy.shouldShow(now, record.fireAt)
            if (intake == null || intake.intakeId !in liveIds || tooLate) {
                if (deferred.remove(record.intakeId)) journalDropped(now, record, intake, zone)
            } else {
                wanted += PlannedReminder(
                    intakeId = intake.intakeId,
                    at = record.fireAt,
                    scheduledAt = intake.scheduledAt,
                    medicationId = intake.medicationId,
                    profileId = intake.profileId,
                    attempt = record.attempt,
                )
            }
        }
        return DeferredPlan(liveIds = liveIds, wanted = wanted)
    }

    /** Слияние плана окна и отложенных срабатываний: при совпадении побеждает отложенное. */
    private fun merge(window: List<PlannedReminder>, deferred: List<PlannedReminder>): List<PlannedReminder> =
        (window.associateBy { it.intakeId } + deferred.associateBy { it.intakeId })
            .values
            .sortedWith(compareBy({ it.at }, { it.intakeId }))

    /** След снятой записи: приём больше не ожидается, повтору ждать нечего. */
    private suspend fun journalDropped(now: Instant, record: DeferredReminder, intake: ReminderIntake?, zone: ZoneId) {
        journal.record(
            ReminderLogEntry(
                occurredAt = now,
                event = ReminderEvent.DEFERRED,
                reason = DeferredReason.DROPPED,
                intakeId = record.intakeId,
                medicationId = intake?.medicationId,
                profileId = intake?.profileId,
                scheduledAt = intake?.scheduledAt?.atZone(zone)?.toInstant(),
                firedAt = record.fireAt,
                attempt = record.attempt,
            ),
        )
    }

    /**
     * След восстановленного срабатывания: будильник поставлен на момент из записи.
     *
     * Запись идёт идемпотентным контрактом (SKL-47): пересборок может быть две
     * одновременно (запуск и перезагрузка), а факт восстановления — один.
     */
    private suspend fun journalRestored(now: Instant, planned: PlannedReminder, zone: ZoneId) {
        journal.recordRestored(
            ReminderLogEntry(
                occurredAt = now,
                event = ReminderEvent.DEFERRED,
                reason = DeferredReason.RESTORED,
                intakeId = planned.intakeId,
                medicationId = planned.medicationId,
                profileId = planned.profileId,
                scheduledAt = planned.scheduledAt.atZone(zone).toInstant(),
                firedAt = planned.at,
                attempt = planned.attempt,
            ),
        )
    }
}

/**
 * Итог проверки постоянных записей (`TDD.md §9.3`, шаги 3–4).
 *
 * [liveIds] — приёмы, о которых напоминать ещё есть кому; [wanted] — отложенные
 * срабатывания, которые обязаны стоять в системе. Разделение на два поля, а не на карту
 * «приём — запись»: пересборке нужны и записи, и ответ «чья запись жива» отдельно.
 */
private data class DeferredPlan(val liveIds: Set<String>, val wanted: List<PlannedReminder>)

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
