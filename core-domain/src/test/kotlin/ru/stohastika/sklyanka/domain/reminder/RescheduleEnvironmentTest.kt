package ru.stohastika.sklyanka.domain.reminder

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Запись среды измерения при пересборке окна (SKL-85).
 *
 * Журнал — журнал изменений: среда пишется при первой пересборке и при смене механизма
 * или устройства, а на неизменной среде молчит (по образцу SKL-47).
 */
class RescheduleEnvironmentTest {

    private val world = ReminderTestWorld()

    @Test
    fun `the first rebuild writes the environment with the acting mechanism`() = runTest {
        world.pending()

        world.reschedule(RescheduleReason.APP_START)

        val parsed = environmentEntry()
        assertEquals(AlarmKind.ALARM_CLOCK, parsed.alarmKind)
        assertEquals("HUAWEI", parsed.device.manufacturer)
        assertEquals("12", parsed.device.osRelease)
        assertTrue(parsed.device.batteryUnrestricted)
    }

    @Test
    fun `the rebuild reason is still recorded on every rebuild`() = runTest {
        world.pending()

        world.reschedule(RescheduleReason.APP_START)
        world.moveTo(ReminderTestWorld.AFTERNOON)
        world.reschedule(RescheduleReason.ANCHOR)

        assertEquals(2, world.journal.of(ReminderEvent.RESCHEDULED).size)
    }

    @Test
    fun `an unchanged environment does not add a second entry`() = runTest {
        world.pending()

        world.reschedule(RescheduleReason.APP_START)
        world.moveTo(ReminderTestWorld.AFTERNOON)
        world.reschedule(RescheduleReason.ANCHOR)

        assertEquals(1, world.journal.of(ReminderEvent.ENVIRONMENT).size)
    }

    @Test
    fun `changing the mechanism marks a new window`() = runTest {
        world.pending()
        world.reschedule(RescheduleReason.APP_START)

        world.alarms.kind = AlarmKind.EXACT_IDLE
        world.moveTo(ReminderTestWorld.AFTERNOON)
        world.reschedule(RescheduleReason.MANUAL)

        assertEquals(2, world.journal.of(ReminderEvent.ENVIRONMENT).size)
        assertEquals(AlarmKind.EXACT_IDLE, environmentEntry().alarmKind)
    }

    @Test
    fun `changing the device marks a new window`() = runTest {
        world.pending()
        world.reschedule(RescheduleReason.APP_START)

        world.environment.facts = world.environment.facts.copy(buildDisplay = "HUAWEI Pura 80 Ultra 15.0.2")
        world.moveTo(ReminderTestWorld.AFTERNOON)
        world.reschedule(RescheduleReason.MANUAL)

        assertEquals(2, world.journal.of(ReminderEvent.ENVIRONMENT).size)
        assertEquals("HUAWEI Pura 80 Ultra 15.0.2", environmentEntry().device.buildDisplay)
    }

    /** Запись среды — служебная нагрузка: разбор даёт её, всё остальное даёт `null`. */
    @Test
    fun `an environment record parses and the rebuild record does not`() = runTest {
        world.pending()
        world.reschedule(RescheduleReason.APP_START)

        val rescheduled = world.journal.of(ReminderEvent.RESCHEDULED).single()
        assertNull(ReminderEnvironment.parse(rescheduled.reason.orEmpty()))
    }

    private suspend fun environmentEntry(): ReminderEnvironment {
        val entry = world.journal.of(ReminderEvent.ENVIRONMENT).last()
        return requireNotNull(ReminderEnvironment.parse(entry.reason.orEmpty())) {
            "the environment record must carry a parseable payload"
        }
    }
}
