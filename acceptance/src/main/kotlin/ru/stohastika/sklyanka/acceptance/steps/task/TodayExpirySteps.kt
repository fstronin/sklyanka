package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.intake.SweepReason
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import java.time.LocalTime

/**
 * Шаги SKL-08 о просроченных приёмах и отметке с часов (SKL-26, группа «Отметка приёма
 * с экрана»).
 *
 * Это предметная область, а не экран: «приём остался без отметки» и «окно истекло» —
 * состояния, которые продукт создаёт проходом по просроченным приёмам, и проверяются
 * они тем же доменным проходом, что в приложении. Экранная часть сценариев живёт
 * в `feature-today`.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
class TodayExpirySteps(private val world: AcceptanceWorld) {

    // --- Предыстория: что осталось без отметки ----------------------------------

    @Given("^приём «([^»]+)» на (.+?), (\\d{2}:\\d{2}) остался без отметки$")
    fun thatIntakeWasLeftUnmarked(medication: String, date: String, time: String) {
        world.intakes.pending(world.medicationNamed(medication), world.localMoment(date, time))
    }

    @Given("^(.+?) приём «([^»]+)» на (\\d{2}:\\d{2}) остался без отметки$")
    fun thatIntakeWasLeftUnmarkedOn(date: String, medication: String, time: String) {
        world.intakes.pending(world.medicationNamed(medication), world.dateOf(date).atTime(LocalTime.parse(time)))
    }

    @Given("^приёмы «([^»]+)» и «([^»]+)» на (\\d{2}:\\d{2}) остались без отметки$")
    fun thoseTwoIntakesWereLeftUnmarked(first: String, second: String, time: String) {
        val moment = world.clock.today().atTime(LocalTime.parse(time))
        listOf(first, second).forEach { world.intakes.pending(world.medicationNamed(it), moment) }
    }

    @Given("^(.+?) телефон был выключен с (\\d{2}:\\d{2})$")
    fun thePhoneWasSwitchedOff(date: String, time: String) {
        // Выключенный телефон ничего не делает: ни один проход по просроченным приёмам
        // в это время не выполняется, и приёмы остаются неотмеченными.
        world.clock.moveTo(world.localMoment(date, time))
    }

    @Given("^приём «([^»]+)» на (.+?), (\\d{2}:\\d{2}) отмечен принятым с часов в (\\d{2}:\\d{2})$")
    fun thatIntakeWasTakenFromTheWatch(medication: String, date: String, time: String, markedAt: String) {
        val name = world.medicationNamed(medication)
        val moment = world.localMoment(date, time)
        world.intakes.pending(name, moment)
        // Нажатие на часах старше нажатия на экране, но доходит до телефона позже
        // (ЧАС-23): до доставки экран ещё предлагает кнопки, и именно поэтому сценарий
        // проверяет правило первенства по времени действия (ПР-3), а не отсутствие кнопок.
        world.pressOnWatch(MarkResult.TAKEN, moment.toLocalDate().atTime(LocalTime.parse(markedAt)))
    }

    // --- Действия: окно приёма истекло ------------------------------------------

    @Given("^окно приёма истекло, и приём считается пропущенным без отметки$")
    fun theIntakeWindowExpired() {
        // Окно приёма — вход сценария (ОВ-4). У продукта оно по умолчанию до конца суток,
        // раньше местной полуночи в этих сутках не наступает; проход идёт по границе окна
        // тем же доменным сценарием, что в приложении, без подмены состояния записью.
        val intake = world.intakes.current()
        world.clock.moveTo(intake.scheduledAt.toLocalDate().plusDays(1).atStartOfDay())
        world.sweep(SweepReason.TIMER)
        assertEquals(IntakeStatus.MISSED, world.intakes.get(intake.id)?.status, "the intake had to expire")
    }

    // --- Проверки: автоматический пропуск ---------------------------------------

    @Then("^приёмы «([^»]+)» и «([^»]+)» за (.+?) считаются пропущенными без отметки$")
    fun thoseTwoIntakesAreMissedWithoutAMark(first: String, second: String, date: String) {
        val day = world.dateOf(date)
        listOf(first, second).forEach { name ->
            val intakes = world.intakes.of(world.medicationNamed(name)).filter {
                it.scheduledAt.toLocalDate() == day
            }
            assertEquals(1, intakes.size, "the scenario must have exactly one intake of $name on $date")
            assertEquals(IntakeStatus.MISSED, intakes.single().status, "intake of $name on $date")
        }
    }

    @Then("^источником их отметки записано приложение, а не человек$")
    fun theSourceOfTheirMarkIsTheApplication() {
        val missed = world.intakes.ofProfile(world.profiles, world.profiles.active)
            .filter { it.status == IntakeStatus.MISSED }
        assertTrue(missed.isNotEmpty(), "the scenario must have missed intakes by now")
        missed.forEach { intake ->
            assertEquals(IntakeSource.AUTO, intake.source, "an automatic miss names the app as its source")
            assertNull(intake.actorProfileId, "an automatic miss has no person behind it")
        }
    }
}
