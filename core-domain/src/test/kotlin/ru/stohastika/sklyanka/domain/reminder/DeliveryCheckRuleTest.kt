package ru.stohastika.sklyanka.domain.reminder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import java.time.Instant

/**
 * Правило самопроверки доставки (SKL-86, ОВ-108).
 *
 * Границы «вовремя» проверяются числами НАП-2, а не «примерно двумя минутами»: 120 секунд
 * входят, 121 — нет, и проверка меряет опоздание тем же правилом, что боевое напоминание.
 * Состояние читается по последней записи журнала, и каждая ветка подвида названа отдельно.
 */
@DisplayName("The delivery check rule reads its state from the journal")
class DeliveryCheckRuleTest {

    private val dueAt: Instant = Instant.parse("2026-09-25T07:01:30Z")

    private fun entry(reason: String, deviation: Long? = null) = ReminderLogEntry(
        occurredAt = dueAt.plusSeconds(30),
        event = ReminderEvent.DELIVERY_CHECK,
        scheduledAt = dueAt,
        firedAt = null,
        deviationSeconds = deviation,
        reason = reason,
    )

    @Test
    fun `a scheduled check is running until its moment`() {
        val state = DeliveryCheckRule.stateOf(entry(DeliveryCheckReason.SCHEDULED))

        assertEquals(DeliveryCheckState.Running(dueAt), state)
    }

    @Test
    fun `a fired check without a presentation is still running`() {
        // Процесс мог умереть между вызовом показа и отметкой предъявления: решение
        // достроит вход, а до него проверка не завершена (НАП-4).
        val state = DeliveryCheckRule.stateOf(entry(DeliveryCheckReason.FIRED, deviation = 30))

        assertEquals(DeliveryCheckState.Running(dueAt), state)
    }

    @Test
    fun `a shown check is finished with its outcome and minutes`() {
        val state = DeliveryCheckRule.stateOf(entry(DeliveryCheckReason.SHOWN, deviation = 720))

        assertEquals(
            DeliveryCheckState.Finished(
                outcome = DeliveryCheckOutcome.LATE,
                dueAt = dueAt,
                resolvedAt = dueAt.plusSeconds(30),
                lateMinutes = 12,
            ),
            state,
        )
    }

    @Test
    fun `an on time presentation carries no minutes`() {
        val state = DeliveryCheckRule.stateOf(entry(DeliveryCheckReason.SHOWN, deviation = 30))

        assertEquals(DeliveryCheckOutcome.ON_TIME, (state as DeliveryCheckState.Finished).outcome)
        assertNull(state.lateMinutes)
    }

    @Test
    fun `a check without a firing is not delivered`() {
        val state = DeliveryCheckRule.stateOf(entry(DeliveryCheckReason.NOT_DELIVERED))

        assertEquals(DeliveryCheckOutcome.NOT_DELIVERED, (state as DeliveryCheckState.Finished).outcome)
    }

    @Test
    fun `a check with the app open is not completed`() {
        val state = DeliveryCheckRule.stateOf(entry(DeliveryCheckReason.NOT_COMPLETED))

        assertEquals(DeliveryCheckOutcome.NOT_COMPLETED, (state as DeliveryCheckState.Finished).outcome)
    }

    @Test
    fun `a cancelled check leaves no state behind`() {
        val state = DeliveryCheckRule.stateOf(entry(DeliveryCheckReason.CANCELLED))

        assertEquals(DeliveryCheckState.None, state)
    }

    @Test
    fun `an unknown reason reads as no check at all`() {
        // Запись, оставленная более новой сборкой, не имеет права сломать экран (МГ-6).
        assertEquals(DeliveryCheckState.None, DeliveryCheckRule.stateOf(entry("teleported")))
    }

    @Test
    fun `no entry means no check`() {
        assertAll(
            { assertEquals(DeliveryCheckState.None, DeliveryCheckRule.stateOf(null)) },
            {
                assertEquals(
                    DeliveryCheckState.None,
                    DeliveryCheckRule.stateOf(
                        ReminderLogEntry(occurredAt = dueAt, event = ReminderEvent.SHOWN, deviationSeconds = 0),
                    ),
                )
            },
        )
    }

    @Test
    fun `the on time boundary is the one the requirement names`() {
        assertAll(
            { assertEquals(DeliveryCheckOutcome.ON_TIME, DeliveryCheckRule.outcomeOf(0)) },
            { assertEquals(DeliveryCheckOutcome.ON_TIME, DeliveryCheckRule.outcomeOf(120)) },
            { assertEquals(DeliveryCheckOutcome.LATE, DeliveryCheckRule.outcomeOf(121)) },
        )
    }

    @Test
    fun `minutes are whole minutes of the deviation`() {
        assertAll(
            { assertEquals(0, DeliveryCheckRule.lateMinutes(59)) },
            { assertEquals(1, DeliveryCheckRule.lateMinutes(61)) },
            { assertEquals(12, DeliveryCheckRule.lateMinutes(720)) },
        )
    }

    @Test
    fun `a failed and an uncompleted check are announced while a successful one is not`() {
        val late = DeliveryCheckState.Finished(
            outcome = DeliveryCheckOutcome.LATE,
            dueAt = dueAt,
            resolvedAt = dueAt,
            lateMinutes = 12,
        )
        val completed = late.copy(outcome = DeliveryCheckOutcome.NOT_COMPLETED)

        assertAll(
            { assertEquals(late, DeliveryCheckRule.announcementOf(late, announced = null)) },
            { assertEquals(late, DeliveryCheckRule.announcementOf(late, announced = dueAt.minusSeconds(60))) },
            { assertNull(DeliveryCheckRule.announcementOf(late, announced = dueAt)) },
            { assertNull(DeliveryCheckRule.announcementOf(completed, announced = null)) },
            { assertNull(DeliveryCheckRule.announcementOf(DeliveryCheckState.Running(dueAt), announced = null)) },
            { assertNull(DeliveryCheckRule.announcementOf(DeliveryCheckState.None, announced = null)) },
        )
    }
}
