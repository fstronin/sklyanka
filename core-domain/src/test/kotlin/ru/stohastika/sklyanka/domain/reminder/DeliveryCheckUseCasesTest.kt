package ru.stohastika.sklyanka.domain.reminder

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * Сценарии самопроверки доставки (SKL-86, ПМ-21, ОВ-108).
 *
 * Проверка идёт тем же миром, что остальная подсистема напоминаний: журнал, будильники
 * и уведомления — фейки, решения — настоящие сценарии. Ни одного шага, которого
 * не было бы в продукте: «человек закрыл приложение» здесь значит ровно то, что
 * `AppForeground` отвечает «закрыто».
 */
@DisplayName("The delivery check survives a closed app and names every outcome")
class DeliveryCheckUseCasesTest {

    private val world = ReminderTestWorld()

    private suspend fun start(): DeliveryCheckState = world.startDeliveryCheck()

    private fun movedBy(duration: Duration) = world.moveTo(ReminderTestWorld.NOW.plus(duration))

    @Test
    fun `starting a check records it and schedules the test firing ninety seconds ahead`() = runTest {
        val state = start()

        val dueAt = world.momentOf(ReminderTestWorld.NOW).plus(DeliveryCheckRule.DELAY)
        assertEquals(DeliveryCheckState.Running(dueAt), state)
        assertEquals(dueAt, world.alarms.deliveryCheck)
        val recorded = world.journal.entries.single()
        assertEquals(ReminderEvent.DELIVERY_CHECK, recorded.event)
        assertEquals(DeliveryCheckReason.SCHEDULED, recorded.reason)
        assertEquals(dueAt, recorded.scheduledAt)
        assertNull(recorded.intakeId, "the check is not an intake")
        assertNull(recorded.profileId, "the check carries no medical data")
    }

    @Test
    fun `cancelling a running check takes the alarm away and leaves a record`() = runTest {
        start()
        movedBy(Duration.ofSeconds(30))

        world.cancelDeliveryCheck()

        assertNull(world.alarms.deliveryCheck, "the test alarm must be gone")
        assertTrue(world.alarms.deliveryCheckCancelCalls > 0, "the cancel must reach the system")
        val recorded = world.journal.entries.last()
        assertEquals(DeliveryCheckReason.CANCELLED, recorded.reason)
        assertEquals(ReminderEvent.DELIVERY_CHECK, recorded.event)
    }

    @Test
    fun `cancelling twice changes nothing and writes no second record`() = runTest {
        start()
        world.cancelDeliveryCheck()
        val afterFirst = world.journal.entries.size
        movedBy(Duration.ofSeconds(30))

        world.cancelDeliveryCheck()

        assertEquals(afterFirst, world.journal.entries.size, "a finished check cannot be cancelled again")
    }

    @Test
    fun `a presentation thirty seconds after the due moment is on time`() = runTest {
        start()
        movedBy(DeliveryCheckRule.DELAY.plusSeconds(30))

        val state = world.firedDeliveryCheck()

        assertEquals(
            DeliveryCheckState.Finished(
                outcome = DeliveryCheckOutcome.ON_TIME,
                dueAt = world.momentOf(ReminderTestWorld.NOW).plus(DeliveryCheckRule.DELAY),
                resolvedAt = world.clock.now(),
                lateMinutes = null,
            ),
            state,
        )
        assertEquals(1, world.notifications.deliveryChecks.size, "the test card must be asked to show once")
        assertEquals(2, world.firedRecords().size, "fired and shown must both be recorded")
        assertEquals(
            listOf(DeliveryCheckReason.SCHEDULED, DeliveryCheckReason.FIRED, DeliveryCheckReason.SHOWN),
            world.reasonCodes(),
        )
    }

    @Test
    fun `a presentation twelve minutes late is named with its minutes`() = runTest {
        start()
        movedBy(DeliveryCheckRule.DELAY.plusMinutes(12))

        val state = world.firedDeliveryCheck()

        val finished = state as DeliveryCheckState.Finished
        assertEquals(DeliveryCheckOutcome.LATE, finished.outcome)
        assertEquals(12L, finished.lateMinutes)
        assertEquals(720L, world.journal.entries.last().deviationSeconds)
    }

    @Test
    fun `a firing beyond the ordinary show threshold is still presented`() = runTest {
        // Порог SKL-88 к проверке не применяется: иначе «не дошло» не отличить
        // от «дошло слишком поздно» (ОВ-109).
        start()
        movedBy(DeliveryCheckRule.DELAY.plusMinutes(40))

        val state = world.firedDeliveryCheck()

        assertEquals(DeliveryCheckOutcome.LATE, (state as DeliveryCheckState.Finished).outcome)
        assertEquals(40L, state.lateMinutes)
        assertEquals(DeliveryCheckReason.SHOWN, world.journal.entries.last().reason)
    }

