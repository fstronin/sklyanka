package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.en.But
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.intake.UndoOutcome
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import java.time.LocalTime

/**
 * Доменные шаги приёмки SKL-30 — сам акт отмены, её след в журнале и напоминание.
 *
 * Живут в тестовом наборе `acceptance`, а не в общем артефакте: экранным прогонам они
 * не нужны, а в `app` их не видно вовсе — его classpath общий артефакт плюс шаги `app`
 * (TDD.md §12.3). Одинаковые по смыслу утверждения, которые нужны и экранам, объявлены
 * один раз в `steps/shared/UndoSharedSteps`.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class UndoSteps(private val world: AcceptanceWorld) {

    // --- Предыстория ----------------------------------------------------------------------

    @Given("^журнал операций пуст$")
    fun theJournalIsEmpty() {
        assertTrue(world.log.records.isEmpty(), "the journal must start empty: ${world.log.events}")
    }

    @Given("^напоминание о приёме «([^»]+)» на (.+?), (\\d{2}:\\d{2}) сработало$")
    fun theReminderFired(medication: String, date: String, time: String) {
        val name = world.medicationNamed(medication)
        val moment = world.localMoment(date, time)
        world.intakes.pending(name, moment)
        world.reminders.reschedule(RescheduleReason.MANUAL)
        world.clock.moveTo(moment)
        world.reminders.deliverThrough(moment)
        assertTrue(world.reminders.shownCards.isNotEmpty(), "the reminder of $name had to fire at $time")
    }

    @Given("^приём отмечен принятым в (\\d{2}:\\d{2})$")
    fun theCurrentIntakeIsMarkedTaken(time: String) {
        val intake = world.intakes.currentOrScheduled()
        val moment = intake.scheduledAt.toLocalDate().atTime(LocalTime.parse(time))
        world.clock.moveTo(moment)
        world.mark(intake.id, MarkResult.TAKEN, IntakeSource.PHONE, world.clock.momentOf(moment))
    }

    @Given("^в (\\d{2}:\\d{2}) человек отменил отметку$")
    fun thePersonAlreadyUndidTheMark(time: String) {
        world.undo(listOf(world.intakes.current().id), world.clock.today().atTime(LocalTime.parse(time)))
        assertEquals(UndoOutcome.Applied(undone = 1), world.lastUndoOutcome, "the undo had to take effect")
    }

    // Строки про остаток и группу одновременных приёмов объявлены в TodayDenseSteps:
    // они стоят и в сценариях плотного дня, и второе объявление сделало бы шаг
    // неоднозначным.

    // --- Действия -------------------------------------------------------------------------

    @When("^(.+?) в (\\d{2}:\\d{2}) человек отменяет отметку$")
    fun thePersonUndoesTheMark(date: String, time: String) {
        world.undo(listOf(world.intakes.current().id), world.localMoment(date, time))
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) человек отменяет отметку приёма на (\\d{2}:\\d{2})$")
    fun thePersonUndoesTheIntakeAt(date: String, time: String, target: String) {
        val intake = world.intakes.at(world.medications.last, world.localMoment(date, target))
        world.undo(listOf(intake.id), world.localMoment(date, time))
    }

    @When("^в (\\d{2}:\\d{2}) человек отменяет это действие$")
    fun thePersonUndoesThatAction(time: String) {
        val ids = world.intakes.lastGroupChangedIds
        assertEquals(GROUP_SIZE, ids.size, "the group action did not take place")
        world.undo(ids, world.clock.today().atTime(LocalTime.parse(time)))
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) человек отмечает все три приёма принятыми одним действием$")
    fun thePersonMarksAllThreeIntakes(date: String, time: String) {
        val moment = world.localMoment(date, time)
        // Приёмы группы уже заведены Предысторией: шаг берёт ожидающиеся приёмы суток
        // и отмечает их одним действием (ОВ-32). Время действия отличается от времени
        // приёма — нажатие в 14:05 отмечает приёмы на 14:00.
        val ids = world.intakes.ofProfile(world.profiles, world.profiles.active)
            .filter { it.isPending && it.scheduledAt.toLocalDate() == moment.toLocalDate() }
            .map { it.id }
        assertEquals(GROUP_SIZE, ids.size, "the scenario needs three pending intakes on ${moment.toLocalDate()}")
        world.markGroup(ids, moment, MarkResult.TAKEN)
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) человек пытается отменить ту же отметку ещё раз$")
    fun thePersonTriesToUndoTheSameMarkAgain(date: String, time: String) {
        val ids = world.lastUndoneIds
        assertTrue(ids.isNotEmpty(), "the scenario never undid a mark")
        world.undo(ids, world.localMoment(date, time))
        assertEquals(UndoOutcome.Applied(undone = 0), world.lastUndoOutcome, "there is no second undo")
    }

    // --- Проверки: журнал и напоминание -----------------------------------------------------

    @Then("^в журнале появляется запись об отмене отметки$")
    fun theJournalCarriesTheUndoRecord() {
        assertTrue(world.lastUndoOutcome is UndoOutcome.Applied, "the undo did not happen: ${world.lastUndoOutcome}")
        assertTrue(
            world.log.records.any { it.event == OpEvent.INTAKE_MARK_UNDONE },
            "the journal must carry the undo trace: ${world.log.events}",
        )
    }

    @Then("^в записи есть время отмены и опознаватели приёма и лекарства$")
    fun theRecordCarriesTheMomentAndIdentifiers() {
        val record = world.log.records.last { it.event == OpEvent.INTAKE_MARK_UNDONE }
        val intake = world.intakes.current()
        assertEquals(world.lastUndoAt, record.at, "the moment of the record is the moment of the undo")
        assertEquals(intake.id, record.intakeId, "intake id")
        assertEquals(intake.medicationId, record.medicationId, "medication id")
    }

    @But("^в записи нет ни названия лекарства, ни дозировки, ни имени профиля$")
    fun theRecordCarriesNoMedicalData() {
        val written = world.log.records.flatMap { record ->
            listOfNotNull(
                record.profileId,
                record.medicationId,
                record.intakeId,
                record.before,
                record.after,
                record.source,
                record.detail,
            )
        }
        val medicationNames = world.medications.names()
        val profileNames = world.profiles.all().map { it.name }
        written.forEach { value ->
            medicationNames.forEach { name ->
                assertTrue(!value.contains(name), "the journal carries the medication name: $value")
            }
            profileNames.forEach { name ->
                assertTrue(!value.contains(name), "the journal carries the profile name: $value")
            }
            assertTrue(!value.contains(TABLET_WORD), "the journal carries a dose: $value")
        }
    }

    @But("^напоминание об этом приёме повторно не приходит$")
    fun theReminderDoesNotComeAgain() {
        val intake = world.intakes.current()
        assertNull(world.reminders.alarmOf(intake.id), "the undo must not reschedule the alarm")
        assertEquals(1, world.reminders.shownCards.size, "the reminder must not be shown a second time")
    }

    private companion object {
        /** Слово единицы дозы: в журнале не должно быть ни дозы, ни её единицы (НФТ-19). */
        const val TABLET_WORD = "таблетк"

        /** Размер группы одновременных приёмов: сценарий называет ровно три. */
        const val GROUP_SIZE = 3
    }
}
