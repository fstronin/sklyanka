package ru.stohastika.sklyanka.domain.reminder

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.intake.SweepReason
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import java.time.LocalDateTime

@DisplayName("Diagnostics answer why a reminder did not arrive")
class ReminderDiagnosticsTest {

    private val world = ReminderTestWorld()

    @Test
    fun `the last fired and the next planned reminder are both shown`() = runTest {
        world.pending()
        world.pending(at = LocalDateTime.of(2026, 9, 26, 14, 0), id = "tomorrow")
        world.reschedule(RescheduleReason.APP_START)

        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 0).plusSeconds(37))
        world.fired(Fixtures.INTAKE_ID)
        world.take()

        world.moveTo(LocalDateTime.of(2026, 9, 25, 15, 0))
        val snapshot = world.diagnostics()

        val shown = requireNotNull(snapshot.lastShown)
        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 25, 14, 0)), shown.scheduledAt)
        assertEquals(37L, shown.deviationSeconds)
        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 26, 14, 0)), snapshot.nextAt)
        assertEquals("tomorrow", snapshot.nextIntakeId)
        assertEquals(2, snapshot.totalScheduled)
    }

    @Test
    fun `the last delivery is a repeat when the repeat came last`() = runTest {
        // Повтор — тоже предъявление напоминания (НАП-37): экран обязан показать его,
        // а не оставаться на первичном показе — иначе разбор повторов врёт.
        world.pending()
        world.moveTo(ReminderTestWorld.AFTERNOON)
        world.fired(Fixtures.INTAKE_ID)
        world.moveTo(ReminderTestWorld.AFTERNOON.plusMinutes(15))
        world.fired(Fixtures.INTAKE_ID, attempt = 1)

        val shown = requireNotNull(world.diagnostics().lastShown)

        assertEquals(ReminderEvent.REPEATED, shown.event)
        assertEquals(world.momentOf(ReminderTestWorld.AFTERNOON), shown.scheduledAt)
        assertEquals(world.momentOf(ReminderTestWorld.AFTERNOON.plusMinutes(15)), shown.firedAt)
        assertEquals(900L, shown.deviationSeconds)
    }

    @Test
    fun `the time and the reason of the last rebuild are shown`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 10, 0))
        world.reschedule(RescheduleReason.SCHEDULE_EDITED)

        world.moveTo(LocalDateTime.of(2026, 9, 25, 10, 5))
        val snapshot = world.diagnostics()

        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 25, 10, 0)), snapshot.lastReschedule?.occurredAt)
        assertEquals(RescheduleReason.SCHEDULE_EDITED.code, snapshot.lastReschedule?.reason)
    }

    @Test
    fun `every permission of the table has its own line`() = runTest {
        world.permissions.states[ReminderPermission.EXACT_ALARMS] = PermissionState.DENIED
        world.permissions.states[ReminderPermission.DND_POLICY_ACCESS] = PermissionState.DENIED

        val snapshot = world.diagnostics()

        assertEquals(ReminderPermission.entries.toSet(), snapshot.permissions.keys)
        assertEquals(PermissionState.GRANTED, snapshot.permissions[ReminderPermission.NOTIFICATIONS])
        assertEquals(PermissionState.DENIED, snapshot.permissions[ReminderPermission.EXACT_ALARMS])
        assertEquals(PermissionState.DENIED, snapshot.permissions[ReminderPermission.DND_POLICY_ACCESS])
        // Автозапуск EMUI программно не читается: честный ответ — «проверьте вручную» (НАП-20).
        assertEquals(PermissionState.UNKNOWN, snapshot.permissions[ReminderPermission.AUTOSTART])
        assertEquals(ReminderWarning.EXACT_ALARMS_OFF, snapshot.reliability.warning)
    }

    @Test
    fun `the journal holds at least fifty records`() = runTest {
        assertTrue(BuildReminderDiagnosticsUseCase.JOURNAL_LIMIT >= 50)
        repeat(60) { index ->
            world.journal.record(
                ReminderLogEntry(
                    occurredAt = world.clock.now().plusSeconds(index.toLong()),
                    event = ReminderEvent.SHOWN,
                    intakeId = "intake-$index",
                ),
            )
        }

        val snapshot = world.diagnostics()

        assertEquals(BuildReminderDiagnosticsUseCase.JOURNAL_LIMIT, snapshot.journal.size)
        assertEquals("intake-59", snapshot.journal.first().intakeId)
    }

    @Test
    fun `the seven day on time summary is part of the snapshot`() = runTest {
        // Два срабатывания в пределах НАП-2, одно с опозданием и одно старше недели:
        // сводка обязана считаться по промежутку времени, а не по последним записям.
        listOf(0L to 10L, 0L to 30L, 0L to 600L).forEach { (offsetDays, deviation) ->
            world.journal.record(firing(days = offsetDays, deviation = deviation))
        }
        world.journal.record(firing(days = 8L, deviation = 5L))

        val summary = world.diagnostics().onTime

        assertEquals(3, summary.fired)
        assertEquals(2, summary.onTime)
        assertEquals(7L, summary.days)
    }

    /** Предъявленное напоминание: сводка «вовремя» считается по показу, а не по колбэку. */
    private fun firing(days: Long, deviation: Long) = ReminderLogEntry(
        occurredAt = world.clock.now().minusSeconds(days * SECONDS_IN_DAY),
        event = ReminderEvent.SHOWN,
        intakeId = "intake-$days-$deviation",
        deviationSeconds = deviation,
    )

    @Test
    fun `a plan that fired nothing is reported as a break`() = runTest {
        world.pending(at = LocalDateTime.of(2026, 9, 25, 12, 0))
        world.reschedule(RescheduleReason.APP_START)

        world.moveTo(LocalDateTime.of(2026, 9, 25, 18, 0))
        val snapshot = world.diagnostics()

        assertEquals(world.momentOf(LocalDateTime.of(2026, 9, 25, 12, 0)), snapshot.outage?.since)
    }

    @Test
    fun `an intake already swept into missed still proves the break`() = runTest {
        // Длинный перерыв: приложение остановили, окно приёма истекло, и проход
        // по просроченным перевёл приём в «пропущено без отметки» — раньше, чем признак
        // перерыва успели посчитать. Именно этот случай НАП-38 и описывает: человек
        // открывает приложение и обязан увидеть, с какого момента напоминания не шли.
        world.pending(at = LocalDateTime.of(2026, 9, 25, 12, 0))
        world.reschedule(RescheduleReason.APP_START)
        world.moveTo(LocalDateTime.of(2026, 9, 26, 9, 0))
        world.sweep(SweepReason.APP_START)

        val snapshot = world.diagnostics()

        assertEquals(IntakeStatus.MISSED, world.stored().status, "the sweep must have run first")
        assertEquals(
            world.momentOf(LocalDateTime.of(2026, 9, 25, 12, 0)),
            snapshot.outage?.since,
            "a break the person lived through cannot disappear because the sweep tidied up after it",
        )
    }

    @Test
    fun `an intake the person marked is not a break`() = runTest {
        // Отметка заранее: напоминание не приходило, потому что и не должно было.
        world.pending(at = LocalDateTime.of(2026, 9, 25, 12, 0))
        world.take()

        world.moveTo(LocalDateTime.of(2026, 9, 25, 18, 0))

        assertEquals(null, world.diagnostics().outage)
    }

    @Test
    fun `the journal carries identifiers only and no medical data`() = runTest {
        world.pending()
        world.secondMedication()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 0))
        world.fired(Fixtures.INTAKE_ID)
        world.take()
        world.reschedule(RescheduleReason.ALARM_FIRED)
        world.snooze(ReminderTestWorld.SECOND_INTAKE_ID, minutes = 15)

        val forbidden = listOf(
            world.medication().name,
            world.medication(ReminderTestWorld.SECOND_MEDICATION_ID).name,
            requireNotNull(world.medication().strength).toPlainString(),
            requireNotNull(world.profiles.stored[Fixtures.PROFILE_ID]).name,
        )
        val text = world.journal.entries.joinToString("\n")

        assertTrue(world.journal.entries.isNotEmpty(), "there must be something to check")
        forbidden.forEach { value ->
            assertTrue(!text.contains(value), "the reminder journal must not carry '$value' (NFR-19)")
        }
    }

    private companion object {
        const val SECONDS_IN_DAY = 86_400L
    }
}
