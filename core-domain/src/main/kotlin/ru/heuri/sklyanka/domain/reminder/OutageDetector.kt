package ru.heuri.sklyanka.domain.reminder

import ru.heuri.sklyanka.domain.reminder.port.ReminderJournal
import ru.heuri.sklyanka.domain.reminder.port.ReminderPlanSource
import ru.heuri.sklyanka.domain.time.TimeSource
import java.time.Duration
import java.time.Instant

/** Срабатывание, которое обязано было случиться: приём ожидался, будильник на него стоял. */
data class ExpectedFiring(val intakeId: String, val at: Instant)

/**
 * Обнаруженный перерыв в напоминаниях (НАП-38).
 *
 * [since] — момент первого несостоявшегося срабатывания: именно его приложение называет
 * человеку («напоминания не приходили с 12:00»), потому что «что-то сломалось» ему
 * ничего не говорит и проверить это нечем.
 */
data class ReminderOutage(val since: Instant, val missed: Int)

/**
 * Признак принудительной остановки приложения (НАП-38, испытание И-7).
 *
 * Прямого способа узнать, что приложение остановили из системных настроек, на Android нет:
 * процесс убит, будильники сняты, и никакого события об этом приложение не получает.
 * Поэтому признак вычисляется косвенно и по фактам, которые уже есть: приём ожидался,
 * его момент прошёл, а в журнале нет записи о срабатывании.
 *
 * Чистая функция: правило обнаружения проверяется на JVM, потому что на устройстве
 * его воспроизведение стоит ручного прогона И-7.
 */
object OutageDetector {

    /**
     * Запас, в пределах которого молчание журнала ещё не считается перерывом.
     *
     * Две минуты — та же граница, что у критерия «сработало вовремя» (НАП-2): пока
     * срабатывание может быть в пути, объявлять отказ рано.
     */
    val GRACE: Duration = Duration.ofMinutes(2)

    fun detect(
        now: Instant,
        expected: List<ExpectedFiring>,
        firedIntakeIds: Set<String>,
        grace: Duration = GRACE,
    ): ReminderOutage? {
        val bound = now.minus(grace)
        val missed = expected
            .filter { !it.at.isAfter(bound) && it.intakeId !in firedIntakeIds }
            .sortedBy { it.at }
        val first = missed.firstOrNull() ?: return null
        return ReminderOutage(since = first.at, missed = missed.size)
    }
}

/**
 * Обнаружение перерыва при запуске приложения (НАП-38).
 *
 * Запись в журнал — часть требования, а не отладка: без неё сообщение «напоминания
 * не приходили с 12:00» невозможно потом подтвердить, а жалоба «оно мне это показало
 * один раз и забыло» неразбираема (`TDD.md §11.1`).
 *
 * Повторный запуск того же перерыва второй записи не создаёт: признак перерыва
 * вычисляется каждый раз заново, и без сверки журнал наполнялся бы одной и той же
 * записью при каждом открытии приложения.
 */
class RecordReminderOutageUseCase(
    private val plan: ReminderPlanSource,
    private val journal: ReminderJournal,
    private val time: TimeSource,
) {

    suspend operator fun invoke(): ReminderOutage? {
        val now = time.now()
        val zone = time.zone()
        val expected = WindowIntakeSelector
            .remindable(
                plan.candidates(
                    from = now.atZone(zone).toLocalDateTime().minusDays(1),
                    toInclusive = now.atZone(zone).toLocalDateTime(),
                ),
            )
            .map { ExpectedFiring(it.intakeId, it.scheduledAt.atZone(zone).toInstant()) }
        val outage = OutageDetector.detect(
            now = now,
            expected = expected,
            firedIntakeIds = journal.firedIntakeIds(now.minus(LOOKBACK)),
        ) ?: return null

        val recorded = journal.last(ReminderEvent.OUTAGE)
        if (recorded?.scheduledAt != outage.since) {
            journal.record(
                ReminderLogEntry(
                    occurredAt = now,
                    event = ReminderEvent.OUTAGE,
                    // Момент, с которого напоминания не приходили, — то, что приложение
                    // называет человеку; число несработавших идёт рядом.
                    scheduledAt = outage.since,
                    alarmsScheduled = outage.missed,
                    delivered = false,
                ),
            )
        }
        return outage
    }

    private companion object {
        /** Глубина поиска — сутки: перерыв длиннее человек видит в истории приёмов. */
        val LOOKBACK: Duration = Duration.ofDays(1)
    }
}
