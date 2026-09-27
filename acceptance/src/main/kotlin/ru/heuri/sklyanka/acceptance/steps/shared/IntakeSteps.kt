package ru.heuri.sklyanka.acceptance.steps.shared

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.heuri.sklyanka.acceptance.RussianDates
import ru.heuri.sklyanka.acceptance.steps.TAKE_BUTTON
import ru.heuri.sklyanka.acceptance.steps.localMoment
import ru.heuri.sklyanka.acceptance.steps.resultOf
import ru.heuri.sklyanka.acceptance.steps.statusOf
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.intake.MarkResult
import ru.heuri.sklyanka.domain.model.IntakeStatus
import java.time.LocalTime

/**
 * Общий словарь: приём, его отметка и состояние, в котором он остался.
 *
 * Эти строки пишут все задачи, которым нужен человек с приёмом: «приём … ожидается» —
 * шесть задач, «приём считается принятым» — семь, «временем отметки записано …» — пять.
 * Объявлены они здесь по одному разу и работают поверх того же состояния сценария, что
 * и шаги задачи: отметка, сделанная общим шагом, видна проверке задачи.
 *
 * Отметка гоняется настоящим доменным сценарием (`MarkIntakeUseCase`) поверх хранилища
 * в памяти — второй копии правил перехода в приёмке нет.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class IntakeSteps(private val world: AcceptanceWorld) {

    // --- Предыстория: приёмы и уже сделанные отметки -----------------------------------------

    // Имя лекарства — «всё, кроме закрывающей кавычки», а не «что угодно покороче»:
    // с (.+?) это выражение ловило строку «приём «Конкор» профиля «Мама» на … ожидается»
    // и уходило искать лекарство с именем «Конкор» профиля «Мама». Дефект нашла
    // исполнимая приёмка SKL-15 (SKL-26): пока сценарии стояли нереализованными,
    // до этой строки исполнение не доходило никогда.
    @Given("^приём «([^»]+)» на (.+?), (\\d{2}:\\d{2}) (?:ожидается|не отмечался)$")
    fun thatIntakeIsPending(medication: String, date: String, time: String) {
        world.intakes.pending(medication, world.localMoment(date, time))
    }

    @Given("^человек его не отмечал$")
    fun thePersonNeverMarkedIt() {
        assertTrue(world.intakes.current().isPending, "the intake is already marked")
    }

    @Given("^приём «(.+?)» на (.+?), (\\d{2}:\\d{2}) отмечен принятым в (\\d{2}:\\d{2})(?: в телефоне)?$")
    fun thatIntakeIsAlreadyTaken(medication: String, date: String, time: String, markedAt: String) {
        world.markExisting(medication, world.localMoment(date, time), LocalTime.parse(markedAt), MarkResult.TAKEN)
    }

    @Given("^приёмы «(.+?)» с (.+?) по (.+?) отмечены принятыми$")
    fun theIntakesOfThoseDatesAreTaken(medication: String, from: String, to: String) {
        // Отметка как данность Предыстории, а не как действие: сценарий, который проверяет
        // сам переход, отмечает приём доменным сценарием отметки.
        val times = world.medications.get(medication).times
        RussianDates.range(from, to, world.clock.today()).forEach { date ->
            times.forEach { time -> world.intakes.markTaken(medication, date.atTime(time)) }
        }
    }

    @Given("^в (\\d{2}:\\d{2}) человек нажал «(Принято|Пропустить)» на часах$")
    fun thePersonPressedOnTheWatch(time: String, button: String) {
        val result = if (button == TAKE_BUTTON) MarkResult.TAKEN else MarkResult.SKIPPED
        world.pressOnWatch(result, world.clock.today().atTime(LocalTime.parse(time)))
    }

    // --- Действия: отметка приёма --------------------------------------------------------------

    @When("^(.+?) в (\\d{2}:\\d{2}) человек отмечает приём (принятым|пропущенным)(?: в телефоне)?$")
    fun thePersonMarksTheIntake(date: String, time: String, result: String) {
        world.markFromPhone(world.localMoment(date, time), resultOf(result))
    }

    @When("^в (\\d{2}:\\d{2}) человек отмечает приём (принятым|пропущенным)$")
    fun thePersonMarksTheIntakeAt(time: String, result: String) {
        world.markFromPhone(world.clock.today().atTime(LocalTime.parse(time)), resultOf(result))
    }

    @When("^в (\\d{2}:\\d{2}) человек отмечает тот же приём (принятым|пропущенным) в телефоне$")
    fun thePersonMarksTheSameIntakeAt(time: String, result: String) {
        world.markFromPhone(world.clock.today().atTime(LocalTime.parse(time)), resultOf(result))
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) человек нажимает «Принять» для «(.+?)»$")
    fun thePersonPressesTakeForThatMedication(date: String, time: String, medication: String) {
        world.rememberState()
        world.clock.moveTo(world.localMoment(date, time))
        world.pressTakeOnDemand(medication)
    }

    // --- Проверки: состояние приёма -------------------------------------------------------------

    @Then("^приём (?:считается|остаётся) (принятым|пропущенным осознанно|пропущенным без отметки|ожидающимся)$")
    fun theIntakeIsInThatState(state: String) {
        assertEquals(statusOf(state), world.intakes.current().status, describe())
    }

    @Then("^приём профиля «(.+?)» считается (принятым|пропущенным осознанно|пропущенным без отметки)$")
    fun theIntakeOfThatProfileIsInThatState(profile: String, state: String) {
        val intake = world.intakes.ofProfile(world.profiles, profile).first()
        assertEquals(statusOf(state), intake.status, "intake of $profile")
    }

    @Then("^приём «(.+?)» на (.+?), (\\d{2}:\\d{2}) остаётся принятым$")
    fun thatIntakeStaysTaken(medication: String, date: String, time: String) {
        val intake = world.intakes.at(medication, world.localMoment(date, time))
        assertEquals(IntakeStatus.TAKEN, intake.status)
    }

    // --- Проверки: поля отметки и то, чего действие не тронуло ------------------------------------

    @Then("^временем отметки (?:записано|остаётся) (.+?), (\\d{2}:\\d{2})$")
    fun theMomentOfTheMarkIs(date: String, time: String) {
        val moment = world.clock.momentOf(world.localMoment(date, time))
        assertEquals(moment, world.intakes.current().actedAt, describe())
    }

    @Then("^в истории отметка подписана «отметили вы»$")
    fun theHistoryShowsThatYouMarkedIt() {
        assertNotNull(
            world.intakes.current().actorProfileId,
            "the mark of a foreign profile must carry its actor",
        )
    }

    @Then("^приёмы профиля «(.+?)» не изменяются$")
    fun theIntakesOfThatProfileAreUnchanged(profile: String) {
        world.intakes.ofProfile(world.profiles, profile).forEach { intake ->
            assertEquals(world.before[intake.id], intake, "intake at ${intake.scheduledAt}")
        }
    }

    @Then("^отметки приёмов (?:«(.+?)» )?с (.+?) по (.+?) (?:остаются прежними|не изменяются)$")
    fun theMarkingsOfThatRangeAreUnchanged(medication: String?, from: String, to: String) {
        val name = medication ?: world.medications.last
        val dates = RussianDates.range(from, to, world.clock.today()).toSet()
        val marked = world.before.values.filter { it.scheduledAt.toLocalDate() in dates && !it.isPending }
        assertTrue(marked.isNotEmpty(), "the scenario marked nothing")
        marked.forEach { intake ->
            val now = world.intakes.of(name).firstOrNull { it.id == intake.id }
            assertEquals(intake, now, "intake ${intake.scheduledAt}")
        }
    }

    // --- Вспомогательное ------------------------------------------------------------------------

    private fun describe(): String = "intake: " + world.intakes.current()
}
