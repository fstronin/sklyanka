package ru.heuri.sklyanka.domain.reminder

import ru.heuri.sklyanka.domain.reminder.port.AlarmGateway
import ru.heuri.sklyanka.domain.reminder.port.PermissionState
import ru.heuri.sklyanka.domain.reminder.port.PermissionStateReader
import ru.heuri.sklyanka.domain.reminder.port.ReminderJournal
import ru.heuri.sklyanka.domain.reminder.port.ReminderPermission
import ru.heuri.sklyanka.domain.reminder.port.ReminderPlanSource
import ru.heuri.sklyanka.domain.time.TimeSource
import java.time.Duration
import java.time.Instant

/**
 * Снимок состояния подсистемы напоминаний (НАП-37, НФТ-52).
 *
 * Диагностика — часть продукта, а не отладочный экран: когда напоминания не приходят,
 * человек не может отличить «приложение сломалось» от «система запретила», и его жалоба
 * без этих данных неразбираема. Наружу снимок не уходит: сети у приложения нет (НАП-39).
 */
data class ReminderDiagnostics(
    /** Последнее сработавшее напоминание: плановое, фактическое, отклонение. */
    val lastShown: ReminderLogEntry?,
    val nextAt: Instant?,
    val nextIntakeId: String?,
    /** Сколько будильников реально стоит в системе (а не сколько домен собирался поставить). */
    val totalScheduled: Int,
    val lastReschedule: ReminderLogEntry?,
    val permissions: Map<ReminderPermission, PermissionState>,
    val reliability: ReminderReliability,
    val outage: ReminderOutage?,
    /** Сводка «сколько сработало вовремя за последние 7 дней» (НФТ-51). */
    val onTime: ReminderOnTimeSummary,
    /** Журнал срабатываний — не менее 50 записей (НАП-37). */
    val journal: List<ReminderLogEntry>,
)

/**
 * Сборка снимка диагностики.
 *
 * Состояние разрешений читается из системы, а не из сохранённого флага (НФТ-52), число
 * будильников — из системы, а не из плана: расхождение «план есть, будильников нет» — это
 * и есть отказ, который экран обязан показать.
 */
class BuildReminderDiagnosticsUseCase(
    private val journal: ReminderJournal,
    private val alarms: AlarmGateway,
    private val permissions: PermissionStateReader,
    private val plan: ReminderPlanSource,
    private val reliability: ReadReminderReliabilityUseCase,
    private val time: TimeSource,
) {

    suspend operator fun invoke(limit: Int = JOURNAL_LIMIT): ReminderDiagnostics {
        val now = time.now()
        val zone = time.zone()
        val scheduled = alarms.scheduled()
        val next = scheduled.entries.filter { it.value.isAfter(now) }.minByOrNull { it.value }

        // Ожидавшиеся срабатывания: приёмы, чей момент прошёл, а отметки так и нет.
        // Нижняя граница — сутки назад: перерыв длиной больше суток человек и так увидит
        // в истории, а выборка на всю базу стоила бы полного прохода по приёмам.
        val expected = WindowIntakeSelector
            .remindable(
                plan.candidates(
                    from = now.atZone(zone).toLocalDateTime().minusDays(1),
                    toInclusive = now.atZone(zone).toLocalDateTime(),
                ),
            )
            .map { ExpectedFiring(it.intakeId, it.scheduledAt.atZone(zone).toInstant()) }

        return ReminderDiagnostics(
            lastShown = journal.last(ReminderEvent.SHOWN),
            nextAt = next?.value,
            nextIntakeId = next?.key,
            totalScheduled = scheduled.size,
            lastReschedule = journal.last(ReminderEvent.RESCHEDULED),
            permissions = permissions.all(),
            reliability = reliability(),
            outage = OutageDetector.detect(
                now = now,
                expected = expected,
                firedIntakeIds = journal.firedIntakeIds(now.minus(OUTAGE_LOOKBACK)),
            ),
            // Сводка считается по промежутку времени, а не по последним записям: «вовремя
            // за 7 дней» от длины показанного журнала зависеть не имеет права (НФТ-51).
            onTime = ReminderOnTimeRule.summarize(journal.since(now.minus(ReminderOnTimeRule.WINDOW))),
            journal = journal.recent(limit),
        )
    }

    companion object {
        /** НАП-37: журнал последних срабатываний — не менее 50 записей. */
        const val JOURNAL_LIMIT: Int = 50

        /** Глубина поиска перерыва: сутки. Дальше вопрос решается историей, а не диагностикой. */
        private val OUTAGE_LOOKBACK: Duration = Duration.ofDays(1)
    }
}
