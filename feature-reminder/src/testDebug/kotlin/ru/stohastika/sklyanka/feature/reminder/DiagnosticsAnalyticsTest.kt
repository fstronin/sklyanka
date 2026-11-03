package ru.stohastika.sklyanka.feature.reminder

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.acceptance.diagnostics.DiagnosticsStand
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.analytics.AnalyticsEventName
import ru.stohastika.sklyanka.domain.analytics.AnalyticsProperty

/**
 * Исход самопроверки доставки в аналитике (SKL-165, ПМ-26).
 *
 * Проверка идёт настоящими доменными сценариями мира приёмки: «дошло», «опоздало»
 * и «не дошло» — не строки, а записи журнала. Событие ставится там, где проверка
 * признана завершённой, поэтому повторное перечитывание снимка дубля не даёт.
 */
class DiagnosticsAnalyticsTest {

    private val world = AcceptanceWorld()

    @After
    fun dropMainDispatcher() {
        DiagnosticsStand.dropMainDispatcher()
    }

    @Test
    fun `an on time check is a passed result recorded once`() {
        val kit = kit()
        world.reminders.appForeground = false
        world.reminders.startDeliveryCheck()
        fireAtDue()

        val model = DiagnosticsStand.model(world, kit.recorder)
        model.refresh()

        val record = kit.queue.recorded.single()
        assertEquals(AnalyticsEventName.DELIVERY_CHECK_RESULT, record.name)
        assertEquals(mapOf(AnalyticsProperty.RESULT to "passed"), record.properties)
    }

    @Test
    fun `a late check is a late result`() {
        val kit = kit()
        world.reminders.appForeground = false
        world.reminders.startDeliveryCheck()
        fireAtDue(plusSeconds = 5 * 60)

        DiagnosticsStand.model(world, kit.recorder)

        assertEquals(mapOf(AnalyticsProperty.RESULT to "late"), kit.queue.recorded.single().properties)
    }

    @Test
    fun `a check that never fired is a failed result`() {
        val kit = kit()
        world.reminders.appForeground = false
        world.reminders.startDeliveryCheck()
        expireCheck()

        DiagnosticsStand.model(world, kit.recorder)

        assertEquals(mapOf(AnalyticsProperty.RESULT to "failed"), kit.queue.recorded.single().properties)
    }

    @Test
    fun `reopening the same finished check does not duplicate the event`() {
        val kit = kit()
        world.reminders.appForeground = false
        world.reminders.startDeliveryCheck()
        expireCheck()

        val model = DiagnosticsStand.model(world, kit.recorder)
        model.refresh()
        model.refresh()

        assertEquals(1, kit.queue.recorded.size)
        assertTrue(kit.transport.delivered.none { it.name != AnalyticsEventName.DELIVERY_CHECK_RESULT })
    }

    /** Мир аналитики теста: запись идёт сразу, виртуального времени ей не нужно. */
    private fun kit() = AnalyticsTestKit(CoroutineScope(Dispatchers.Unconfined))

    private fun fireAtDue(plusSeconds: Long = 0) {
        val dueAt = requireNotNull(world.reminders.deliveryCheckAlarm()) { "the check must be scheduled" }
        world.clock.moveTo(dueAt.atZone(world.clock.zone).toLocalDateTime().plusSeconds(plusSeconds))
        world.reminders.firedDeliveryCheck()
    }

    private fun expireCheck() {
        val dueAt = requireNotNull(world.reminders.deliveryCheckAlarm()) { "the check must be scheduled" }
        world.clock.moveTo(dueAt.atZone(world.clock.zone).toLocalDateTime().plusSeconds(5))
    }
}
