package ru.heuri.sklyanka.reminder

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.heuri.sklyanka.domain.reminder.RescheduleReason

/**
 * Задача перепланирования: ограничения и полезная нагрузка (решение `ОВ-79`).
 *
 * Главная проверка здесь — **отсутствие сетевого ограничения**. Разрешение
 * `ACCESS_NETWORK_STATE` снято из слитого манифеста, и это безопасно ровно до тех пор,
 * пока ни одна задача не просит сеть: сетевое ограничение заставило бы библиотеку
 * обратиться к `ConnectivityManager`, а обращение без разрешения — отказ в рантайме,
 * то есть несостоявшееся восстановление будильников. Условие механическое, и проверять
 * его должен тест, а не память того, кто добавит следующую задачу.
 */
class ReminderWorkTest {

    @Test
    fun `no request asks for the network`() {
        RescheduleReason.entries.forEach { reason ->
            val constraints = ReminderWork.rescheduleRequest(reason).workSpec.constraints

            assertEquals(
                "a network constraint would reach ConnectivityManager without the permission",
                NetworkType.NOT_REQUIRED,
                constraints.requiredNetworkType,
            )
            assertFalse(constraints.requiresCharging())
            assertFalse(constraints.requiresBatteryNotLow())
            assertFalse(constraints.requiresStorageNotLow())
        }
    }

    @Test
    fun `the reason travels to the worker and comes back whole`() {
        RescheduleReason.entries.forEach { reason ->
            val data = ReminderWork.rescheduleRequest(reason).workSpec.input

            assertEquals(reason.code, data.getString(ReminderWork.KEY_REASON))
            assertEquals(reason, ReminderWork.reasonOf(data.getString(ReminderWork.KEY_REASON)))
        }
    }

    @Test
    fun `an unknown reason code leaves the worker nothing to do`() {
        assertNull(ReminderWork.reasonOf(null))
        assertNull(ReminderWork.reasonOf("whatever"))
    }

    @Test
    fun `a request is never delayed`() {
        // Ускоренная задача с отсрочкой запрещена библиотекой, а напоминание с отсрочкой
        // запрещено смыслом: восстанавливать план надо сейчас.
        assertEquals(0L, ReminderWork.rescheduleRequest(RescheduleReason.BOOT).workSpec.initialDelay)
    }

    @Test
    fun `the two queues are told apart`() {
        assertNotEquals(ReminderWork.RESCHEDULE, ReminderWork.DAY_BOUNDARY)
    }
}
