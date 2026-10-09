package ru.stohastika.sklyanka.data.room

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * Окно ежедневной проверки целостности (`ADR-0012` п. 4, SKL-94).
 *
 * Проверяется сравнение двух моментов, а не база: ошибка здесь стоит либо проверки на каждом
 * старте (приложение на EMUI стартует часто, а проверка читает базу целиком), либо проверки,
 * которой не было вовсе.
 */
class DatabaseMaintenanceRuleTest {

    private val now = Instant.parse("2026-09-25T08:00:00Z")

    @Test
    fun `without a previous check the check is due`() {
        assertTrue(integrityCheckIsDue(lastCheckAt = null, now = now))
    }

    @Test
    fun `a check from an hour ago is too fresh`() {
        assertFalse(integrityCheckIsDue(lastCheckAt = now.minus(Duration.ofHours(1)), now = now))
    }

    @Test
    fun `a check from a day ago is due again`() {
        assertTrue(integrityCheckIsDue(lastCheckAt = now.minus(Duration.ofHours(24)), now = now))
    }

    @Test
    fun `exactly one interval is the border that allows the check`() {
        val interval = Duration.ofHours(24)

        assertTrue(
            "exactly on the border the check is due: the rule forbids more often, not less often",
            integrityCheckIsDue(now.minus(interval), now, interval),
        )
        assertFalse(integrityCheckIsDue(now.minus(interval).plusSeconds(1), now, interval))
    }

    @Test
    fun `an old check is due even after a week`() {
        assertTrue(integrityCheckIsDue(lastCheckAt = now.minus(Duration.ofDays(7)), now = now))
    }
}
