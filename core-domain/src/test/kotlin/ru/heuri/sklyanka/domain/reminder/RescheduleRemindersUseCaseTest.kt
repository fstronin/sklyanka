package ru.heuri.sklyanka.domain.reminder

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.heuri.sklyanka.domain.model.Fixtures
import ru.heuri.sklyanka.domain.model.IntakeStatus
import java.time.LocalDateTime

@DisplayName("Rebuilding the window is one idempotent operation with many reasons")
class RescheduleRemindersUseCaseTest {

    private val world = ReminderTestWorld()

    @Test
    fun `alarms are set for the next two days and for nothing beyond them`() = runTest {
        listOf(25, 26, 27, 28).forEach { day ->
            world.pending(at = LocalDateTime.of(2026, 9, day, 14, 0), id = "intake-$day")
        }

        val outcome = world.reschedule(RescheduleReason.APP_START)

        assertEquals(setOf("intake-25", "intake-26"), world.alarms.alarms.keys)
        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 25, 14, 0)), world.alarms.alarms["intake-25"])
        assertEquals(2, outcome.total)
        assertEquals(2, outcome.scheduled)
        assertEquals(0, outcome.cancelled)
    }

    @Test
    fun `an as-needed medication produces no alarm`() = runTest {
        world.usePrn()
        world.pending()

        world.reschedule(RescheduleReason.APP_START)

        assertTrue(world.alarms.alarms.isEmpty())
    }

    @Test
    fun `archiving removes the alarm of its intake and unarchiving brings it back`() = runTest {
        world.pending()
        world.reschedule(RescheduleReason.APP_START)
        assertEquals(1, world.alarms.alarms.size)

        world.moveTo(LocalDateTime.of(2026, 9, 25, 10, 0))
        world.archive()
        val afterArchive = world.reschedule(RescheduleReason.MEDICATION_ARCHIVED)

        assertTrue(world.alarms.alarms.isEmpty())
        assertEquals(1, afterArchive.cancelled)

        world.moveTo(LocalDateTime.of(2026, 9, 25, 11, 0))
        world.unarchive()
        world.reschedule(RescheduleReason.MEDICATION_UNARCHIVED)

        assertEquals(setOf(Fixtures.INTAKE_ID), world.alarms.alarms.keys)
    }

    @Test
    fun `a second rebuild over unchanged data neither cancels nor sets anything`() = runTest {
        world.pending()
        world.reschedule(RescheduleReason.APP_START)
        val callsAfterFirst = world.alarms.scheduleCalls

        world.moveTo(LocalDateTime.of(2026, 9, 25, 9, 1))
        val second = world.reschedule(RescheduleReason.ANCHOR)

        assertEquals(0, second.scheduled)
        assertEquals(0, second.cancelled)
        assertEquals(1, second.total)
        assertEquals(callsAfterFirst, world.alarms.scheduleCalls)
        assertEquals(0, world.alarms.cancelCalls)
        assertEquals(1, world.alarms.alarms.size)
    }

    @Test
    fun `changing the time of an intake moves its alarm`() = runTest {
        world.pending()
        world.reschedule(RescheduleReason.APP_START)

        world.moveTo(LocalDateTime.of(2026, 9, 25, 10, 0))
        world.intakes.put(world.stored().copy(scheduledAt = LocalDateTime.of(2026, 9, 25, 16, 0)))
        world.reschedule(RescheduleReason.SCHEDULE_EDITED)

        assertEquals(
            world.momentOf(LocalDateTime.of(2026, 9, 25, 16, 0)),
            world.alarms.alarms[Fixtures.INTAKE_ID],
        )
        assertEquals(1, world.alarms.alarms.size)
    }

    @Test
    fun `an orphan alarm of an intake that no longer exists is cancelled`() = runTest {
        world.alarms.alarms["ghost"] = world.momentOf(LocalDateTime.of(2026, 9, 25, 14, 0))
        world.pending()

        val outcome = world.reschedule(RescheduleReason.MANUAL)

        assertFalse(world.alarms.alarms.containsKey("ghost"))
        assertEquals(1, outcome.cancelled)
    }

    @Test
    fun `the queue does not run empty after a reminder has fired`() = runTest {
        listOf(25, 26, 27).forEach { day ->
            world.pending(at = LocalDateTime.of(2026, 9, day, 14, 0), id = "intake-$day")
        }
        world.reschedule(RescheduleReason.APP_START)

        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 0))
        world.fired(intakeId = "intake-25")
        world.take(id = "intake-25")
        val afterFiring = world.reschedule(RescheduleReason.ALARM_FIRED)

        assertEquals(setOf("intake-26", "intake-27"), world.alarms.alarms.keys)
        assertTrue(afterFiring.total > 0, "the queue must not be left empty (NFR-7)")
    }

    @Test
    fun `the anchor alarm is renewed on every rebuild`() = runTest {
        world.pending()

        world.reschedule(RescheduleReason.APP_START)

        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 26, 3, 17)), world.alarms.anchor)
    }

    @Test
    fun `an intake that expired while the device was off becomes missed and gets no alarm`() = runTest {
        world.pending(at = LocalDateTime.of(2026, 9, 25, 14, 0))

        world.moveTo(LocalDateTime.of(2026, 9, 26, 9, 0))
        world.reschedule(RescheduleReason.BOOT)

        assertEquals(IntakeStatus.MISSED, world.stored().status)
        assertTrue(world.alarms.alarms.isEmpty())
        // Ни одного нового уведомления: проход по просроченным их не порождает (НАП-34).
        assertTrue(world.notifications.shown.isEmpty())
    }

    @Test
    fun `the rebuild records its reason and its counters in the journal`() = runTest {
        world.pending()

        world.reschedule(RescheduleReason.TIMEZONE_CHANGED)

        val entry = world.journal.of(ReminderEvent.RESCHEDULED).single()
        assertEquals(RescheduleReason.TIMEZONE_CHANGED.code, entry.reason)
        assertEquals(0, entry.alarmsCancelled)
        assertEquals(1, entry.alarmsScheduled)
        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 25, 14, 0)), entry.scheduledAt)
    }

    @Test
    fun `a revoked right to exact alarms is reported instead of a silent downgrade`() = runTest {
        world.pending()
        world.alarms.exactAllowed = false

        val outcome = world.reschedule(RescheduleReason.APP_START)

        assertFalse(outcome.exactAlarmsAllowed)
        assertFalse(
            ReminderReliability(
                notificationsEnabled = outcome.notificationsEnabled,
                exactAlarmsAllowed = outcome.exactAlarmsAllowed,
            ).isReliable,
        )
        // Будильники всё равно поставлены: приложение не переходит на неточные,
        // но и не перестаёт напоминать (НАП-16, НАП-18).
        assertEquals(1, world.alarms.alarms.size)
    }

    @Test
    fun `the next reminder of the window is reported for the diagnostics screen`() = runTest {
        world.pending(at = LocalDateTime.of(2026, 9, 25, 20, 0), id = "evening")
        world.pending(at = LocalDateTime.of(2026, 9, 25, 14, 0), id = "afternoon")

        val outcome = world.reschedule(RescheduleReason.APP_START)

        assertEquals("afternoon", outcome.nextIntakeId)
        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 25, 14, 0)), outcome.nextAt)
    }

    @Test
    fun `an intake of a foreign profile is planned only with mirrored reminders`() = runTest {
        world.foreignMedication(mirrorReminders = false)

        world.reschedule(RescheduleReason.PROFILE_SET_CHANGED)
        assertTrue(world.alarms.alarms.isEmpty())

        world.foreignMedication(mirrorReminders = true)
        world.reschedule(RescheduleReason.PROFILE_SET_CHANGED)

        assertEquals(setOf(ReminderTestWorld.FOREIGN_INTAKE_ID), world.alarms.alarms.keys)
    }

    @Test
    fun `an empty plan reports no next reminder`() = runTest {
        val outcome = world.reschedule(RescheduleReason.APP_START)

        assertNull(outcome.nextAt)
        assertEquals(0, outcome.total)
    }
}

