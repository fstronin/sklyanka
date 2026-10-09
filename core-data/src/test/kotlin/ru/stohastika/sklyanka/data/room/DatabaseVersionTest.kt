package ru.stohastika.sklyanka.data.room

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Правило состояния базы (Р-44, `ADR-0012` п. 7, SKL-128).
 *
 * Проверяется на JVM и на именованных версиях: ожидаемая версия — параметр, поэтому тест
 * не ломается от того, что схема получила очередной номер.
 */
class DatabaseVersionTest {

    @Test
    fun `a missing database is a first install, not a newer one`() {
        assertEquals(DatabaseState.Ready, databaseState(storedVersion = null, expectedVersion = 9))
    }

    @Test
    fun `an older database is migrated forward as usual`() {
        assertEquals(DatabaseState.Ready, databaseState(storedVersion = 8, expectedVersion = 9))
    }

    @Test
    fun `the expected version is ready`() {
        assertEquals(DatabaseState.Ready, databaseState(storedVersion = 9, expectedVersion = 9))
    }

    @Test
    fun `a newer database is a refusal`() {
        assertEquals(DatabaseState.Newer, databaseState(storedVersion = 10, expectedVersion = 9))
    }

    @Test
    fun `the shipped database version is the one the rule expects by default`() {
        // Сборка спрашивает правило без аргумента версии: перепутанное число здесь
        // означало бы отказ на своей же базе — или, что хуже, работу на чужой.
        assertEquals(DatabaseState.Ready, databaseState(SklyankaDatabase.VERSION))
    }

    @Test
    fun `a pending migration meets a refusal when the copy does not fit`() {
        // Место проверяется до миграции (ADR-0012 п. 3): не хватило на копию — миграция
        // не выполняется, и открывать базу этой сборке нельзя.
        val budget = MigrationCopyBudget(requiredBytes = 50, freeBytes = 10)

        assertEquals(
            DatabaseState.NotEnoughSpace(budget),
            databaseState(storedVersion = 8, expectedVersion = 9, budget = budget),
        )
    }

    @Test
    fun `a pending migration meets no refusal when the copy fits`() {
        val budget = MigrationCopyBudget(requiredBytes = 50, freeBytes = 50)

        assertEquals(DatabaseState.Ready, databaseState(storedVersion = 8, expectedVersion = 9, budget = budget))
    }

    @Test
    fun `space is nobody's business when no migration is pending`() {
        // База той же версии места не спрашивает: копировать нечего. Иначе отказ из-за
        // места запретил бы работу на исправной базе без единой причины.
        val budget = MigrationCopyBudget(requiredBytes = 5_000, freeBytes = 0)

        assertEquals(DatabaseState.Ready, databaseState(storedVersion = 9, expectedVersion = 9, budget = budget))
        assertEquals(DatabaseState.Ready, databaseState(storedVersion = null, expectedVersion = 9, budget = budget))
    }

    @Test
    fun `a newer database outranks the missing space`() {
        // База новее — отказ по другой причине и решается первым: мигрировать её вперёд
        // этой сборке нечем, и место здесь ничего не меняет.
        val budget = MigrationCopyBudget(requiredBytes = 5_000, freeBytes = 0)

        assertEquals(DatabaseState.Newer, databaseState(storedVersion = 10, expectedVersion = 9, budget = budget))
    }
}
