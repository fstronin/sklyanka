package ru.stohastika.sklyanka.platform.reminder

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import ru.stohastika.sklyanka.domain.reminder.DirectBootAlarm
import ru.stohastika.sklyanka.domain.reminder.DirectBootAlarms
import ru.stohastika.sklyanka.domain.reminder.DirectBootReplayDecision
import ru.stohastika.sklyanka.domain.reminder.DirectBootReplayPolicy
import ru.stohastika.sklyanka.domain.reminder.DirectBootTrace
import ru.stohastika.sklyanka.domain.reminder.DirectBootTraceReason
import ru.stohastika.sklyanka.domain.reminder.port.DirectBootAlarmStore
import ru.stohastika.sklyanka.domain.reminder.port.DirectBootTraceStore
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant

/**
 * Восстановление будильников по снимку до разблокировки (SKL-87).
 *
 * Проверяется, что ставятся только будущие записи, адрес совпадает с планировщиком,
 * а отказ (устаревший/отсутствующий снимок, нет права на точные будильники) деградирует
 * явно — следом в очереди, а не молча.
 */
@RunWith(AndroidJUnit4::class)
class LockedBootAlarmReplayerTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val alarms = requireNotNull(context.getSystemService(AlarmManager::class.java))
    private val shadow = shadowOf(alarms)

    private val now: Instant = Instant.parse("2026-09-27T02:05:00Z")

    private class FakeStore(var snapshot: DirectBootAlarms?) : DirectBootAlarmStore {
        override suspend fun load(): DirectBootAlarms? = snapshot
        override suspend fun put(alarm: DirectBootAlarm) = Unit
        override suspend fun remove(intakeId: String) = Unit
        override suspend fun replace(alarms: List<DirectBootAlarm>) = Unit
    }

    private class FakeTraces : DirectBootTraceStore {
        val appended = mutableListOf<DirectBootTrace>()
        override suspend fun append(trace: DirectBootTrace) {
            appended += trace
        }

        override suspend fun drain(): List<DirectBootTrace> = appended.toList()
    }

    private fun replayer(store: DirectBootAlarmStore, traces: DirectBootTraceStore, at: Instant = now) =
        LockedBootAlarmReplayer(context, store, traces, FixedTimeSource(at))

    private fun snapshot(updatedAt: Instant = now, alarms: List<DirectBootAlarm>) =
        DirectBootAlarms(DirectBootReplayPolicy.FORMAT_VERSION, updatedAt, alarms)

    @Before
    fun resetAlarmState() {
        alarms.cancelAll()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    /**
     * Будильники приёмов: восстановитель ставит только их.
     *
     * `SklyankaApp` при создании пересобирает план в фоновом потоке и ставит свой якорь
     * и границу суток — настоящее поведение продукта. Проверка считает будильники своего
     * адреса, а не весь список Robolectric, иначе зависела бы от гонки с фоном
     * (`ProbeStandTest` — тот же приём).
     */
    @Suppress("DEPRECATION")
    private fun intakeAlarms(): List<ShadowAlarmManager.ScheduledAlarm> = shadow.scheduledAlarms.filter { alarm ->
        alarm.operation?.let { shadowOf(it).savedIntent }?.action == ReminderAlarms.ACTION_INTAKE
    }

    @Test
    fun `only future alarms are placed and the trace counts skipped ones`() = runTest {
        val future = DirectBootAlarm("intake-future", now.plusSeconds(3600), 0)
        val past = DirectBootAlarm("intake-past", now.minusSeconds(1), 0)
        val traces = FakeTraces()

        replayer(FakeStore(snapshot(alarms = listOf(future, past))), traces).replay()

        assertEquals(1, intakeAlarms().size)
        val trace = traces.appended.single()
        assertEquals(1, trace.replayed)
        assertEquals(1, trace.skipped)
        assertEquals(0, trace.failed)
        assertEquals(DirectBootTraceReason.REPLAYED.code, trace.reason)
    }

    @Test
    fun `the replayed alarm sits at the planner address`() = runTest {
        val future = DirectBootAlarm("intake-future", now.plusSeconds(3600), 0)

        replayer(FakeStore(snapshot(alarms = listOf(future))), FakeTraces()).replay()

        // Проба планировщика тем же адресом находит будильник: пересборка его заменит, а не
        // плодит второй (НАП-9).
        assertNotNull(
            ReminderAlarms.existing(context, ReminderAlarms.intake(context, "intake-future", 0)),
        )
    }

    @Test
    fun `a stale snapshot places nothing and leaves a stale trace`() = runTest {
        val stale =
            snapshot(updatedAt = now.minus(DirectBootReplayPolicy.MAX_AGE).minusSeconds(1), alarms = emptyList())
        val traces = FakeTraces()

        val decision = replayer(FakeStore(stale), traces).replay()

        assertEquals(DirectBootReplayDecision.Skip(DirectBootTraceReason.STALE), decision)
        assertTrue(intakeAlarms().isEmpty())
        assertEquals(DirectBootTraceReason.STALE.code, traces.appended.single().reason)
    }

    @Test
    fun `a missing snapshot leaves a missing trace`() = runTest {
        val traces = FakeTraces()

        val decision = replayer(FakeStore(null), traces).replay()

        assertEquals(DirectBootReplayDecision.Skip(DirectBootTraceReason.MISSING), decision)
        assertEquals(DirectBootTraceReason.MISSING.code, traces.appended.single().reason)
    }

    @Test
    fun `denied exact alarms place nothing and leave an exact denied trace`() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val future = DirectBootAlarm("intake-future", now.plusSeconds(3600), 0)
        val traces = FakeTraces()

        val decision = replayer(FakeStore(snapshot(alarms = listOf(future))), traces).replay()

        assertEquals(DirectBootReplayDecision.Skip(DirectBootTraceReason.EXACT_DENIED), decision)
        assertTrue(intakeAlarms().isEmpty())
        assertEquals(DirectBootTraceReason.EXACT_DENIED.code, traces.appended.single().reason)
    }
}
