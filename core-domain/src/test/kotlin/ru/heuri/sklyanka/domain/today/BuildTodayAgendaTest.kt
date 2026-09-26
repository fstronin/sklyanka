package ru.heuri.sklyanka.domain.today

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.model.ScheduleMode
import java.time.LocalTime

/** Сборка экрана «Сегодня» целиком (`TDD.md §9`). */
class BuildTodayAgendaTest {

    private val build = BuildTodayAgenda()

    private val eutirox = Today.medication("m-eutirox", times = listOf(LocalTime.of(8, 0)))
    private val magnesium = Today.medication("m-magnesium", times = listOf(LocalTime.of(14, 0)))
    private val losartan = Today.medication("m-losartan", times = listOf(LocalTime.of(21, 0)))
    private val ibuprofen = Today.medication("m-ibuprofen", mode = ScheduleMode.PRN, maxPerDay = 3, times = emptyList())

    @Test
    fun `a taken intake shows its actual mark time`() {
        val agenda = agendaOf(
            intakes = listOf(Today.intake("i-1", eutirox.id, Today.at(8, 0), IntakeStatus.TAKEN)),
            medications = listOf(eutirox),
            now = Today.at(9, 0),
        )

        assertEquals(IntakePresentation.TAKEN, agenda.layout.groups.single().items.single().presentation)
    }

    @Test
    fun `an intake whose time has come offers two buttons`() {
        val agenda = agendaOf(
            intakes = listOf(Today.intake("i-1", magnesium.id, Today.at(14, 0))),
            medications = listOf(magnesium),
            now = Today.at(14, 5),
        )

        assertEquals(IntakePresentation.DUE_NOW, agenda.layout.groups.single().items.single().presentation)
    }

    @Test
    fun `an intake still ahead offers the round check button`() {
        val agenda = agendaOf(
            intakes = listOf(Today.intake("i-1", losartan.id, Today.at(21, 0))),
            medications = listOf(losartan),
            now = Today.at(14, 5),
        )

        assertEquals(IntakePresentation.UPCOMING, agenda.layout.groups.single().items.single().presentation)
    }

    @Test
    fun `a missed intake stays in its group and cannot be marked`() {
        val agenda = agendaOf(
            intakes = listOf(Today.intake("i-1", eutirox.id, Today.at(8, 0), IntakeStatus.MISSED)),
            medications = listOf(eutirox),
            now = Today.at(23, 50),
        )

        val item = agenda.layout.groups.single { it.time == LocalTime.of(8, 0) }.items.single()
        assertEquals(IntakePresentation.MISSED, item.presentation)
        assertFalse(item.isMarkable)
    }

    @Test
    fun `an intake of an archived course does not reach the screen`() {
        val archived = Today.medication("m-archived", archived = true)
        val agenda = agendaOf(
            intakes = listOf(Today.intake("i-archived", archived.id, Today.at(8, 0))),
            // Выборка активных лекарств архивные уже отсекла (ПР-11): приём без своего
            // лекарства на экран не попадает.
            medications = listOf(eutirox),
            now = Today.at(9, 0),
        )

        assertTrue(agenda.layout.groups.isEmpty())
    }

    @Test
    fun `an on-demand intake does not appear in the time lanes`() {
        val agenda = agendaOf(
            intakes = listOf(Today.intake("i-prn", ibuprofen.id, Today.at(16, 40), IntakeStatus.TAKEN)),
            medications = listOf(ibuprofen),
            now = Today.at(17, 0),
        )

        assertTrue(agenda.layout.groups.isEmpty())
        assertEquals(1, agenda.prn.size)
        assertEquals(1, agenda.prn.single().takenToday)
    }

    @Test
    fun `an exhausted daily limit closes the take button`() {
        val taken = (0 until 3).map { index ->
            Today.intake("i-prn-$index", ibuprofen.id, Today.at(8 + index * 5, 0), IntakeStatus.TAKEN)
        }

        val agenda = agendaOf(intakes = taken, medications = listOf(ibuprofen), now = Today.at(21, 0))

        assertFalse(agenda.prn.single().canTake)
    }

    @Test
    fun `a profile without medications sees the first run empty state`() {
        val agenda = agendaOf(intakes = emptyList(), medications = emptyList(), now = Today.at(7, 30))

        assertTrue(agenda.isFirstRun)
        assertFalse(agenda.progress.hasPlan)
    }

    @Test
    fun `an on-demand medication cancels the empty state`() {
        val agenda = agendaOf(intakes = emptyList(), medications = listOf(ibuprofen), now = Today.at(7, 30))

        assertFalse(agenda.isFirstRun)
        assertFalse(agenda.hasNothingToday)
        assertEquals(1, agenda.prn.size)
    }

    @Test
    fun `medications without intakes today are not the first run empty state`() {
        val agenda = agendaOf(intakes = emptyList(), medications = listOf(eutirox), now = Today.at(7, 30))

        // Лекарства есть, приёмов на сегодня нет: пустое состояние первого запуска
        // здесь показывать нельзя (ОВ-34), и прогрессу дня тоже нечего показывать.
        assertFalse(agenda.isFirstRun)
        assertTrue(agenda.hasNothingToday)
        assertFalse(agenda.progress.hasPlan)
        assertEquals(1, agenda.progress.medicationCount)
    }

    private fun agendaOf(
        intakes: List<Intake>,
        medications: List<Medication>,
        now: java.time.LocalDateTime,
    ): TodayAgenda = build(date = Today.DATE, intakes = intakes, activeMedications = medications, now = now)
}