    @Test
    fun `a show that never reaches the shade is not delivered`() = runTest {
        world.notifications.deliveryCheckReachesTheShade = false
        start()
        movedBy(DeliveryCheckRule.DELAY)

        val state = world.firedDeliveryCheck()

        assertEquals(DeliveryCheckOutcome.NOT_DELIVERED, (state as DeliveryCheckState.Finished).outcome)
        assertEquals(DeliveryCheckReason.NOT_DELIVERED, world.journal.entries.last().reason)
        assertFalse(world.journal.entries.last().delivered ?: true)
    }

    @Test
    fun `a firing while the app is open is not completed and shows nothing`() = runTest {
        world.foreground.foreground = true
        start()
        movedBy(DeliveryCheckRule.DELAY)

        val state = world.firedDeliveryCheck()

        assertEquals(DeliveryCheckOutcome.NOT_COMPLETED, (state as DeliveryCheckState.Finished).outcome)
        assertEquals(DeliveryCheckReason.NOT_COMPLETED, world.journal.entries.last().reason)
        assertTrue(world.notifications.deliveryChecks.isEmpty(), "nothing is shown while the app is open")
    }

    @Test
    fun `entry before the due moment leaves the check running`() = runTest {
        start()
        movedBy(Duration.ofSeconds(30))

        val state = world.finishDeliveryCheck()

        assertEquals(
            DeliveryCheckState.Running(world.momentOf(ReminderTestWorld.NOW).plus(DeliveryCheckRule.DELAY)),
            state,
        )
        assertEquals(DeliveryCheckReason.SCHEDULED, world.journal.entries.last().reason)
        assertTrue(world.journal.entries.none { it.reason == DeliveryCheckReason.NOT_DELIVERED })
    }

    @Test
    fun `entry after the due moment without a firing names not delivered and takes the alarm away`() = runTest {
        start()
        movedBy(DeliveryCheckRule.DELAY)

        val state = world.finishDeliveryCheck()

        assertEquals(DeliveryCheckOutcome.NOT_DELIVERED, (state as DeliveryCheckState.Finished).outcome)
        assertNull(world.alarms.deliveryCheck, "the late alarm must not argue with the named outcome")
        assertEquals(DeliveryCheckReason.NOT_DELIVERED, world.journal.entries.last().reason)
    }

    @Test
    fun `a process death between firing and presentation is closed on entry as not delivered`() = runTest {
        start()
        val dueAt = world.alarms.deliveryCheck ?: error("the check must be scheduled")
        // Процесс умер после записи о срабатывании и до отметки предъявления: в журнале
        // остался `fired`, и вход обязан достроить исход.
        world.journal.record(
            ReminderLogEntry(
                occurredAt = dueAt,
                event = ReminderEvent.DELIVERY_CHECK,
                scheduledAt = dueAt,
                firedAt = dueAt,
                deviationSeconds = 0,
                delivered = false,
                reason = DeliveryCheckReason.FIRED,
            ),
        )
        movedBy(DeliveryCheckRule.DELAY.plusSeconds(5))

        val state = world.finishDeliveryCheck()

        assertEquals(DeliveryCheckOutcome.NOT_DELIVERED, (state as DeliveryCheckState.Finished).outcome)
        assertNull(world.alarms.deliveryCheck)
    }

    @Test
    fun `a late firing does not argue with the outcome already named`() = runTest {
        start()
        movedBy(DeliveryCheckRule.DELAY)
        world.finishDeliveryCheck()
        val recorded = world.journal.entries.size

        // Система отдаёт тестовый будильник с опозданием — срабатывания больше нет.
        movedBy(Duration.ofMinutes(5))
        val state = world.firedDeliveryCheck()

        assertEquals(DeliveryCheckOutcome.NOT_DELIVERED, (state as DeliveryCheckState.Finished).outcome)
        assertEquals(recorded, world.journal.entries.size, "no new result appears after the outcome is named")
    }

    @Test
    fun `an announced outcome is not announced again after a restart`() = runTest {
        start()
        movedBy(DeliveryCheckRule.DELAY.plusSeconds(30))
        val finished = world.firedDeliveryCheck() as DeliveryCheckState.Finished
        assertEquals(finished, DeliveryCheckRule.announcementOf(finished, world.acknowledgements.announced()))

        world.acknowledgeDeliveryCheck(finished.dueAt)
        // «Перезапуск»: подтверждение живёт в хранилище, а не в памяти держателя баннера.
        val afterRestart = world.finishDeliveryCheck()

        assertNull(
            DeliveryCheckRule.announcementOf(afterRestart, world.acknowledgements.announced()),
            "the same result must not be announced twice",
        )
    }

    private fun ReminderTestWorld.firedRecords(): List<ReminderLogEntry> =
        journal.entries.filter { it.reason == DeliveryCheckReason.FIRED || it.reason == DeliveryCheckReason.SHOWN }

    private fun ReminderTestWorld.reasonCodes(): List<String> = journal.entries.mapNotNull { it.reason }
}
