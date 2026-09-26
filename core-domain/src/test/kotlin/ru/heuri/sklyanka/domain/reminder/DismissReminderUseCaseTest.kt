package ru.heuri.sklyanka.domain.reminder

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.heuri.sklyanka.domain.model.Fixtures
import ru.heuri.sklyanka.domain.model.IntakeStatus
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("A mark closes the card, and one row of a shared card does not close it")
class DismissReminderUseCaseTest {

    private val world = ReminderTestWorld()

    @Test
    fun `taking the intake removes the card and revokes the watch card`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 0))
        world.fired(Fixtures.INTAKE_ID)

        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 1))
        world.take()

        assertEquals(IntakeStatus.TAKEN, world.stored().status)
        assertEquals(listOf(Fixtures.INTAKE_ID), world.watch.revoked)
        assertEquals(1, world.notifications.cancelled.size)
        // Остаток списан один раз на дозу приёма (ПР-5).
        assertEquals(BigDecimal("29"), world.medication().stockLeft)
    }

    @Test
    fun `skipping removes the card and leaves the stock alone`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 0))
        world.fired(Fixtures.INTAKE_ID)

        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 2))
        world.skip()

        assertEquals(IntakeStatus.SKIPPED, world.stored().status)
        assertEquals(BigDecimal("30"), world.medication().stockLeft)
        assertEquals(1, world.notifications.cancelled.size)
    }

    @Test
    fun `marking one row of a shared card keeps the card with the other row`() = runTest {
        world.pending()
        world.secondMedication()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 0))
        world.fired(Fixtures.INTAKE_ID)

        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 3))
        world.take()

        assertTrue(world.notifications.cancelled.isEmpty(), "the shared card stays on screen (REM-36)")
        val lastCard = world.notifications.visible.last()
        assertEquals(listOf(ReminderTestWorld.SECOND_INTAKE_ID), lastCard.items.map { it.intakeId })
        assertEquals(IntakeStatus.PENDING, world.stored(ReminderTestWorld.SECOND_INTAKE_ID).status)
    }

    @Test
    fun `marking the last row of a shared card removes it`() = runTest {
        world.pending()
        world.secondMedication()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 0))
        world.fired(Fixtures.INTAKE_ID)

        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 3))
        world.take()
        world.take(id = ReminderTestWorld.SECOND_INTAKE_ID)

        assertEquals(1, world.notifications.cancelled.size)
    }

    @Test
    fun `a mark from the watch closes the card on the phone and does not decrement twice`() = runTest {
        world.pending()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 0))
        world.fired(Fixtures.INTAKE_ID)

        world.moveTo(LocalDateTime.of(2026, 9, 25, 14, 1))
        world.takeOnWatch(at = world.momentOf(LocalDateTime.of(2026, 9, 25, 14, 1)))
        // Повторное нажатие на самой карточке: отметка идемпотентна по intake_id (НАП-32).
        world.take()

        assertEquals(IntakeStatus.TAKEN, world.stored().status)
        assertEquals(BigDecimal("29"), world.medication().stockLeft)
        assertTrue(world.notifications.cancelled.isNotEmpty())
    }
}

/**
 * Отметка приёма, о котором карточки на экране нет.
 *
 * Отдельным классом, потому что проверяется отсутствие действия: отметка с экрана «Сегодня»
 * идёт через ту же единственную точку отметки, и уведомление в ответ на неё было бы
 * уведомлением из ниоткуда.
 */
@DisplayName("A mark without a card on screen shows nothing")
class DismissWithoutCardTest {

    private val world = ReminderTestWorld()

    @Test
    fun `marking from the day screen does not show a reminder of the neighbouring intake`() = runTest {
        world.pending()
        world.secondMedication()
        world.moveTo(LocalDateTime.of(2026, 9, 25, 13, 0))

        world.take()

        assertTrue(world.notifications.shown.isEmpty(), "no notification may appear out of nowhere")
        assertTrue(world.notifications.cancelled.isEmpty())
        assertEquals(listOf(Fixtures.INTAKE_ID), world.watch.revoked)
    }
}
