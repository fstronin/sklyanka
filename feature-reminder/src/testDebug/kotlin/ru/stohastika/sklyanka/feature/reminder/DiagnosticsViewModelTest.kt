package ru.stohastika.sklyanka.feature.reminder

import kotlinx.coroutines.CancellationException
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

    // --- Отчёт разработчику (ОВ-127, AC-30…AC-33) ---------------------------------------------

    @Test
    fun `a sent report is named and its comment reaches the sender`() {
        model.onReportOpen()

        model.onReportCommentChange("напоминание не приходит утром")
        model.onReportSend()

        assertEquals(DeveloperReportOutcome.SENT, model.state.value.reportOutcome)
        assertEquals(listOf("напоминание не приходит утром"), DiagnosticsStand.sentComments)
    }

    @Test
    fun `a refused send keeps the comment and shows retry`() {
        model.onReportOpen()
        model.onReportCommentChange("текст жалобы")
        DiagnosticsStand.reportOutcome = DeveloperReportOutcome.FAILED

        model.onReportSend()

        assertEquals(DeveloperReportOutcome.FAILED, model.state.value.reportOutcome)
        assertEquals("текст жалобы", model.state.value.reportComment)
    }

    @Test
    fun `dismissing the report clears its state for the next open`() {
        model.onReportOpen()
        model.onReportCommentChange("черновик")
        model.onReportSend()

        model.onReportDismiss()

        assertFalse(model.state.value.reportOpen)
        assertEquals("", model.state.value.reportComment)
        assertNull(model.state.value.reportOutcome)
    }

    @Test
    fun `reopening the report starts with an empty field`() {
        model.onReportOpen()
        model.onReportCommentChange("старый текст")
        model.onReportDismiss()

        model.onReportOpen()

        assertTrue(model.state.value.reportOpen)
        assertEquals("", model.state.value.reportComment)
        assertNull(model.state.value.reportOutcome)
    }

    @Test
    fun `a cancelled send is not swallowed and leaves no outcome`() {
        model
        DiagnosticsStand.reportSenderOverride = DeveloperReportSender { throw CancellationException("cancelled") }
        model.onReportOpen()
        model.onReportCommentChange("текст")

        model.onReportSend()

        assertNull(model.state.value.reportOutcome)
        assertEquals("текст", model.state.value.reportComment)
    }
}
