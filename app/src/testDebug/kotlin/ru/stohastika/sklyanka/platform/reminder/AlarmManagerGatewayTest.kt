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
import ru.stohastika.sklyanka.domain.reminder.DirectBootAlarm
import ru.stohastika.sklyanka.domain.reminder.DirectBootAlarms
import ru.stohastika.sklyanka.domain.reminder.DirectBootReplayPolicy
import ru.stohastika.sklyanka.domain.reminder.port.AlarmRegistry
import ru.stohastika.sklyanka.domain.reminder.port.DirectBootAlarmStore
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
    private val directBoot = FakeDirectBootAlarmStore()

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

    private class FakeDirectBootAlarmStore : DirectBootAlarmStore {
        val stored: MutableMap<String, DirectBootAlarm> = linkedMapOf()
        var failWrites: Boolean = false

        override suspend fun load(): DirectBootAlarms? =
            DirectBootAlarms(DirectBootReplayPolicy.FORMAT_VERSION, Instant.EPOCH, stored.values.toList())

        override suspend fun put(alarm: DirectBootAlarm) {
            failIfBroken()
            stored[alarm.intakeId] = alarm
        }

        override suspend fun remove(intakeId: String) {
            failIfBroken()
            stored.remove(intakeId)
        }

        override suspend fun replace(alarms: List<DirectBootAlarm>) {
            failIfBroken()
            stored.clear()
            alarms.forEach { stored[it.intakeId] = it }
        }

        private fun failIfBroken() {
            if (failWrites) throw java.io.IOException("the direct boot mirror is down")
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
            directBoot = directBoot,
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

    @Test
    fun `scheduling mirrors the alarm into the direct boot store`() = runTest {
        gateway(AlarmKind.ALARM_CLOCK).schedule("intake-1", at)

        assertEquals(DirectBootAlarm("intake-1", at, 0), directBoot.stored["intake-1"])
    }

    @Test
    fun `cancelling clears both the registry and the mirror`() = runTest {
        val gateway = gateway(AlarmKind.ALARM_CLOCK)
        gateway.schedule("intake-1", at)

        gateway.cancel("intake-1")

        assertNull(registry.stored["intake-1"])
        assertNull(directBoot.stored["intake-1"])
    }

    @Test
    fun `syncing the mirror keeps exactly the alarms standing in the system`() = runTest {
        val gateway = gateway(AlarmKind.ALARM_CLOCK)
        gateway.schedule("intake-1", at)
        gateway.schedule("intake-2", at.plusSeconds(60), attempt = 1)
        directBoot.stored.clear()

        gateway.syncMirror(
            listOf(
                DirectBootAlarm("intake-1", at, 0),
                DirectBootAlarm("intake-2", at.plusSeconds(60), 1),
                DirectBootAlarm("intake-absent", at, 0),
            ),
        )

        assertEquals(setOf("intake-1", "intake-2"), directBoot.stored.keys)
        assertEquals(DirectBootAlarm("intake-2", at.plusSeconds(60), 1), directBoot.stored["intake-2"])
    }

    @Test
    fun `a broken mirror does not fail arming, cancelling or the rebuild sync`() = runTest {
        val gateway = gateway(AlarmKind.ALARM_CLOCK)
        directBoot.failWrites = true

        gateway.schedule("intake-1", at)
        assertEquals(at, registry.stored["intake-1"])

        gateway.syncMirror(listOf(DirectBootAlarm("intake-1", at, 0)))

        gateway.cancel("intake-1")
        assertNull(registry.stored["intake-1"])
    }

    @Test
    fun `scheduled sees an alarm that exists only in the mirror`() = runTest {
        // Прямая постановка, как у LockedBootAlarmReplayer: зеркало знает будильник,
        // а CE-реестр — нет. Без объединения пересборка не увидела бы его и не сняла.
        val replayedAt = at.plusSeconds(7200)
        directBoot.stored["intake-replayed"] = DirectBootAlarm("intake-replayed", replayedAt, 0)
        alarms.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            replayedAt.toEpochMilli(),
            ReminderAlarms.pendingIntent(context, ReminderAlarms.intake(context, "intake-replayed", 0)),
        )

        val scheduled = gateway(AlarmKind.ALARM_CLOCK).scheduled()

        assertEquals(replayedAt, scheduled["intake-replayed"])
    }

    @Test
    fun `the delivery check goes the same combat path and stays out of the plan`() = runTest {
        // SKL-86: тестовое срабатывание обязано мерить настоящий механизм продукта,
        // а не отдельный тихий вызов, — иначе проверка доказывала бы не то, что обещает.
        val gateway = gateway(AlarmKind.ALARM_CLOCK)
        val checkAt = at.plusSeconds(90)

        gateway.scheduleDeliveryCheck(checkAt)

        val check = shadow.scheduledAlarms.single()
        assertEquals(checkAt.toEpochMilli(), check.triggerAtTime)
        assertNotNull("the check repeats the intake alarm mechanism", check.getAlarmClockInfo())
        assertTrue("the check is not an intake alarm", gateway.scheduled().isEmpty())
        assertNull("it must not enter the alarm registry", registry.stored[ReminderAlarms.ACTION_DELIVERY_CHECK])

        gateway.cancelDeliveryCheck()

        assertTrue("the check must be taken away", shadow.scheduledAlarms.isEmpty())
    }

    @Test
    fun `the delivery check stays quiet in the exact idle variant`() = runTest {
        val gateway = gateway(AlarmKind.EXACT_IDLE)

        gateway.scheduleDeliveryCheck(at)

        val check = shadow.scheduledAlarms.single()
        assertNull("the quiet variant stays quiet", check.getAlarmClockInfo())
        assertTrue("but still pierces Doze", check.isAllowWhileIdle)

        // Снимок Robolectric общий на классы стенда: тест не оставляет за собой будильник.
        gateway.cancelDeliveryCheck()
    }

    @Test
    fun `cancelling an absent delivery check is not an error`() = runTest {
        gateway(AlarmKind.ALARM_CLOCK).cancelDeliveryCheck()

        assertTrue(shadow.scheduledAlarms.isEmpty())
    }
}
