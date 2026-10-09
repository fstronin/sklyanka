package ru.stohastika.sklyanka.data.room

import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * След миграции и проверки целостности после неё (`ADR-0012` п. 4).
 *
 * Три работы, и все три — **после** открытия базы: журнал лежит в той же базе, которую
 * миграция открывает, поэтому записать след раньше невозможно, а обещать запись отказа
 * нельзя (П-7). Вызывается стартом, вне главного потока.
 *
 * Порядок внутри [onStart] не случаен: след миграции важнее исхода проверки — если проверка
 * упадёт, факт миграции уже записан. Проверка после миграции идёт мимо суточного окна
 * (это её собственный повод), а ежедневная — не чаще раза в сутки.
 */
@Singleton
class DatabaseMaintenance @Inject constructor(
    private val database: SklyankaDatabase,
    private val log: OperationLog,
    private val time: TimeSource,
    private val upgrade: DatabaseUpgrade,
) {

    /** Всё, что старт делает с базой после её открытия. */
    suspend fun onStart() {
        if (recordAppliedMigration()) verify(source = SOURCE_MIGRATION)
        checkDaily()
    }

    /**
     * Записать след применённой миграции.
     *
     * @return была ли миграция: без неё проверять после неё нечего
     */
    suspend fun recordAppliedMigration(): Boolean {
        val pending = upgrade.pendingMigration() ?: return false
        log.record(
            event = OpEvent.MIGRATION_APPLIED,
            before = pending.fromVersion.toString(),
            after = pending.toVersion.toString(),
            source = SOURCE_STARTUP,
            detail = "copy=yes;copyBytes=${pending.copyBytes};" +
                "durationMs=${Duration.between(pending.startedAt, time.now()).toMillis()};result=ok",
        )
        upgrade.clear()
        return true
    }

    /**
     * Проверка целостности не чаще раза в сутки.
     *
     * Отметка спрашивается у журнала точным вопросом «когда проверяли в прошлый раз»
     * ([OperationLog.lastOccurredAt]), а не разбором последних записей: между проверками
     * в журнал ложатся сотни записей отметок, и окно не имеет права зависеть от их числа.
     * Второго хранилища для отметки не заводится: факт уже лежит в журнале, и второе место
     * для него стало бы вторым источником правды.
     */
    suspend fun checkDaily() {
        val now = time.now()
        if (!integrityCheckIsDue(log.lastOccurredAt(OpEvent.INTEGRITY_CHECKED), now)) return
        verify(source = SOURCE_DAILY)
    }

    private suspend fun verify(source: String) {
        val report = DatabaseIntegrityCheck(database).run()
        log.record(event = OpEvent.INTEGRITY_CHECKED, source = source, detail = report.detail())
    }

    private companion object {
        const val SOURCE_STARTUP = "startup"
        const val SOURCE_MIGRATION = "migration"
        const val SOURCE_DAILY = "daily"
    }
}

/** Окно между проверками целостности: сутки между двумя моментами, а не календарные (`ADR-0012` п. 4). */
val INTEGRITY_CHECK_INTERVAL: Duration = Duration.ofHours(24)

/**
 * Пора ли проверять целостность: отметки нет или она не свежее окна.
 *
 * Окно — ровно [interval] между двумя моментами, а не «календарные сутки»: смена пояса
 * или перевод часов не имеет права ни запустить проверку, ни пропустить её (НФТ-48).
 * Ровно на границе — пора: «не чаще раза в сутки» запрещает чаще, а не реже.
 *
 * Правило вынесено функцией, потому что оно предметное и проверяется на JVM: проверять его
 * через базу значило бы поднимать Room ради одного сравнения двух моментов.
 */
fun integrityCheckIsDue(lastCheckAt: Instant?, now: Instant, interval: Duration = INTEGRITY_CHECK_INTERVAL): Boolean =
    lastCheckAt == null || !lastCheckAt.isAfter(now.minus(interval))
