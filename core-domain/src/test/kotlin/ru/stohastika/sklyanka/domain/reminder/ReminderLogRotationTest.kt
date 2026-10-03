package ru.stohastika.sklyanka.domain.reminder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Нагрузка записи о ротации: `age=N;size=M` (ПМ-30).
 *
 * Формат живёт в `reason` обычной записи журнала и читается глазами в CSV, поэтому
 * проверяются и запись без нулевых порогов, и устойчивость разбора к чужой строке
 * (МГ-6: журнал более новой сборки не имеет права уронить чтение).
 */
class ReminderLogRotationTest {

    @Test
    fun `both thresholds are written down`() {
        assertEquals("age=3;size=5", ReminderLogRotation(removedByAge = 3, removedBySize = 5).encode())
    }

    @Test
    fun `a zero threshold is left out`() {
        assertEquals("age=3", ReminderLogRotation(removedByAge = 3, removedBySize = 0).encode())
        assertEquals("size=7", ReminderLogRotation(removedByAge = 0, removedBySize = 7).encode())
        assertEquals("", ReminderLogRotation(removedByAge = 0, removedBySize = 0).encode())
    }

    @Test
    fun `the payload survives the round trip`() {
        listOf(
            ReminderLogRotation(removedByAge = 3, removedBySize = 5),
            ReminderLogRotation(removedByAge = 3, removedBySize = 0),
            ReminderLogRotation(removedByAge = 0, removedBySize = 7),
        ).forEach { rotation ->
            assertEquals(rotation, ReminderLogRotation.parse(rotation.encode()))
        }
    }

    @Test
    fun `removed counts both thresholds`() {
        assertEquals(8, ReminderLogRotation(removedByAge = 3, removedBySize = 5).removed)
    }

    @Test
    fun `a foreign or broken payload is refused`() {
        assertNull(ReminderLogRotation.parse(null))
        assertNull(ReminderLogRotation.parse(""))
        assertNull(ReminderLogRotation.parse("garbage"))
        assertNull(ReminderLogRotation.parse("other=1"))
        assertNull(ReminderLogRotation.parse("age=many"))
        assertNull(ReminderLogRotation.parse("age=1;age=2"))
    }
}
