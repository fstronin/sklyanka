package ru.stohastika.sklyanka.platform.reminder

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import ru.stohastika.sklyanka.domain.reminder.port.ReminderMeasurement
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues

/**
 * Действующий механизм будильников приёмов (SKL-85).
 *
 * Ключевое свойство обычной сборки: сохранённое значение инертно. Значение могло остаться
 * от измерительной сборки пилота, и оно не имеет права влиять на планирование (ПМ-20).
 */
class EffectiveAlarmStrategyTest {

    private class StoredSettings(var values: ReminderSettingsValues) : ReminderSettings {
        override suspend fun read(): ReminderSettingsValues = values
        override suspend fun setPersistentEnabled(enabled: Boolean) = Unit
        override suspend fun setSnoozeMinutes(minutes: Int) = Unit
        override suspend fun setAlarmStrategy(kind: AlarmKind) {
            values = values.copy(alarmStrategy = kind)
        }
    }

    private class Measurement(var available: Boolean) : ReminderMeasurement {
        override fun available(): Boolean = available
    }

    @Test
    fun `a normal build ignores a stored exact idle and uses the alarm clock`() = runTest {
        val strategy = EffectiveAlarmStrategy(
            settings = StoredSettings(ReminderSettingsValues(alarmStrategy = AlarmKind.EXACT_IDLE)),
            measurement = Measurement(available = false),
        )

        assertEquals(AlarmKind.ALARM_CLOCK, strategy.kind())
    }

    @Test
    fun `the measuring build follows the chosen variant`() = runTest {
        val strategy = EffectiveAlarmStrategy(
            settings = StoredSettings(ReminderSettingsValues(alarmStrategy = AlarmKind.EXACT_IDLE)),
            measurement = Measurement(available = true),
        )

        assertEquals(AlarmKind.EXACT_IDLE, strategy.kind())
    }

    @Test
    fun `the reliable mechanism is the default of the measuring build`() = runTest {
        val strategy = EffectiveAlarmStrategy(
            settings = StoredSettings(ReminderSettingsValues()),
            measurement = Measurement(available = true),
        )

        assertEquals(AlarmKind.ALARM_CLOCK, strategy.kind())
    }
}