/**
 * Пересборка окна и перенесённые срабатывания.
 *
 * Отдельным классом: здесь проверяется не состав окна, а то, что пересборка **не трогает**
 * будильники, время которых человек или правило повтора уже увели вперёд. Ошибка здесь
 * незаметна на устройстве до первого откладывания, а стоит пропущенного приёма.
 */
@DisplayName("Rebuilding the window keeps the alarms that were deliberately moved forward")
class RescheduleKeepsDeferredAlarmsTest {

    private val world = ReminderTestWorld()

    @Test
    fun `a snoozed reminder survives the next rebuild`() = runTest {
        world.pending()
        world.reschedule(RescheduleReason.APP_START)

        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 1))
        world.snooze(Fixtures.INTAKE_ID, minutes = 15)
        val snoozedAt = world.alarms.alarms[Fixtures.INTAKE_ID]

        val outcome = world.reschedule(RescheduleReason.ALARM_FIRED)

        assertEquals(snoozedAt, world.alarms.alarms[Fixtures.INTAKE_ID])
        assertEquals(0, outcome.cancelled)
    }

    @Test
    fun `a pending persistent repeat survives the next rebuild`() = runTest {
        world.pending()
        world.settings.setPersistentEnabled(true)
        world.reschedule(RescheduleReason.APP_START)

        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 0))
        world.fired(Fixtures.INTAKE_ID)
        val repeatAt = world.alarms.repeats.single().second

        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 1))
        val outcome = world.reschedule(RescheduleReason.ALARM_FIRED)

        assertEquals(repeatAt, world.alarms.alarms[Fixtures.INTAKE_ID])
        assertEquals(0, outcome.cancelled)
    }

    @Test
    fun `a marked intake loses its moved alarm as well`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 1))
        world.snooze(Fixtures.INTAKE_ID, minutes = 15)

        world.take()
        val outcome = world.reschedule(RescheduleReason.ALARM_FIRED)

        assertTrue(world.alarms.alarms.isEmpty())
        assertEquals(1, outcome.cancelled)
    }

    @Test
    fun `an alarm that already fired and left nothing behind is cleaned up`() = runTest {
        world.pending()
        world.reschedule(RescheduleReason.APP_START)

        // Напоминание прозвучало, повторы выключены, человек ничего не нажал: будильник
        // отработал, и держать его в наборе больше незачем.
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 0))
        world.fired(Fixtures.INTAKE_ID)
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 5))
        val outcome = world.reschedule(RescheduleReason.ALARM_FIRED)

        assertTrue(world.alarms.alarms.isEmpty())
        assertEquals(1, outcome.cancelled)
        assertEquals(IntakeStatus.PENDING, world.stored().status)
    }
}
