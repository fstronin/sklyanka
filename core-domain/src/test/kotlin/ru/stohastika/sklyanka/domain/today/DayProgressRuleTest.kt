package ru.stohastika.sklyanka.domain.today

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode

/** Прогресс дня: знаменатель без приёмов «по потребности» (ЭКР-1) и счётчик лекарств (ЭКР-2). */
class DayProgressRuleTest {

    private val scheduled = Today.medication("m-scheduled")
    private val second = Today.medication("m-second")
    private val onDemand = Today.medication("m-prn", mode = ScheduleMode.PRN, maxPerDay = 3)

    @Test
    fun `a day without intakes has nothing to count`() {
        val progress = DayProgressRule(items = emptyList(), activeMedications = listOf(scheduled))

        assertEquals(DayProgress(taken = 0, planned = 0, medicationCount = 1), progress)
        assertFalse(progress.hasPlan)
    }

    @Test
    fun `an on-demand intake enters neither the numerator nor the denominator`() {
        val items = listOf(
            Today.item(Today.intake("i-prn", onDemand.id, Today.at(16, 40), IntakeStatus.TAKEN), onDemand),
        )

        val progress = DayProgressRule(items, activeMedications = listOf(scheduled, onDemand))

        assertEquals(0, progress.taken)
        assertEquals(0, progress.planned)
    }

    @Test
    fun `a mixed day counts only the scheduled intakes`() {
        val items = listOf(
            Today.item(Today.intake("i-1", scheduled.id, Today.at(8, 0), IntakeStatus.TAKEN), scheduled),
            Today.item(Today.intake("i-2", second.id, Today.at(8, 0), IntakeStatus.TAKEN), second),
            Today.item(Today.intake("i-3", scheduled.id, Today.at(14, 0)), scheduled),
            Today.item(Today.intake("i-4", second.id, Today.at(21, 0)), second),
            Today.item(Today.intake("i-5", scheduled.id, Today.at(21, 0)), scheduled),
            Today.item(Today.intake("i-prn", onDemand.id, Today.at(16, 40), IntakeStatus.TAKEN), onDemand),
        )

        val progress = DayProgressRule(items, activeMedications = listOf(scheduled, second, onDemand))

        assertEquals(2, progress.taken)
        assertEquals(5, progress.planned)
    }

    @Test
    fun `the medication counter leaves out on-demand and archived courses`() {
        val archived = Today.medication("m-archived", archived = true)

        val progress = DayProgressRule(
            items = emptyList(),
            activeMedications = listOf(scheduled, second, onDemand, archived),
        )

        assertEquals(2, progress.medicationCount)
    }

    @Test
    fun `the bar fill is the taken share of the plan`() {
        val items = listOf(
            Today.item(Today.intake("i-1", scheduled.id, Today.at(8, 0), IntakeStatus.TAKEN), scheduled),
            Today.item(Today.intake("i-2", scheduled.id, Today.at(14, 0)), scheduled),
        )

        val progress = DayProgressRule(items, activeMedications = listOf(scheduled))

        assertEquals(0.5f, progress.fraction)
    }
}
