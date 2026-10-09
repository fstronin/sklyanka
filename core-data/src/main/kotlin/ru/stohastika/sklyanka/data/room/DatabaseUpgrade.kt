package ru.stohastika.sklyanka.data.room

import android.content.Context
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Instant

/**
 * Что предстоит базе при открытии (`ADR-0012` пп. 3, 4).
 *
 * Подготовка — единственное место, где решается судьба копии: базу старее сборки нельзя
 * открыть без копии, а базу при нехватке места нельзя открывать вовсе. Сборщик базы
 * спрашивает ответ здесь и до `build()` — это единственная точка, где миграция ещё
 * не началась, а путь к ней уже известен.
 *
 * **Решение о состоянии принимается здесь же и один раз на процесс** ([state]). Затем это
 * и нужно: экран запуска, приёмник будильника и сборщик базы обязаны получить один и тот же
 * ответ, а отказ по месту, случившийся между проверкой и копией, обязан сменить решение,
 * а не разойтись с ним. Читай состояние позже — получишь правду о том, что произошло.
 *
 * Зависимостей от базы и журнала у подготовки **нет** и не появится: журнал лежит в той же
 * базе, и обратная зависимость замкнула бы сборку графа в кольцо (база → журнал → база).
 * Направление одностороннее: следящий шаг читает исход отсюда, а не наоборот.
 *
 * Собирается в графе явным `@Provides` (`DataModule`), а не конструктором с `@Inject`:
 * имя базы — параметр со значением по умолчанию, а Dagger подставил бы в него строку
 * и потребовал бы для неё отдельную привязку. Имя знают и тесты — они создают базу
 * предыдущей версии сами, — поэтому оно параметр везде, где о нём спрашивают.
 */
class DatabaseUpgrade(
    private val context: Context,
    private val time: TimeSource,
    private val databaseName: String = SklyankaDatabase.NAME,
) {

    private var decided: DatabaseState? = null

    private var storedVersion: Int? = null

    private var pending: PendingMigration? = null

    /**
     * Состояние базы — одно решение на процесс (Р-44, `ADR-0012` пп. 3, 7).
     *
     * Считается при первом вопросе и остаётся решением до конца процесса; сменить его может
     * только отказ по месту, случившийся при подготовке ([prepare]). Второй источник правды
     * об этом (`databaseStateOf` в каждом приёмнике) считает то же правило по тем же фактам —
     * расходиться им нечем, кроме места, которое как раз и перепроверяется перед копией.
     */
    fun state(): DatabaseState = decided ?: decide().also { decided = it }

    /** Подготовить базу по собственному решению о её состоянии. */
    fun prepare(): DatabaseUpgradeOutcome = prepare(state())

    /**
     * Подготовить базу по названному состоянию.
     *
     * Отдельный вход для стенда приёмки: он называет факты сам («копии нужно 50 МБ,
     * свободно 10 МБ»), потому что кончившегося места на стенде не устроить. Продукт зовёт
     * [prepare] без аргумента — решение берётся из [state].
     */
    fun prepare(state: DatabaseState): DatabaseUpgradeOutcome = when (state) {
        DatabaseState.Ready -> pendingFromVersion()?.let(::copyOrRefuse) ?: DatabaseUpgradeOutcome.NoMigration
        DatabaseState.Newer -> DatabaseUpgradeOutcome.NoMigration
        is DatabaseState.NotEnoughSpace -> DatabaseUpgradeOutcome.RefusedForSpace
    }

    /** Миграция, копия под которую уже снята; `null` — открытие пройдёт без перехода схемы. */
    fun pendingMigration(): PendingMigration? = pending

    /** След записан — исход больше не нужен. */
    fun clear() {
        pending = null
    }

    private fun decide(): DatabaseState {
        val stored = DatabaseVersionReader(context, databaseName).read()
        storedVersion = stored
        return databaseState(
            storedVersion = stored,
            budget = MigrationCopyBudget.onlyWhenPending(context, stored, databaseName = databaseName),
        )
    }

    private fun pendingFromVersion(): Int? = (storedVersion ?: DatabaseVersionReader(context, databaseName).read())
        ?.takeIf { it < SklyankaDatabase.VERSION }

    /**
     * Снять копию или отказать.
     *
     * Место перепроверяется **перед самой копией**: между решением о состоянии и копией
     * его могло стать меньше, и тогда отказ обязан победить, а решение процесса — смениться
     * на [DatabaseState.NotEnoughSpace]. Иначе экран запуска показал бы «Сегодня», а первый
     * запрос к данным открыл бы базу и мигрировал её без копии — то, что норма запрещает.
     */
    private fun copyOrRefuse(fromVersion: Int): DatabaseUpgradeOutcome {
        val budget = MigrationCopyBudget.of(context, databaseName)
        if (!budget.fits) {
            decided = DatabaseState.NotEnoughSpace(budget)
            return DatabaseUpgradeOutcome.RefusedForSpace
        }
        return DatabaseUpgradeOutcome.CopyTaken(takeCopy(fromVersion))
    }

    private fun takeCopy(fromVersion: Int): PendingMigration {
        val copy = MigrationCopyStore(context, databaseName).take(fromVersion)
        val migration = PendingMigration(
            fromVersion = fromVersion,
            toVersion = SklyankaDatabase.VERSION,
            copyBytes = copy.length(),
            startedAt = time.now(),
        )
        pending = migration
        return migration
    }
}

/** Исход подготовки к открытию базы. */
sealed interface DatabaseUpgradeOutcome {

    /** Перехода схемы не будет: первая установка или база той же версии. */
    data object NoMigration : DatabaseUpgradeOutcome

    /** Копия снята, след ждёт записи. */
    data class CopyTaken(val migration: PendingMigration) : DatabaseUpgradeOutcome

    /** Места на копию не хватило: миграция не выполняется, схема не открывается. */
    data object RefusedForSpace : DatabaseUpgradeOutcome
}

/**
 * Предстоящая миграция: версии, размер копии и момент начала.
 *
 * Момент — из [TimeSource], а не из системных часов (НФТ-48): длительность миграции
 * в журнале обязана быть проверяемой.
 */
data class PendingMigration(val fromVersion: Int, val toVersion: Int, val copyBytes: Long, val startedAt: Instant)
