package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import ru.stohastika.sklyanka.acceptance.RussianNames
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.steps.resultOf
import ru.stohastika.sklyanka.acceptance.steps.statusOf
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Шаги SKL-15: чей профиль получает отметку (`ПРФ-10`, `ПРФ-14`, `ПРФ-22`).
 *
 * Это вопрос предметной области, а не экрана: отметка идёт настоящим доменным сценарием
 * (`MarkIntakeUseCase`) поверх мира приёмки, и подпись исполнителя ставит то же правило
 * (`ActorResolver`), что в продукте. Копии правила в приёмке нет.
 *
 * Цена ошибки здесь наибольшая в продукте: отметка чужого приёма как своего добавляет
 * в чужую историю приём, которого не было, и убирает из своей приём, который был.
 * Поэтому каждый сценарий проверяет не только «отметка применилась», но и «в соседнем
 * профиле не изменилось ничего».
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class ProfileMarkingSteps(private val world: AcceptanceWorld) {

    // --- Предыстория -----------------------------------------------------------

    @Given("^у профиля «([^»]+)» есть приём «([^»]+)» на (.+?), (\\d{2}:\\d{2})$")
    fun theProfileHasThatIntake(profile: String, medication: String, date: String, time: String) {
        val moment = world.localMoment(date, time)
        // Строка сама объявляет лекарство, если Предыстория его не заводила: профиль
        // в ней назван, время приёма названо, и выдумывать здесь нечего. Лекарство,
        // заведённое Предысторией, при этом не пересоздаётся — оно найдётся по падежу.
        val known = RussianNames.resolve(medication, world.medications.names())
        if (known == null) {
            world.medications.add(
                profile = world.profiles,
                profileName = profile,
                name = medication,
                times = listOf(moment.toLocalTime()),
            )
        } else {
            assertEquals(
                world.profiles.get(profile).id,
                world.medications.get(known).profileId,
                "«$known» must belong to «$profile»",
            )
        }
        world.intakes.pending(known ?: medication, moment)
    }

    // --- Действия --------------------------------------------------------------

    @When("^(.+?) в (\\d{2}:\\d{2}) владелец телефона отмечает приём «([^»]+)» (принятым|пропущенным)$")
    fun theOwnerMarksThatIntake(date: String, time: String, medication: String, result: String) {
        mark(medication, world.localMoment(date, time), resultOf(result), IntakeSource.PHONE)
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) человек отмечает приём «([^»]+)» (принятым|пропущенным)$")
    fun thePersonMarksThatIntake(date: String, time: String, medication: String, result: String) {
        mark(medication, world.localMoment(date, time), resultOf(result), IntakeSource.PHONE)
    }

    @When("^в (\\d{2}:\\d{2}) этот приём отмечается (принятым|пропущенным) с часов$")
    fun thatIntakeIsMarkedFromTheWatch(time: String, result: String) {
        val intake = world.intakes.current()
        val moment = world.clock.today().atTime(LocalTime.parse(time))
        world.rememberState()
        world.clock.moveTo(moment)
        world.mark(intake.id, resultOf(result), IntakeSource.WATCH, world.clock.momentOf(moment))
    }

    // --- Проверки: чей профиль получил отметку ---------------------------------

    @Then("^приём «([^»]+)» профиля «([^»]+)» считается (принятым|пропущенным вручную|пропущенным без отметки)$")
    fun thatIntakeOfThatProfileIsInThatState(medication: String, profile: String, state: String) {
        val intake = intakeOf(medication, profile)
        assertEquals(statusOf(state), intake.status, "intake of «$medication» of «$profile»")
    }

    @Then("^приём «([^»]+)» считается (принятым|пропущенным вручную)$")
    fun thatIntakeIsInThatState(medication: String, state: String) {
        assertEquals(statusOf(state), focused(medication).status, "intake of «$medication»")
    }

    @Then("^приём «([^»]+)» профиля «([^»]+)» остаётся ожидающимся$")
    fun thatIntakeOfThatProfileStaysPending(medication: String, profile: String) {
        assertEquals(IntakeStatus.PENDING, intakeOf(medication, profile).status, "intake of «$profile»")
    }

    @Then("^в истории профиля «([^»]+)» новых записей не появляется$")
    fun theHistoryOfThatProfileGainsNothing(profile: String) {
        // «Новая запись в истории» — это приём, переставший ожидаться: истории как
        // отдельного хранилища нет, её собирают из приёмов (SKL-18).
        val marked = world.intakes.ofProfile(world.profiles, profile).filterNot { it.isPending }
        val markedBefore = world.before.values.filter { it.id in marked.map { intake -> intake.id } }
        assertEquals(markedBefore.size, marked.size, "the history of «$profile» gained a mark it must not have")
    }

    @Then("^ни один приём профиля «([^»]+)» не меняется$")
    fun noIntakeOfThatProfileChanges(profile: String) {
        world.intakes.ofProfile(world.profiles, profile).forEach { intake ->
            assertEquals(world.before[intake.id], intake, "intake of «$profile» at ${intake.scheduledAt}")
        }
    }

    @Then("^активным профилем на телефоне остаётся «([^»]+)»$")
    fun theActiveProfileStays(profile: String) {
        // Отметка с часов контекст телефона не меняет: на часах экрана профиля нет (ПРФ-22).
        assertEquals(profile, world.profiles.active, "the active profile must not follow a watch press")
    }

    // --- Проверки: подпись исполнителя ----------------------------------------

    @Then("^под фактическим временем отметки стоит подпись «отметили вы»$")
    fun theMarkCarriesTheActorCaption() {
        val intake = world.intakes.current()
        assertNotNull(intake.actedAt, "the mark must carry the moment it was made")
        assertEquals(
            world.profiles.self()?.id,
            intake.actorProfileId,
            "a mark made by the phone owner in a foreign profile must name him (ПРФ-14)",
        )
    }

    @Then("^подписи «отметили вы» под ним нет$")
    fun theMarkCarriesNoActorCaption() {
        assertNull(
            world.intakes.current().actorProfileId,
            "a mark in the own profile must not be signed (ПРФ-14)",
        )
    }

    @Then("^подписи «отметили вы» у него нет$")
    fun thatMarkCarriesNoActorCaption() = theMarkCarriesNoActorCaption()

    @Then("^в истории он подписан «отметили вы»$")
    fun theHistoryShowsTheActor() = theMarkCarriesTheActorCaption()

    // --- Обвязка ---------------------------------------------------------------

    private fun mark(medication: String, moment: LocalDateTime, result: MarkResult, source: IntakeSource) {
        val intake = world.intakes.pending(medication, moment.toLocalDate().atTime(nearestTimeOf(medication, moment)))
        world.rememberState()
        world.clock.moveTo(moment)
        world.mark(intake.id, result, source, world.clock.momentOf(moment))
        world.intakes.focusOn(intake)
    }

    /**
     * Время приёма, о котором говорит строка «в 14:05 … отмечает приём «Л»».
     *
     * Момент действия и время приёма — разные вещи: человек отмечает приём в 14:05, а сам
     * приём стоит на 14:00. Берётся ближайшее время расписания лекарства, не позже момента
     * действия; если таких нет — первое время расписания.
     */
    private fun nearestTimeOf(medication: String, moment: LocalDateTime): LocalTime {
        val times = world.medications.get(medication).times
        return times.filter { !it.isAfter(moment.toLocalTime()) }.maxOrNull()
            ?: times.minOrNull()
            ?: moment.toLocalTime()
    }

    /**
     * Приём, о котором говорит строка «приём «Л» профиля «П» считается …».
     *
     * Сначала — приём, отмеченный действием сценария: планировщик порождает приёмы
     * и на следующие сутки, и «последний по расписанию» — не тот, о котором идёт речь.
     */
    private fun intakeOf(medication: String, profile: String): Intake {
        val owner = world.profiles.get(profile).id
        val medicationId = world.medications.get(medication).id
        assertEquals(owner, world.medications.get(medication).profileId, "«$medication» must belong to «$profile»")
        return world.intakes.appliedMarks.values.lastOrNull { it.medicationId == medicationId }
            ?: world.intakes.of(medication).last()
    }

    /**
     * Приём лекарства, о котором говорит сценарий, — с разбором падежа.
     *
     * Сценарий называет лекарство так, как велит фраза («приём „Магния B6“ считается
     * принятым»), а модель хранит имя в начальной форме: сверять имена дословно
     * значило бы требовать от сценария канцелярита. Ищется приём последнего действия
     * сценария: планировщик заводит записи и на следующие сутки, и «последний по
     * расписанию» — не тот, о котором идёт речь.
     */
    private fun focused(medication: String): Intake {
        val name = world.medicationNamed(medication)
        val medicationId = world.medications.get(name).id
        world.intakes.currentId?.let { world.intakes.get(it) }
            ?.takeIf { it.medicationId == medicationId }
            ?.let { return it }
        return world.intakes.of(name).last { it.scheduledAt.toLocalDate() == world.clock.today() }
    }
}
