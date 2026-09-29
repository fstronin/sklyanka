package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.PrnOutcome
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode

/**
 * Шаги SKL-08 о приёме «по потребности» (SKL-26, группа «Приём „по потребности“»).
 *
 * Предметная область: приём по потребности рождается уже принятым в момент нажатия
 * (ПР-4), а суточное ограничение считает его за приём (ПР-7). Нажатие на экране —
 * это тот же доменный сценарий [AcceptanceWorld.pressTakeOnDemand], копии правила
 * в приёмке нет.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class TodayPrnSteps(private val world: AcceptanceWorld) {

    // --- Предыстория ------------------------------------------------------------

    @Given("^сегодня «([^»]+)» ещё не принимали$")
    fun thatOnDemandMedicationWasNotTakenToday(medication: String) {
        val name = world.medicationNamed(medication)
        assertTrue(
            world.intakes.of(name).none { it.scheduledAt.toLocalDate() == world.clock.today() },
            "«$name» must not have intakes today yet",
        )
    }

    @Given("^(.+?) «([^»]+)» уже принят в (\\d{2}:\\d{2}), (\\d{2}:\\d{2}) и (\\d{2}:\\d{2})$")
    fun thatOnDemandMedicationWasTakenAtThoseTimes(
        date: String,
        medication: String,
        first: String,
        second: String,
        third: String,
    ) {
        val name = world.medicationNamed(medication)
        listOf(first, second, third).forEach { time ->
            world.clock.moveTo(world.localMoment(date, time))
            val outcome = world.pressTakeOnDemand(name)
            assertTrue(outcome is PrnOutcome.Created, "the history of «$name» could not be created: $outcome")
        }
    }

    @Given("^у профиля нет ни одного лекарства по потребности$")
    fun theProfileHasNoOnDemandMedications() {
        // Общая Предыстория файла заводит лекарство по потребности всем сценариям:
        // этому оно не нужно, и строка убирает его, а не спорит с Предысторией.
        val profileId = world.profiles.get(world.profiles.active).id
        world.medications.names()
            .filter { name ->
                val medication = world.medications.get(name)
                medication.profileId == profileId && medication.scheduleMode == ScheduleMode.PRN
            }
            .forEach(world.medications::remove)
        val left = world.medications.all().filter { it.profileId == profileId && it.scheduleMode == ScheduleMode.PRN }
        assertTrue(left.isEmpty(), "the on-demand medications must be gone: $left")
    }

    // --- Проверки ---------------------------------------------------------------

    @Then("^появляется принятый приём «([^»]+)» с назначенным временем (.+?), (\\d{2}:\\d{2})$")
    fun aTakenIntakeAppearsWithThatScheduledMoment(medication: String, date: String, time: String) {
        val outcome = world.intakes.lastPrnOutcome
        assertTrue(outcome is PrnOutcome.Created, "the press did not create an intake: $outcome")
        val intake = (outcome as PrnOutcome.Created).intake
        assertEquals(world.localMoment(date, time), intake.scheduledAt, "scheduled moment of the on-demand intake")
        assertEquals(IntakeStatus.TAKEN, intake.status, "an on-demand intake is created taken (ПР-4)")
        assertEquals(
            world.medications.get(world.medicationNamed(medication)).id,
            intake.medicationId,
            "the created intake must belong to the medication the scenario names",
        )
    }

    @Then("^фактическое время отметки совпадает с назначенным$")
    fun theMarkedMomentEqualsTheScheduledOne() {
        val intake = world.intakes.current()
        assertEquals(
            world.clock.momentOf(intake.scheduledAt),
            intake.actedAt,
            "an on-demand intake is marked at the moment it is created (ПР-4)",
        )
    }
}
