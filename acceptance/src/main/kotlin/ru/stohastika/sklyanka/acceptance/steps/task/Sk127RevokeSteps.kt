package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.PrnOutcome
import ru.stohastika.sklyanka.domain.intake.UndoOutcome
import ru.stohastika.sklyanka.domain.log.OpEvent
import java.time.LocalTime

/**
 * Шаги приёмки SKL-127 — снятие записи «по потребности» (Р-46).
 *
 * Живут в тестовом наборе `acceptance`, а не в общем артефакте: экранным прогонам они
 * не нужны (окно отмены проверено сценариями SKL-30, а снятие — исходом, а не нажатием).
 *
 * Невидимость снятой записи проверяется по [ru.stohastika.sklyanka.acceptance.world.ScenarioIntakes.visibleOf]:
 * сценарное хранилище повторяет условие `revoked_at IS NULL` выборок Room, сам SQL закреплён
 * инструментальным тестом (`IntakeRepositoryRevokeTest`: признак, невидимость в выборках дня и периода,
 * счёт суток и условное удаление лекарства).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class Sk127RevokeSteps(private val world: AcceptanceWorld) {

    // --- Предыстория -----------------------------------------------------------------------

    @Given("^в (\\d{2}:\\d{2}) человек принял «([^»]+)» и снял эту запись в (\\d{2}:\\d{2})$")
    fun thePersonTookAndRevoked(taken: String, medication: String, revoked: String) {
        val name = world.medicationNamed(medication)
        val day = world.clock.today()
        world.clock.moveTo(day.atTime(LocalTime.parse(taken)))
        val outcome = world.pressTakeOnDemand(name)
        assertTrue(outcome is PrnOutcome.Created, "the on-demand record had to be created: $outcome")
        world.undo(listOf(world.intakes.of(name).last().id), day.atTime(LocalTime.parse(revoked)))
        assertEquals(UndoOutcome.Applied(undone = 1), world.lastUndoOutcome, "the record had to be revoked")
    }

    // --- Действия --------------------------------------------------------------------------

    @When("^(.+?) в (\\d{2}:\\d{2}) человек снимает запись приёма «([^»]+)»$")
    fun thePersonRevokesTheRecord(date: String, time: String, medication: String) {
        world.undo(listOf(recordOf(medication).id), world.localMoment(date, time))
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) человек пытается снять ту же запись ещё раз$")
    fun thePersonRevokesTheSameRecordAgain(date: String, time: String) {
        world.undo(listOf(recordOf(world.medications.last).id), world.localMoment(date, time))
    }

    // --- Проверки --------------------------------------------------------------------------

    @Then("^запись приёма «([^»]+)» снята$")
    fun theRecordIsRevoked(medication: String) {
        val record = recordOf(medication)
        assertNotNull(record.revokedAt, "the record must carry the moment of revocation")
        assertEquals(UndoOutcome.Applied(undone = 1), world.lastUndoOutcome, "the revocation had to take effect")
    }

    @Then("^запись приёма «([^»]+)» остаётся снятой$")
    fun theRecordStaysRevoked(medication: String) {
        val record = recordOf(medication)
        assertNotNull(record.revokedAt, "the record must stay revoked")
        assertEquals(
            UndoOutcome.Applied(undone = 0),
            world.lastUndoOutcome,
            "the second revocation must change nothing",
        )
    }

    @Then("^в истории приёма «([^»]+)» на (\\d{2}:\\d{2}) нет$")
    fun theHistoryDoesNotShowTheRecord(medication: String, time: String) {
        val name = world.medicationNamed(medication)
        val moment = world.clock.today().atTime(LocalTime.parse(time))
        val visible = world.intakes.visibleOf(name).filter { it.scheduledAt == moment }
        assertTrue(visible.isEmpty(), "the revoked record must be invisible to the product: $visible")
    }

    @Then("^в журнале появляется запись о снятии записи$")
    fun theJournalHasTheRevocation() {
        assertTrue(
            world.log.records.any { it.event == OpEvent.INTAKE_REVOKED },
            "the revocation must leave a trace: ${world.log.events}",
        )
    }

    @Then("^в записи есть время снятия и опознаватели приёма и лекарства$")
    fun theRecordNamesTheIntake() {
        val record = world.log.records.last { it.event == OpEvent.INTAKE_REVOKED }
        assertNotNull(record.intakeId, "the trace must name the intake")
        assertNotNull(record.medicationId, "the trace must name the medication")
        assertNotNull(record.at, "the trace must carry its moment")
    }

    @Then("^в выгрузке у записи приёма «([^»]+)» на (\\d{2}:\\d{2}) указана отметка снятия$")
    fun theExportCarriesTheRevocation(medication: String, time: String) {
        // Выгрузка — тот же снимок, из которого строится файл; провод (JSON) закреплён
        // тестами кодека в `core-data`, а здесь проверяется, что снятие в него попадает.
        val name = world.medicationNamed(medication)
        val medicationId = world.medications.get(name).id
        val moment = world.clock.today().atTime(LocalTime.parse(time))
        val exported = world.transfer.snapshot().intakes
            .single { it.medicationId == medicationId && it.scheduledAt == moment }
        assertNotNull(exported.revokedAt, "the exported record must carry the moment of revocation")
    }

    /** Запись «по потребности» последнего названного лекарства: у режима она одна на нажатие. */
    private fun recordOf(medication: String) = world.intakes.of(world.medicationNamed(medication))
        .lastOrNull()
        ?: error("the scenario created no on-demand record for «$medication»")
}
