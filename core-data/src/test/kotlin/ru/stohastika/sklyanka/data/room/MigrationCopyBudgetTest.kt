package ru.stohastika.sklyanka.data.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Бюджет копии перед миграцией (`ADR-0012` п. 3, SKL-94).
 *
 * Проверяется арифметика решения, а не чтение файлов: числа недостающих мегабайт человек
 * видит на экране, и ошибка округления здесь — обещание, которого место не исполнит.
 */
class MigrationCopyBudgetTest {

    @Test
    fun `exactly the required size fits`() {
        val budget = MigrationCopyBudget(requiredBytes = 1_000, freeBytes = 1_000)

        assertTrue("exactly the copy size must fit", budget.fits)
        assertEquals(0, budget.missingBytes)
    }

    @Test
    fun `one byte short is a refusal, not a rounding`() {
        val budget = MigrationCopyBudget(requiredBytes = 1_001, freeBytes = 1_000)

        assertFalse(budget.fits)
        assertEquals(1, budget.missingBytes)
        assertEquals("less than a megabyte short is still named as a whole one", 1, budget.missingMegabytes)
    }

    @Test
    fun `missing megabytes round up`() {
        val megabyte = 1024L * 1024L
        val budget = MigrationCopyBudget(requiredBytes = 10 * megabyte + 1, freeBytes = 0)

        assertEquals("ten megabytes and one byte must be named as eleven", 11, budget.missingMegabytes)
    }

    @Test
    fun `no free space means the whole copy does not fit`() {
        val budget = MigrationCopyBudget(requiredBytes = 50_000, freeBytes = 0)

        assertFalse(budget.fits)
        assertEquals(50_000, budget.missingBytes)
    }
}
