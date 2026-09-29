package ru.stohastika.sklyanka.domain.reminder

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Восстановление отложенных срабатываний после перезагрузки (SKL-35, ОВ-75, `TDD.md §9.3`).
 *
 * «Перезагрузка» воспроизводится [ReminderTestWorld.simulateReboot]: будильники системы
 * стираются, постоянные записи остаются. Пересборку зовут причиной `BOOT` — как её позвал бы
 * приёмник системного события; показывает или нет — решает уже срабатывание.
 */
@DisplayName("A rebuild restores the deferred reminders and drops the dead ones")
class RestoreDeferredRemindersTest {

    private val world = ReminderTestWorld()

    private suspend fun snoozeAtOnePastTwo() {
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 1))
        world.snooze(Fixtures.INTAKE_ID, minutes = 15)
    }

    @Test
    fun `a snoozed reminder comes back with its moment and show number`() = runTest {
        world.pending()
        snoozeAtOnePastTwo()
        world.simulateReboot()
        assertTrue(world.alarms.alarms.isEmpty(), "the reboot wipes the alarms")

        world.reschedule(RescheduleReason.BOOT)

        assertEquals(
            world.momentOf(LocalDateTime.of(2026, 9, 25, 14, 16)),
            world.alarms.alarms[Fixtures.INTAKE_ID],
        )
        assertEquals(0, world.alarms.attempts[Fixtures.INTAKE_ID])
        assertEquals(
            DeferredReason.RESTORED,
            world.journal.of(ReminderEvent.DEFERRED).single().reason,
        )
    }

    @Test
    fun `a pending persistent repeat comes back with its number`() = runTest {
        world.pending()
        world.settings.setPersistentEnabled(true)
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 0))
        world.fired(Fixtures.INTAKE_ID)
        world.simulateReboot()

        world.reschedule(RescheduleReason.BOOT)

        assertEquals(
            world.momentOf(LocalDateTime.of(2026, 9, 25, 14, 15)),
            world.alarms.alarms[Fixtures.INTAKE_ID],
        )
        assertEquals(1, world.alarms.attempts[Fixtures.INTAKE_ID], "the show number must survive the reboot")
    }

    @Test
    fun `a second rebuild over the same record neither arms nor writes again`() = runTest {
        world.pending()
        snoozeAtOnePastTwo()
        world.simulateReboot()
        world.reschedule(RescheduleReason.BOOT)
        val calls = world.alarms.scheduleCalls
        val restored = world.journal.of(ReminderEvent.DEFERRED).size

        world.reschedule(RescheduleReason.BOOT)

        assertEquals(calls, world.alarms.scheduleCalls, "the second rebuild must not arm the same alarm again")
        assertEquals(restored, world.journal.of(ReminderEvent.DEFERRED).size)
        assertEquals(
            world.momentOf(LocalDateTime.of(2026, 9, 25, 14, 16)),
            world.alarms.alarms[Fixtures.INTAKE_ID],
        )
    }

    /**
     * Включение в тот же день: момент повтора ушёл в прошлое, но окно приёма ещё открыто.
     *
     * Будильник ставится на прошедший момент — `setExactAndAllowWhileIdle` предъявляет
     * его сразу, и человек получает обещанное напоминание, как только телефон вернулся.
     * Плановое время приёма при этом не переписывается (НАП-31, решение ведущей сессии).
     */
    @Test
    fun `a deferred moment in the past is armed at once while the intake is still pending`() = runTest {
        world.pending()
        snoozeAtOnePastTwo()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 5))
        world.simulateReboot()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 15, 0))

        world.reschedule(RescheduleReason.BOOT)

        assertEquals(
            world.momentOf(LocalDateTime.of(2026, 9, 25, 14, 16)),
            world.alarms.alarms[Fixtures.INTAKE_ID],
            "the overdue moment is a point on the absolute scale, not thrown away",
        )
        // Система предъявляет прошедший будильник немедленно — здесь это доставка вручную.
        world.fired(Fixtures.INTAKE_ID)
        assertTrue(world.notifications.shown.isNotEmpty(), "the reminder must arrive when the phone is back")
        assertEquals(LocalDateTime.of(2026, 9, 25, 14, 0), world.stored().scheduledAt)
        assertEquals(IntakeStatus.PENDING, world.stored().status)
    }

    @Test
    fun `a deferred moment after midnight leaves the intake missed and drops the record`() = runTest {
        world.pending()
        snoozeAtOnePastTwo()
        world.moveTo(LocalDateTime.of(2026, 9, 26, 9, 0))
        world.simulateReboot()

        world.reschedule(RescheduleReason.BOOT)

        assertEquals(IntakeStatus.MISSED, world.stored().status)
        assertTrue(world.alarms.alarms.isEmpty(), "a missed intake gets no alarm")
        assertTrue(world.deferred.stored.isEmpty(), "the record goes with the intake")
        assertTrue(world.notifications.shown.isEmpty(), "the sweep must not wake anyone")
        assertEquals(DeferredReason.DROPPED, world.journal.of(ReminderEvent.DEFERRED).single().reason)
    }

    @Test
    fun `an archived medication drops its record`() = runTest {
        world.pending()
        snoozeAtOnePastTwo()
        world.archive()
        world.simulateReboot()

        world.reschedule(RescheduleReason.MEDICATION_ARCHIVED)

        assertTrue(world.deferred.stored.isEmpty())
        assertTrue(world.alarms.alarms.isEmpty())
    }

    @Test
    fun `an on-demand medication drops its record`() = runTest {
        world.pending()
        snoozeAtOnePastTwo()
        world.usePrn()
        world.simulateReboot()

        world.reschedule(RescheduleReason.SCHEDULE_EDITED)

        assertTrue(world.deferred.stored.isEmpty())
        assertTrue(world.alarms.alarms.isEmpty())
    }

    @Test
    fun `a foreign profile without mirrored reminders drops its record`() = runTest {
        world.foreignMedication(mirrorReminders = false)
        world.deferUntil(LocalDateTime.of(2026, 9, 25, 14, 16), id = ReminderTestWorld.FOREIGN_INTAKE_ID)

        world.reschedule(RescheduleReason.PROFILE_SET_CHANGED)

        assertTrue(world.deferred.stored.isEmpty())
        assertTrue(world.alarms.alarms.isEmpty())
    }

    @Test
    fun `a foreign profile with mirrored reminders gets its repeat back`() = runTest {
        world.foreignMedication(mirrorReminders = true)
        world.deferUntil(LocalDateTime.of(2026, 9, 25, 14, 16), id = ReminderTestWorld.FOREIGN_INTAKE_ID)

        world.reschedule(RescheduleReason.PROFILE_SET_CHANGED)

        assertEquals(
            world.momentOf(LocalDateTime.of(2026, 9, 25, 14, 16)),
            world.alarms.alarms[ReminderTestWorld.FOREIGN_INTAKE_ID],
        )
    }

    @Test
    fun `a marked intake drops its record`() = runTest {
        world.pending()
        snoozeAtOnePastTwo()
        world.take()
        world.simulateReboot()

        world.reschedule(RescheduleReason.BOOT)

        assertTrue(world.deferred.stored.isEmpty())
        assertTrue(world.alarms.alarms.isEmpty())
    }

    @Test
    fun `a record of an intake that no longer exists is dropped`() = runTest {
        world.deferUntil(LocalDateTime.of(2026, 9, 25, 14, 16), id = "ghost")

        world.reschedule(RescheduleReason.DATA_IMPORTED)

        assertTrue(world.deferred.stored.isEmpty())
        assertEquals(DeferredReason.DROPPED, world.journal.of(ReminderEvent.DEFERRED).single().reason)
    }

    /**
     * Перелёт не переносит и не дублирует момент повтора: он точка на абсолютной шкале.
     *
     * После смены пояса локальное время приёма может снова оказаться будущим, и тогда
     * для одного приёма есть два намерения; отложенное важнее окна, а второй пересборки
     * достаточно, чтобы доказать: лишнего будильника не появляется.
     */
    @Test
    fun `a timezone change keeps the same moment and arms one alarm`() = runTest {
        world.pending()
        snoozeAtOnePastTwo()
        val fireAt = requireNotNull(world.alarms.alarms[Fixtures.INTAKE_ID])
        world.simulateReboot()

        world.flyTo(ZoneId.of("Asia/Krasnoyarsk"))
        world.reschedule(RescheduleReason.TIMEZONE_CHANGED)
        val calls = world.alarms.scheduleCalls
        world.reschedule(RescheduleReason.TIMEZONE_CHANGED)

        assertEquals(fireAt, world.alarms.alarms[Fixtures.INTAKE_ID])
        assertEquals(1, world.alarms.alarms.size)
        assertEquals(calls, world.alarms.scheduleCalls, "the second rebuild finds the alarm already in place")
    }

    /**
     * Предел повторов считается по номеру показа и переживает перезагрузку (ОВ-27).
     *
     * Перезагрузка не сбрасывает счёт: состоявшиеся повторы остаются состоявшимися,
     * а третьего не приходит.
     */
    @Test
    fun `the repeat limit is counted by the record and survives the reboot`() = runTest {
        world.pending()
        world.settings.setPersistentEnabled(true)
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 0))
        world.fired(Fixtures.INTAKE_ID)
        world.simulateReboot()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 5))
        world.reschedule(RescheduleReason.BOOT)
        assertEquals(1, world.alarms.attempts[Fixtures.INTAKE_ID], "the first repeat survived")

        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 15))
        world.fired(Fixtures.INTAKE_ID, attempt = 1)
        assertEquals(2, world.alarms.attempts[Fixtures.INTAKE_ID], "the second repeat is planned")

        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 30))
        world.fired(Fixtures.INTAKE_ID, attempt = 2)

        assertTrue(world.deferred.stored.isEmpty(), "the limit has been reached")
        assertEquals(2, world.journal.of(ReminderEvent.REPEATED).size)
        assertTrue(world.stored().isPending, "the intake stays unmarked and expires by its own rule")
    }
}
