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
}
