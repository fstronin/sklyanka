package ru.stohastika.sklyanka.feature.reminder

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.acceptance.diagnostics.DiagnosticsStand
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckOutcome
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckState
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import java.time.LocalDateTime

/**
 * Состояние экрана проверки напоминаний (SKL-86, НФТ-52).
 *
 * Модель собирается на общем мире приёмки — как в стенде, но без композиции: вопросы
 * здесь про состояние, а не про пиксели. Проверка идёт настоящими доменными сценариями,
 * поэтому «идёт», «отменена» и «не дошло» — не строки, а настоящие записи журнала.
 */
class DiagnosticsViewModelTest {

    private val world = AcceptanceWorld()

    private val model: DiagnosticsViewModel get() = DiagnosticsStand.model(world)

    @After
    fun dropMainDispatcher() {
        DiagnosticsStand.dropMainDispatcher()
    }

    @Test
    fun `the check sheet opens and closes without arming anything`() {
        model.onCheckDelivery()
        assertTrue(model.state.value.checkSheetOpen)

        model.onDismissCheckSheet()

        assertFalse(model.state.value.checkSheetOpen)
        assertNull("«Отмена» must not arm the test firing", world.reminders.deliveryCheckAlarm())
        assertTrue("«Отмена» must not leave a check record", world.reminders.deliveryCheckRecords().isEmpty())
    }

    @Test
    fun `starting a check arms the test firing ninety seconds ahead and shows progress`() {
        model.onCheckDelivery()

        model.onStartDeliveryCheck()

        assertFalse(model.state.value.checkSheetOpen)
        val running = model.state.value.snapshot?.deliveryCheck as DeliveryCheckState.Running
        assertEquals(world.clock.momentOf(LocalDateTime.of(2026, 9, 25, 7, 0)).plusSeconds(90), running.dueAt)
        assertEquals(running.dueAt, world.reminders.deliveryCheckAlarm())
        assertEquals(1, world.reminders.deliveryCheckRecords().size)
    }

    @Test
    fun `cancelling takes the test firing away and returns the block to its initial state`() {
        model.onStartDeliveryCheck()

        model.onCancelDeliveryCheck()

        assertNull(world.reminders.deliveryCheckAlarm())
        assertEquals(DeliveryCheckState.None, model.state.value.snapshot?.deliveryCheck)
    }

    @Test
    fun `refresh closes an expired check without a restart`() {
        // Срок прошёл, срабатывания не было: возвращение на экран обязано назвать
        // «не дошло» без перезапуска приложения (НФТ-52).
        model.onStartDeliveryCheck()
        world.clock.moveTo(LocalDateTime.of(2026, 9, 25, 7, 2, 0))

        model.refresh()

        val finished = model.state.value.snapshot?.deliveryCheck as DeliveryCheckState.Finished
        assertEquals(DeliveryCheckOutcome.NOT_DELIVERED, finished.outcome)
        assertNull("the late alarm must not argue with the outcome", world.reminders.deliveryCheckAlarm())
    }

    @Test
    fun `refresh leaves a check that is still running alone`() {
        model.onStartDeliveryCheck()

        model.refresh()

        assertTrue(model.state.value.snapshot?.deliveryCheck is DeliveryCheckState.Running)
    }

    @Test
    fun `acknowledging a finished result records its moment and does not touch a running one`() {
        model.onStartDeliveryCheck()
        val dueAt = (model.state.value.snapshot?.deliveryCheck as DeliveryCheckState.Running).dueAt
        world.clock.moveTo(LocalDateTime.of(2026, 9, 25, 7, 2, 0))
        model.refresh()
        assertEquals(
            DeliveryCheckOutcome.NOT_DELIVERED,
            (model.state.value.snapshot?.deliveryCheck as DeliveryCheckState.Finished).outcome,
        )

        model.onAcknowledgeDeliveryCheck()

        assertEquals(dueAt, runBlocking { world.reminders.acknowledgements.announced() })
    }

    @Test
    fun `a check that is still running has nothing to acknowledge`() {
        model.onStartDeliveryCheck()

        model.onAcknowledgeDeliveryCheck()

        assertNull(runBlocking { world.reminders.acknowledgements.announced() })
    }

    // --- Служебная выгрузка журнала (НФТ-51, SKL-106) ------------------------------------------

    @Test
    fun `the file is named before the place is chosen and nothing is written yet`() {
        assertEquals("sklyanka-reminder-journal-2026-09-25.csv", model.journalExportFileName())
        assertTrue(world.transfer.files.documents.isEmpty())
    }

    @Test
    fun `the chosen place receives the journal and the state names the record count`() {
        world.reminders.seedJournal(listOf(firedEntry()))

        model.onJournalExportResult("memory://document")

        assertEquals(JournalExportUi.Saved(records = 1), model.state.value.journalExport)
        assertTrue(world.transfer.files.documents.containsKey("memory://document"))
    }

    @Test
    fun `cancelling the system choice leaves no outcome and no file`() {
        world.reminders.seedJournal(listOf(firedEntry()))

        model.onJournalExportResult(null)

        assertNull(model.state.value.journalExport)
        assertTrue(world.transfer.files.documents.isEmpty())
    }

    @Test
    fun `not enough space is named and no half-file remains`() {
        world.reminders.seedJournal(listOf(firedEntry()))
        world.transfer.files.freeSpace = 1L

        model.onJournalExportResult("memory://document")

        assertEquals(JournalExportUi.NoSpace, model.state.value.journalExport)
        assertTrue(world.transfer.files.documents.isEmpty())
    }

    @Test
    fun `an interrupted write is named and the target is withdrawn`() {
        world.reminders.seedJournal(listOf(firedEntry()))
        world.transfer.files.failOnWrite = true

        model.onJournalExportResult("memory://document")

        assertEquals(JournalExportUi.Failed, model.state.value.journalExport)
        assertTrue(world.transfer.files.documents.isEmpty())
    }

    @Test
    fun `a vanished system handler is an honest failure, not silence`() {
        model.onJournalExportUnavailable()

        assertEquals(JournalExportUi.Failed, model.state.value.journalExport)
    }

    private fun firedEntry(): ReminderLogEntry = ReminderLogEntry(
        occurredAt = java.time.Instant.parse("2026-09-25T04:00:37Z"),
        event = ReminderEvent.SHOWN,
        intakeId = "intake-1",
        scheduledAt = java.time.Instant.parse("2026-09-25T04:00:00Z"),
        firedAt = java.time.Instant.parse("2026-09-25T04:00:37Z"),
        deviationSeconds = 37,
        delivered = true,
    )
}
