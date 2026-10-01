package ru.stohastika.sklyanka.debug

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import ru.stohastika.sklyanka.R
import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import ru.stohastika.sklyanka.domain.reminder.port.AlarmRegistry
import ru.stohastika.sklyanka.domain.reminder.port.ReminderMeasurement
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettings
import ru.stohastika.sklyanka.domain.reminder.port.ReminderSettingsValues
import ru.stohastika.sklyanka.platform.reminder.AlarmManagerGateway
import ru.stohastika.sklyanka.platform.reminder.EffectiveAlarmStrategy
import java.time.Instant

/**
 * Измерительный пробник (SKL-89).
 *
 * Проверяется то, что иначе видно только на устройстве: пробы ставятся реальным
 * `AlarmManagerGateway` (тем же вызовом, что продуктовые напоминания), формат строки
 * лога держится машинным контрактом со скриптом, уведомление уходит в отдельный
 * тестовый канал, а debug-манифест подписан на действия стенда.
 */
@RunWith(AndroidJUnit4::class)
class ProbeStandTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val alarms = requireNotNull(context.getSystemService(AlarmManager::class.java))
    private val shadow = shadowOf(alarms)

    private val now: Instant = Instant.parse("2026-10-01T09:00:00Z")

    /**
     * Только пробные будильники.
     *
     * `SklyankaApp` при создании пересобирает план в фоновом потоке и ставит свои
     * будильники — настоящее поведение продукта. Тест стенда не имеет права зависеть
     * от того, успел ли старт приложения до его тела, поэтому считаются будильники
     * с адресом пробы, а не весь список.
     */
    @Suppress("DEPRECATION")
    private fun probeAlarms(): List<ShadowAlarmManager.ScheduledAlarm> = shadow.scheduledAlarms.filter { alarm ->
        val intent = alarm.operation?.let { shadowOf(it).savedIntent }
        intent?.action == ProbeProtocol.ACTION_FIRE
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
        override suspend fun all(): Map<String, Instant> = emptyMap()
        override suspend fun put(intakeId: String, at: Instant) = Unit
        override suspend fun remove(intakeId: String) = Unit
    }

    private fun gateway(kind: AlarmKind, measurementAvailable: Boolean = true): AlarmManagerGateway =
        AlarmManagerGateway(
            context = context,
            registry = FakeAlarmRegistry(),
            strategy = EffectiveAlarmStrategy(
                settings = StoredSettings(ReminderSettingsValues(alarmStrategy = kind)),
                measurement = Measurement(available = measurementAvailable),
            ),
        )

    private fun pendingFor(index: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        index,
        Intent(context, ProbeFireReceiver::class.java)
            .setAction(ProbeProtocol.ACTION_FIRE)
            .setData(Uri.parse("sklyanka://probe/$index")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @Test
    fun `probe instants are offsets in minutes from the scheduling moment`() {
        val moments = ProbeProtocol.instants(now, listOf(2, 5, 10))

        assertEquals(
            listOf(now.plusSeconds(120), now.plusSeconds(300), now.plusSeconds(600)),
            moments,
        )
    }

    @Test
    fun `the acting strategy is parsed strictly and garbage is rejected`() {
        assertEquals(AlarmKind.ALARM_CLOCK, ProbeProtocol.parseStrategy("alarm_clock"))
        assertEquals(AlarmKind.EXACT_IDLE, ProbeProtocol.parseStrategy("exact_idle"))
        assertNull(ProbeProtocol.parseStrategy("garbage"))
        assertNull(ProbeProtocol.parseStrategy(null))
    }

    @Test
    fun `the log lines keep the contract the stand script parses`() {
        assertEquals(
            "probe=1;event=scheduled;scheduled_at=1759310000000;strategy=alarm_clock;placed=true",
            ProbeProtocol.scheduledLine(1, Instant.ofEpochMilli(1759310000000), AlarmKind.ALARM_CLOCK, true),
        )
        assertEquals(
            "probe=2;event=fired;scheduled_at=1759310000000;fired_at=1759310005000;" +
                "deviation_seconds=5;strategy=exact_idle",
            ProbeProtocol.firedLine(
                2,
                Instant.ofEpochMilli(1759310000000),
                Instant.ofEpochMilli(1759310005000),
                AlarmKind.EXACT_IDLE,
            ),
        )
        assertEquals("strategy_set=exact_idle", ProbeProtocol.strategySetLine(AlarmKind.EXACT_IDLE))
    }

    @Test
    fun `probes are placed by the real gateway with the system alarm clock`() = runTest {
        val placements = ProbePlan.place(gateway(AlarmKind.ALARM_CLOCK), now, listOf(2, 5)) { index, _, _ ->
            pendingFor(index)
        }

        assertEquals(listOf(now.plusSeconds(120), now.plusSeconds(300)), placements.map { it.at })
        assertTrue(placements.all { it.kind == AlarmKind.ALARM_CLOCK && it.placed })
        val armed = probeAlarms()
        assertEquals(2, armed.size)
        armed.forEach { alarm ->
            assertNotNull("a probe must be a system alarm clock", alarm.getAlarmClockInfo())
        }
    }

    @Test
    fun `the exact idle variant keeps probes quiet`() = runTest {
        val placements = ProbePlan.place(gateway(AlarmKind.EXACT_IDLE), now, listOf(2)) { index, _, _ ->
            pendingFor(index)
        }

        assertEquals(AlarmKind.EXACT_IDLE, placements.single().kind)
        val alarm = probeAlarms().single()
        assertNull("a quiet probe must not show a status bar entry", alarm.getAlarmClockInfo())
        assertTrue("it must still pierce Doze", alarm.isAllowWhileIdle)
    }

    @Test
    fun `a measurement-off build places probes as system alarm clocks anyway`() = runTest {
        val placements = ProbePlan.place(
            gateway(AlarmKind.EXACT_IDLE, measurementAvailable = false),
            now,
            listOf(2),
        ) { index, _, _ ->
            pendingFor(index)
        }

        assertEquals(AlarmKind.ALARM_CLOCK, placements.single().kind)
        assertNotNull(probeAlarms().single().getAlarmClockInfo())
    }

    @Test
    fun `a fired probe leaves a neutral notification in its own channel`() {
        ProbeNotifications.show(context, probe = 2)

        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        val channel = requireNotNull(manager.notificationChannels.firstOrNull { it.id == ProbeProtocol.CHANNEL_ID })
        assertEquals(context.getString(R.string.debug_probe_channel_name), channel.name)
        assertEquals(1, shadowOf(manager).size())
    }

    @Test
    fun `the debug manifest subscribes the control receiver to the stand actions`() {
        listOf(ProbeProtocol.ACTION_SCHEDULE, ProbeProtocol.ACTION_SET_STRATEGY).forEach { action ->
            val receivers = context.packageManager.queryBroadcastReceivers(Intent(action), 0)
            assertTrue(
                "ProbeControlReceiver must be subscribed to $action",
                receivers.any { it.activityInfo?.name == ProbeControlReceiver::class.java.name },
            )
        }
    }
}
