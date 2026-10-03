package ru.stohastika.sklyanka.reminder

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.stohastika.sklyanka.domain.reminder.AcknowledgeDeliveryCheckUseCase
import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckOutcome
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckReason
import ru.stohastika.sklyanka.domain.reminder.DirectBootAlarm
import ru.stohastika.sklyanka.domain.reminder.FinishDeliveryCheckOnEntryUseCase
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.port.AlarmGateway
import ru.stohastika.sklyanka.domain.reminder.port.DeliveryCheckAcknowledgements
import ru.stohastika.sklyanka.domain.reminder.port.ReminderJournal
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.time.Instant
import java.time.ZoneId

/**
 * Объявление результата самопроверки при входе (SKL-86, ОВ-108).
 *
 * Держатель — тонкая обвязка над доменом: истёкшая проверка закрывается, результат
 * объявляется один раз и не возвращается после подтверждения, в том числе после
 * перезапуска процесса (подтверждение живёт в порту, а не в памяти держателя).
 */
class DeliveryCheckAnnouncerTest {

    private val now: Instant = Instant.parse("2026-09-25T07:02:00Z")
    private val dueAt: Instant = Instant.parse("2026-09-25T07:01:30Z")

    private val journal = RecordingJournal()
    private val alarms = NoAlarms()
    private val acknowledgements = FakeAcknowledgements()
    private val time = FixedTimeSource(now, ZoneId.of("Europe/Moscow"))

    private val announcer = DeliveryCheckAnnouncer(
        finishCheck = FinishDeliveryCheckOnEntryUseCase(journal, alarms, time),
        acknowledgeCheck = AcknowledgeDeliveryCheckUseCase(acknowledgements),
        acknowledgements = acknowledgements,
    )

    @Test
    fun `no check means nothing to announce`() = runTest {
        announcer.refresh()

        assertNull(announcer.current.value)
    }

    @Test
    fun `a running check is not announced and is left alone`() = runTest {
        journal.record(
            ReminderLogEntry(
                occurredAt = now.minusSeconds(30),
                event = ReminderEvent.DELIVERY_CHECK,
                scheduledAt = now.plusSeconds(120),
                reason = DeliveryCheckReason.SCHEDULED,
            ),
        )

        announcer.refresh()

        assertNull(announcer.current.value)
        assertEquals(1, journal.entries.size)
    }

    @Test
    fun `an expired check is closed and announced as not delivered`() = runTest {
        journal.record(
            ReminderLogEntry(
                occurredAt = now.minusSeconds(120),
                event = ReminderEvent.DELIVERY_CHECK,
                scheduledAt = dueAt,
                reason = DeliveryCheckReason.SCHEDULED,
            ),
        )
        alarms.deliveryCheck = dueAt

        announcer.refresh()

        val announced = requireNotNull(announcer.current.value)
        assertEquals(DeliveryCheckOutcome.NOT_DELIVERED, announced.outcome)
        assertEquals(dueAt, announced.dueAt)
        assertNull("the late alarm must be taken away", alarms.deliveryCheck)
    }

    @Test
    fun `an uncompleted check is closed without a banner`() = runTest {
        journal.record(
            ReminderLogEntry(
                occurredAt = now.minusSeconds(30),
                event = ReminderEvent.DELIVERY_CHECK,
                scheduledAt = dueAt,
                reason = DeliveryCheckReason.NOT_COMPLETED,
            ),
        )

        announcer.refresh()

        assertNull(announcer.current.value)
    }

    @Test
    fun `an acknowledged result does not come back after a restart`() = runTest {
        journal.record(
            ReminderLogEntry(
                occurredAt = now.minusSeconds(30),
                event = ReminderEvent.DELIVERY_CHECK,
                scheduledAt = dueAt,
                deviationSeconds = 720,
                delivered = true,
                reason = DeliveryCheckReason.SHOWN,
            ),
        )
        announcer.refresh()
        assertEquals(DeliveryCheckOutcome.LATE, announcer.current.value?.outcome)

        announcer.acknowledge()
        // «Перезапуск»: подтверждение — в порту, а не в памяти держателя.
        announcer.refresh()

        assertNull(announcer.current.value)
        assertEquals(dueAt, acknowledgements.announcedAt)
    }

    @Test
    fun `a new check is announced after the previous one was acknowledged`() = runTest {
        acknowledgements.announcedAt = dueAt.minusSeconds(600)
        journal.record(
            ReminderLogEntry(
                occurredAt = now.minusSeconds(30),
                event = ReminderEvent.DELIVERY_CHECK,
                scheduledAt = dueAt,
                delivered = true,
                reason = DeliveryCheckReason.SHOWN,
            ),
        )

        announcer.refresh()

        assertEquals(DeliveryCheckOutcome.ON_TIME, announcer.current.value?.outcome)
    }
}

private class RecordingJournal : ReminderJournal {

    val entries: MutableList<ReminderLogEntry> = mutableListOf()

    override suspend fun record(entry: ReminderLogEntry) {
        entries += entry
    }

    override suspend fun recent(limit: Int): List<ReminderLogEntry> = entries.takeLast(limit).reversed()

    override suspend fun last(event: ReminderEvent): ReminderLogEntry? = entries.lastOrNull { it.event == event }

    override suspend fun firedIntakeIds(since: Instant): Set<String> = emptySet()

    override suspend fun storedBytes(): Long = 0

    override suspend fun deleteOlderThan(before: Instant): Int = 0

    override suspend fun deleteOldest(protectedSince: Instant, limit: Int): Int = 0
}

private class NoAlarms : AlarmGateway {

    var deliveryCheck: Instant? = null

    override suspend fun schedule(intakeId: String, at: Instant, attempt: Int) = Unit

    override suspend fun cancel(intakeId: String) = Unit

    override suspend fun syncMirror(placed: List<DirectBootAlarm>) = Unit

    override suspend fun scheduled(): Map<String, Instant> = emptyMap()

    override suspend fun scheduleAnchor(at: Instant) = Unit

    override suspend fun scheduleDayBoundary(at: Instant) = Unit

    override suspend fun scheduleDeliveryCheck(at: Instant) {
        deliveryCheck = at
    }

    override suspend fun cancelDeliveryCheck() {
        deliveryCheck = null
    }

    override fun canScheduleExact(): Boolean = true

    override suspend fun alarmKind(): AlarmKind = AlarmKind.ALARM_CLOCK
}

private class FakeAcknowledgements : DeliveryCheckAcknowledgements {

    var announcedAt: Instant? = null

    override suspend fun announced(): Instant? = announcedAt

    override suspend fun remember(dueAt: Instant) {
        announcedAt = dueAt
    }
}
