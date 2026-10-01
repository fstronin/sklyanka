package ru.stohastika.sklyanka.platform.reminder

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import ru.stohastika.sklyanka.domain.reminder.port.AlarmRegistry
import ru.stohastika.sklyanka.domain.reminder.port.ReminderMeasurement
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import java.time.Instant

/**
 * Механизм постановки будильников (SKL-85, НАП-5, НАП-42).
 *
 * Приём обязан идти системным будильником с show-намерением, служебные будильники —
 * тихим точным. Различить вызовы в `AlarmManager` иначе негде: на устройстве это видно
 * только по признаку в статус-баре, а его отсутствие в тесте не наблюдается.
 */
@RunWith(AndroidJUnit4::class)
class AlarmManagerGatewayTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val alarms = requireNotNull(context.getSystemService(AlarmManager::class.java))
    private val shadow = shadowOf(alarms)
    private val registry = FakeAlarmRegistry()

    private val at: Instant = Instant.parse("2026-09-26T05:00:00Z")

    /**
     * Снять будильники, оставшиеся от соседних проверок в общем снимке Robolectric.
     *
     * Соседние классы (например, стенд проб SKL-89) ставят будильники через настоящий
     * `AlarmManager`, и без очистки `scheduledAlarms.single()` увидел бы чужой будильник.
     */
    @Before
    fun clearScheduledAlarms() {
        alarms.cancelAll()
    }

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

    private class FakeAlarmRegistry : AlarmRegistry {
        val stored: MutableMap<String, Instant> = linkedMapOf()
        override suspend fun all(): Map<String, Instant> = stored.toMap()
        override suspend fun put(intakeId: String, at: Instant) {
            stored[intakeId] = at
        }

        override suspend fun remove(intakeId: String) {
            stored.remove(intakeId)
        }
    }

    private fun gateway(kind: AlarmKind, measurementAvailable: Boolean = true): AlarmManagerGateway =
        AlarmManagerGateway(
            context = context,
            registry = registry,
            strategy = EffectiveAlarmStrategy(
                settings = StoredSettings(ReminderSettingsValues(alarmStrategy = kind)),
                measurement = Measurement(available = measurementAvailable),
            ),
        )

    @Test
    fun `an intake alarm goes through the system alarm clock with a show intent`() = runTest {
        gateway(AlarmKind.ALARM_CLOCK).schedule("intake-1", at)

        val alarm = shadow.scheduledAlarms.single()
        assertEquals(at.toEpochMilli(), alarm.triggerAtTime)
        val clockInfo = requireNotNull(alarm.getAlarmClockInfo()) { "setAlarmClock must carry AlarmClockInfo" }
        assertEquals(at.toEpochMilli(), clockInfo.triggerTime)
        assertNotNull("the status bar entry must open the app", clockInfo.showIntent)
        assertEquals("the alarm must be armed", at, registry.stored["intake-1"])
    }

    @Test
    fun `the exact idle variant stays a quiet exact alarm`() = runTest {
        val gateway = gateway(AlarmKind.EXACT_IDLE)

        gateway.schedule("intake-1", at)

        val alarm = shadow.scheduledAlarms.single()
        assertEquals(at.toEpochMilli(), alarm.triggerAtTime)
        assertNull("a quiet alarm must not show a status bar entry", alarm.getAlarmClockInfo())
        assertTrue("it must still pierce Doze", alarm.isAllowWhileIdle)
        assertEquals(AlarmKind.EXACT_IDLE, gateway.alarmKind())
    }

    @Test
    fun `a normal build ignores the stored exact idle variant`() = runTest {
        val gateway = gateway(AlarmKind.EXACT_IDLE, measurementAvailable = false)

        gateway.schedule("intake-1", at)

        assertNotNull("a normal build always uses the alarm clock", shadow.scheduledAlarms.single().getAlarmClockInfo())
        assertEquals(AlarmKind.ALARM_CLOCK, gateway.alarmKind())
    }

    @Test
    fun `the anchor and the day boundary never show as an alarm clock`() = runTest {
        val gateway = gateway(AlarmKind.ALARM_CLOCK)

        gateway.scheduleAnchor(at)
        gateway.scheduleDayBoundary(at.plusSeconds(3600))

        assertEquals(2, shadow.scheduledAlarms.size)
        shadow.scheduledAlarms.forEach { alarm ->
            assertNull("a service alarm must stay quiet (НАП-42)", alarm.getAlarmClockInfo())
            assertTrue(alarm.isAllowWhileIdle)
        }
    }

    @Test
    fun `the acting mechanism is the one just used to arm the alarm`() = runTest {
        val gateway = gateway(AlarmKind.EXACT_IDLE)

        assertEquals(AlarmKind.EXACT_IDLE, gateway.alarmKind())
        gateway.schedule("intake-1", at)

        assertNull(shadow.scheduledAlarms.single().getAlarmClockInfo())
    }

    @Test
    fun `a repeated arming replaces the previous alarm instead of adding a second one`() = runTest {
        val gateway = gateway(AlarmKind.ALARM_CLOCK)

        gateway.schedule("intake-1", at)
        gateway.schedule("intake-1", at.plusSeconds(900), attempt = 1)

        assertEquals("one intake is one alarm address (НАП-9)", 1, shadow.scheduledAlarms.size)
        assertEquals(at.plusSeconds(900).toEpochMilli(), shadow.scheduledAlarms.single().triggerAtTime)
    }
}
