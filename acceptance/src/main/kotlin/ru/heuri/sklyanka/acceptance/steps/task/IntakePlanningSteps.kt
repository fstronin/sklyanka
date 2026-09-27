package ru.heuri.sklyanka.acceptance.steps.task

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.heuri.sklyanka.acceptance.RussianDates
import ru.heuri.sklyanka.acceptance.world.AcceptanceWorld
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.schedule.PlanReason
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Шаги приёмки SKL-06 — генерация приёмов из расписания.
 *
 * Здесь остался предмет самой задачи: окно генерации, запуск планировщика и проверки
 * состава плана. Допущения предметной области — профиль, момент, лекарство с расписанием
 * и курсом, уже отмеченный приём — переехали в общий словарь `steps/shared`, потому что
 * те же строки написаны в Предысториях других задач.
 *
 * Состояние сценария приезжает параметром конструктора: оно общее со всеми классами шагов
 * этого сценария, и шаг задачи видит то, что изменил общий шаг.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса: шаг обязан
 * совпадать с документом дословно, иначе прогон перестаёт быть контрактом задачи. Правило
 * RussianTextInCode разрешает пакет ru.heuri.sklyanka.acceptance вместе с подпакетами
 * именно поэтому (config/detekt/detekt.yml).
 *
 * Сценарии гоняют настоящий доменный сценарий `PlanIntakesUseCase` поверх хранилища
 * в памяти: домен для того и вынесен в чистый Kotlin, чтобы приёмка не требовала эмулятора.
 */
@Suppress("TooManyFunctions")
class IntakePlanningSteps(private val world: AcceptanceWorld) {

    // --- Предыстория --------------------------------------------------------------------

    @Given("^окно генерации приёмов — (\\S+) суток, считая текущие$")
    fun theGenerationWindowIs(depth: String) {
        world.depthDays = RussianDates.count(depth)
    }

    // --- Лекарства и курсы ----------------------------------------------------------------

    @Given("^у профиля «(.+?)» есть лекарство «(.+?)» в режиме «по потребности» не чаще (\\d+) раз в сутки$")
    fun theProfileHasAPrnMedication(profile: String, medication: String, maxPerDay: Int) {
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = medication,
            times = emptyList(),
            mode = ScheduleMode.PRN,
            maxPerDay = maxPerDay,
        )
    }

    @Given("^у профиля «(.+?)» нет лекарства «(.+?)»$")
    fun theProfileHasNoSuchMedication(profile: String, medication: String) {
        world.profiles.get(profile)
        assertFalse(world.medications.has(medication), "medication $medication must not exist yet")
    }

    @Given("^курс «(.+?)» (?:идёт|шёл) с (.+?) по (.+?)$")
    fun theCourseRunsBetween(medication: String, from: String, to: String) {
        val end = date(to)
        world.medications.update(medication) { it.copy(startDate = RussianDates.date(from, end), endDate = end) }
    }

    @Given("^курс «(.+?)» начался (.+?) и не имеет даты окончания$")
    fun theCourseIsOpenEnded(medication: String, from: String) {
        world.medications.update(medication) { it.copy(startDate = date(from), endDate = null) }
    }

    @Given("^«(.+?)» отправлено в архив (.+?)$")
    fun theMedicationWasArchivedOn(medication: String, on: String) {
        world.medications.archive(medication, date(on).atTime(NOON).atZone(world.clock.zone).toInstant())
    }

    @Given("^«(.+?)» находится в архиве$")
    fun theMedicationIsArchived(medication: String) {
        world.medications.archive(medication, world.clock.now())
    }

    // --- Уже созданные приёмы ----------------------------------------------------------------

    @Given("^приёмы курса уже созданы и отмечены$")
    fun theIntakesOfTheWholeCourseAreTaken() {
        val medication = world.medications.get(world.medications.last)
        val end = medication.endDate ?: world.clock.today()
        markCourse(
            world.medications.last,
            generateSequence(medication.startDate) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList(),
        )
    }

    @Given("^приём «(.+?)» на (.+?), (\\d{2}:\\d{2}) отмечен принятым$")
    fun thatSingleIntakeIsTaken(medication: String, date: String, time: String) {
        world.intakes.markTaken(medication, moment(date, time))
    }

    @Given("^приёмы «(.+?)» спланированы$")
    fun theIntakesAreAlreadyPlanned(medication: String) {
        world.plan()
        assertTrue(world.intakes.pendingOf(medication).isNotEmpty(), "nothing planned for $medication")
    }

    @Given("^приёмы «(.+?)» спланированы по (.+?) включительно$")
    fun theIntakesArePlannedUntil(medication: String, until: String) {
        world.plan()
        assertTrue(
            world.intakes.pendingOf(medication).any { it.scheduledAt.toLocalDate() == date(until) },
            "nothing planned for $until",
        )
    }

    @Given("^приёмы уже спланированы по (.+?) включительно$")
    fun theIntakesOfTheOnlyMedicationArePlannedUntil(until: String) {
        theIntakesArePlannedUntil(world.medications.last, until)
    }

    @Given("^приёмы «(.+?)» спланированы: (\\d+) ожидающихся приёмов$")
    fun theIntakesArePlannedAndCounted(medication: String, expected: Int) {
        world.plan()
        assertEquals(expected, world.intakes.pendingOf(medication).size)
    }

    @Given("^приложение уже спланировало приёмы$")
    fun theApplicationHasAlreadyPlanned() {
        world.plan()
    }

    // --- Действия ---------------------------------------------------------------------------

    @When("^приложение планирует приёмы$")
    fun theApplicationPlansIntakes() {
        world.rememberState()
        world.plan()
    }

    @When("^приложение планирует приёмы (\\d{1,2} \\S+ \\d{4})$")
    fun theApplicationPlansIntakesOn(date: String) {
        world.rememberState()
        world.clock.moveToDate(date(date))
        world.plan()
    }

    @When("^приложение планирует приёмы ещё раз$")
    fun theApplicationPlansIntakesAgain() {
        world.rememberState()
        world.plan()
    }

    @When("^приложение планирует приёмы три раза подряд (.+?) в (\\d{2}:\\d{2})$")
    fun theApplicationPlansIntakesThriceInARow(date: String, time: String) {
        world.rememberState()
        world.clock.moveTo(moment(date, time))
        repeat(PLAN_RUNS_IN_A_ROW) { world.plan() }
    }

    @When("^наступает (.+?), (\\d{2}:\\d{2}) и приложение планирует приёмы$")
    fun theTimeComesAndTheApplicationPlansIntakes(date: String, time: String) {
        world.rememberState()
        world.clock.moveTo(moment(date, time))
        world.plan()
    }

    @When(
        "^человек заводит «(.+?)» с приёмами в (\\d{2}:\\d{2}(?:(?:, | и )\\d{2}:\\d{2})*) " +
            "и бессрочным курсом с (.+?)$",
    )
    fun thePersonAddsAMedication(medication: String, times: String, from: String) {
        world.rememberState()
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = medication,
            times = RussianDates.times(times),
            startDate = date(from),
        )
    }

    @When("^«(.+?)» отправляют в архив (.+?) в (\\d{2}:\\d{2})$")
    fun theMedicationIsSentToTheArchive(medication: String, date: String, time: String) {
        world.rememberState()
        world.clock.moveTo(moment(date, time))
        world.medications.archive(medication, world.clock.now())
        // НАП-11: архивация — событие перепланирования, а не только правка карточки.
        world.planMedication(medication, PlanReason.MEDICATION_ARCHIVED)
    }

    @When("^«(.+?)» возвращают из архива (.+?) в (\\d{2}:\\d{2})$")
    fun theMedicationReturnsFromTheArchive(medication: String, date: String, time: String) {
        world.rememberState()
        world.clock.moveTo(moment(date, time))
        world.medications.unarchive(medication)
    }

    @When(
        "^(.+?) в (\\d{2}:\\d{2}) человек переносит (?:утренний|вечерний) приём " +
            "с (\\d{2}:\\d{2}) на (\\d{2}:\\d{2})$",
    )
    fun thePersonMovesOneTimeOfTheSchedule(date: String, time: String, from: String, to: String) {
        world.rememberState()
        world.clock.moveTo(moment(date, time))
        val name = world.medications.last
        val was = LocalTime.parse(from)
        val becomes = LocalTime.parse(to)
        world.medications.update(name) { medication ->
            medication.copy(times = medication.times.map { if (it == was) becomes else it })
        }
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) человек переносит окончание курса на (.+?)$")
    fun thePersonMovesTheEndOfTheCourse(date: String, time: String, to: String) {
        world.rememberState()
        world.clock.moveTo(moment(date, time))
        world.medications.update(world.medications.last) { it.copy(endDate = date(to)) }
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) человек убирает дату окончания курса$")
    fun thePersonDropsTheEndOfTheCourse(date: String, time: String) {
        world.rememberState()
        world.clock.moveTo(moment(date, time))
        world.medications.update(world.medications.last) { it.copy(endDate = null) }
    }

    @When("^системные часы переводят на (.+?), (\\d{2}:\\d{2})$")
    fun theSystemClockIsSetTo(date: String, time: String) {
        world.rememberState()
        world.clock.moveTo(moment(date, time))
    }

    // --- Проверки: сколько приёмов --------------------------------------------------------------

    @Then("^у «(.+?)» (?:по-прежнему )?ровно (\\d+) ожидающ\\S+ приём\\S*$")
    fun theMedicationHasExactlyThatManyPendingIntakes(medication: String, expected: Int) {
        assertEquals(expected, world.intakes.pendingOf(medication).size, describe(medication))
    }

    @Then("^(?:всего )?у профиля «(.+?)» ровно (\\d+) ожидающихся приёмов$")
    fun theProfileHasExactlyThatManyPendingIntakes(profile: String, expected: Int) {
        assertEquals(expected, world.intakes.pendingOfProfile(world.profiles, profile).size)
    }

    @Then("^на каждую из дат с (.+?) по (.+?) приходится ровно (\\S+) приёма$")
    fun everyDateOfTheRangeHoldsThatManyIntakes(from: String, to: String, count: String) {
        assertCountPerDate(
            world.medications.last,
            RussianDates.range(from, to, world.clock.today()),
            RussianDates.count(count),
        )
    }

    @Then("^на каждую дату с (.+?) по (.+?) приходится ровно (\\S+) приём$")
    fun everyDateOfTheRangeHoldsThatManyIntakesSingular(from: String, to: String, count: String) {
        assertCountPerDate(
            world.medications.last,
            RussianDates.range(from, to, world.clock.today()),
            RussianDates.count(count),
        )
    }

    @Then("^на каждую дату с (.+?) по (.+?) приходится ровно (\\S+) приём «(.+?)»$")
    fun everyDateOfTheRangeHoldsThatManyIntakesOf(from: String, to: String, count: String, medication: String) {
        assertCountPerDate(medication, RussianDates.range(from, to, world.clock.today()), RussianDates.count(count))
    }

    @Then("^на каждую дату курса приходится ровно (\\S+) приёма$")
    fun everyDateOfTheCourseHoldsThatManyIntakes(count: String) {
        val medication = world.medications.get(world.medications.last)
        val last = medication.endDate ?: world.clock.today().plusDays((world.depthDays - 1).toLong())
        val dates = generateSequence(maxOf(medication.startDate, world.clock.today())) { it.plusDays(1) }
            .takeWhile { !it.isAfter(last) }
            .toList()
        assertCountPerDate(world.medications.last, dates, RussianDates.count(count))
    }

    @Then("^на (.+?) у «(.+?)» ровно (\\S+) приёма$")
    fun thatDateHoldsThatManyIntakes(date: String, medication: String, count: String) {
        assertCountPerDate(medication, listOf(date(date)), RussianDates.count(count))
    }

    @Then("^на (.+?) у «(.+?)» ровно один приём — в (\\d{2}:\\d{2})$")
    fun thatDateHoldsASingleIntakeAt(date: String, medication: String, time: String) {
        assertEquals(
            listOf(LocalTime.parse(time)),
            timesOn(medication, date(date)),
            describe(medication),
        )
    }

    @Then("^на (.+?) у «(.+?)» есть приёмы в (\\d{2}:\\d{2}) и (\\d{2}:\\d{2})$")
    fun thatDateHoldsBothIntakes(date: String, medication: String, first: String, second: String) {
        assertEquals(listOf(LocalTime.parse(first), LocalTime.parse(second)), timesOn(medication, date(date)))
    }

    @Then("^на (.+?) приходятся приёмы в (\\d{2}:\\d{2}) и (\\d{2}:\\d{2})$")
    fun thatDateHoldsBothIntakesOfTheOnlyMedication(date: String, first: String, second: String) {
        assertEquals(
            listOf(LocalTime.parse(first), LocalTime.parse(second)),
            timesOn(world.medications.last, date(date)),
        )
    }

    @Then("^времена приёмов каждой даты — (.+?)(?: по местным часам)?$")
    fun everyDateHoldsTheseTimes(times: String) {
        val expected = RussianDates.times(times)
        val byDate = world.intakes.pendingOf(world.medications.last).groupBy { it.scheduledAt.toLocalDate() }
        assertTrue(byDate.isNotEmpty(), "no intakes planned at all")
        byDate.forEach { (date, ofDate) ->
            assertEquals(expected, ofDate.map { it.scheduledAt.toLocalTime() }.sorted(), "date $date")
        }
    }

    @Then("^все они приходятся на (.+?)$")
    fun allOfThemFallOnThatDate(date: String) {
        val expected = date(date)
        assertTrue(
            world.intakes.pendingOf(world.medications.last).all { it.scheduledAt.toLocalDate() == expected },
            describe(world.medications.last),
        )
    }

    @Then("^все они относятся к «(.+?)»$")
    fun allOfThemBelongToThatMedication(medication: String) {
        val expected = world.medications.get(medication).id
        val ofActiveProfile = world.intakes.pendingOfProfile(world.profiles, world.profiles.active)
        assertTrue(ofActiveProfile.all { it.medicationId == expected })
    }

    @Then("^последний из них приходится на (.+?), (\\d{2}:\\d{2})$")
    fun theLastOfThemFallsOn(date: String, time: String) {
        assertEquals(moment(date, time), world.intakes.pendingOf(world.medications.last).last().scheduledAt)
    }

    @Then("^самый ранний из них приходится на (.+?), (\\d{2}:\\d{2})$")
    fun theEarliestOfThemFallsOn(date: String, time: String) {
        assertEquals(moment(date, time), world.intakes.pendingOf(world.medications.last).first().scheduledAt)
    }

    // --- Проверки: чего быть не должно -------------------------------------------------------------

    @Then("^приёмов на (\\d{1,2}(?:, \\d{1,2})*(?: и \\d{1,2})? \\S+ \\d{4}) не существует$")
    fun noIntakesOnThoseDates(dates: String) {
        RussianDates.dates(dates, world.clock.today()).forEach { date ->
            assertTrue(intakesOn(world.medications.last, date).isEmpty(), "date $date")
        }
    }

    @Then("^приёмов на (.+?) в (\\d{2}:\\d{2}) и (\\d{2}:\\d{2}) не существует$")
    fun noIntakesAtThoseTimes(date: String, first: String, second: String) {
        val times = timesOn(world.medications.last, date(date))
        assertFalse(LocalTime.parse(first) in times)
        assertFalse(LocalTime.parse(second) in times)
    }

    @Then("^приёмов на даты после (.+?) не существует$")
    fun noIntakesAfterThatDate(date: String) {
        noIntakesOfThatMedicationAfterThatDate(world.medications.last, date)
    }

    @Then("^(?:ожидающихся )?приёмов «(.+?)» на даты после (.+?) не существует$")
    fun noIntakesOfThatMedicationAfterThatDate(medication: String, date: String) {
        val edge = date(date)
        assertTrue(
            world.intakes.pendingOf(medication).none { it.scheduledAt.toLocalDate() > edge },
            describe(medication),
        )
    }

    @Then("^приёмов «(.+?)» в (\\d{2}:\\d{2}) на даты после (.+?) не существует$")
    fun noIntakesAtThatTimeAfterThatDate(medication: String, time: String, date: String) {
        val edge = date(date)
        val at = LocalTime.parse(time)
        val stray = world.intakes.of(medication)
            .filter { it.scheduledAt.toLocalDate() > edge && it.scheduledAt.toLocalTime() == at }
        assertTrue(stray.isEmpty(), describe(medication))
    }

    @Then("^приёма на (.+?) у «(.+?)» не существует$")
    fun noIntakeOnThatDate(date: String, medication: String) {
        assertTrue(intakesOn(medication, date(date)).isEmpty())
    }

    @Then("^приёма на (.+?) не появляется$")
    fun noIntakeAppearsOnThatDate(date: String) {
        assertTrue(intakesOn(world.medications.last, date(date)).isEmpty())
    }

    @Then("^второго приёма (?:«(.+?)» )?на (.+?), (\\d{2}:\\d{2}) не появляется$")
    fun noSecondIntakeAtThatMoment(medication: String?, date: String, time: String) {
        val name = medication ?: world.medications.last
        assertEquals(1, world.intakes.of(name).count { it.scheduledAt == moment(date, time) })
    }

    @Then("^новых приёмов «(.+?)» не появляется$")
    fun noNewIntakesAppear(medication: String) {
        val added = world.intakes.of(medication).filterNot { it.id in world.before.keys }
        assertTrue(added.isEmpty(), "new intakes appeared: ${added.map { it.scheduledAt }}")
    }

    @Then("^ожидающихся приёмов «(.+?)» не существует ни на одну дату окна$")
    fun noPendingIntakesInTheWholeWindow(medication: String) {
        assertTrue(world.intakes.pendingOf(medication).isEmpty(), describe(medication))
    }

    @Then("^ожидающихся приёмов «(.+?)» не остаётся$")
    fun noPendingIntakesLeft(medication: String) {
        assertTrue(world.intakes.pendingOf(medication).isEmpty(), describe(medication))
    }

    @Then("^в списке приёмов профиля «(.+?)» нет ни одного приёма «(.+?)»$")
    fun theProfileListHoldsNoIntakeOfThatMedication(profile: String, medication: String) {
        val foreign = world.medications.get(medication).id
        assertTrue(world.intakes.ofProfile(world.profiles, profile).none { it.medicationId == foreign })
    }

    // --- Проверки: что осталось нетронутым ------------------------------------------------------------

    @Then("^у «(.+?)» появляется приём на (.+?), (\\d{2}:\\d{2})$")
    fun aNewIntakeAppearsAt(medication: String, date: String, time: String) {
        val at = moment(date, time)
        val intake = world.intakes.of(medication).firstOrNull { it.scheduledAt == at }
        assertNotNull(intake, "no intake at $at")
        assertFalse(requireNotNull(intake).id in world.before.keys, "the intake is not a new one")
    }

    @Then("^приём «(.+?)» на (.+?), (\\d{2}:\\d{2}) остаётся в списке приёмов с прежним состоянием$")
    fun thatIntakeStaysUntouched(medication: String, date: String, time: String) {
        val intake = intakeAt(medication, moment(date, time))
        assertEquals(world.before[intake.id], intake)
    }

    @Then("^приём «(.+?)» на (.+?), (\\d{2}:\\d{2}) остаётся тем же приёмом, что и до правки$")
    fun thatIntakeKeepsItsIdentity(medication: String, date: String, time: String) {
        val intake = intakeAt(medication, moment(date, time))
        assertTrue(intake.id in world.before.keys, "the intake was recreated and lost its alarm")
        assertEquals(world.before[intake.id], intake)
    }

    @Then("^приёмы «(.+?)» с (.+?) по (.+?) остаются на месте$")
    fun theIntakesOfThatRangeStayInPlace(medication: String, from: String, to: String) {
        val dates = RussianDates.range(from, to, world.clock.today()).toSet()
        val kept = world.before.values.filter { it.scheduledAt.toLocalDate() in dates }
        assertTrue(kept.isNotEmpty(), "nothing to keep")
        kept.forEach { intake -> assertEquals(intake, world.intakes.of(medication).firstOrNull { it.id == intake.id }) }
    }

    @Then("^приёмы «(.+?)» с (.+?) по (.+?) приходятся на (\\d{2}:\\d{2}) и (\\d{2}:\\d{2})$")
    fun theIntakesOfThatRangeFallOnThoseTimes(
        medication: String,
        from: String,
        to: String,
        first: String,
        second: String,
    ) {
        val expected = listOf(LocalTime.parse(first), LocalTime.parse(second))
        RussianDates.range(from, to, world.clock.today()).forEach { date ->
            assertEquals(expected, timesOn(medication, date), "date $date")
        }
    }

    @Then("^приёмы «(.+?)» на вечер с (.+?) по (.+?) приходятся на (\\d{2}:\\d{2})$")
    fun theEveningIntakesOfThatRangeFallOnThatTime(medication: String, from: String, to: String, time: String) {
        val expected = LocalTime.parse(time)
        RussianDates.range(from, to, world.clock.today()).forEach { date ->
            val evening = timesOn(medication, date).filter { it >= NOON }
            assertEquals(listOf(expected), evening, "date $date")
        }
    }

    // --- Вспомогательное ------------------------------------------------------------------------------

    private fun markCourse(medication: String, dates: List<LocalDate>) {
        val times = world.medications.get(medication).times
        dates.forEach { date -> times.forEach { time -> world.intakes.markTaken(medication, date.atTime(time)) } }
    }

    private fun assertCountPerDate(medication: String, dates: List<LocalDate>, expected: Int) {
        dates.forEach { date ->
            assertEquals(expected, intakesOn(medication, date).size, "date $date of $medication")
        }
    }

    private fun intakesOn(medication: String, date: LocalDate): List<Intake> =
        world.intakes.pendingOf(medication).filter { it.scheduledAt.toLocalDate() == date }

    private fun timesOn(medication: String, date: LocalDate): List<LocalTime> =
        intakesOn(medication, date).map { it.scheduledAt.toLocalTime() }.sorted()

    private fun intakeAt(medication: String, at: LocalDateTime): Intake =
        requireNotNull(world.intakes.of(medication).firstOrNull { it.scheduledAt == at }) { "no intake at $at" }

    private fun date(text: String): LocalDate = RussianDates.date(text, world.clock.today())

    private fun moment(date: String, time: String): LocalDateTime = date(date).atTime(LocalTime.parse(time))

    private fun describe(medication: String): String =
        "intakes of $medication: " + world.intakes.pendingOf(medication).map { it.scheduledAt }

    private companion object {
        /** «Три раза подряд» из текста сценария: повторный запуск не должен дублировать приёмы. */
        const val PLAN_RUNS_IN_A_ROW = 3

        val NOON: LocalTime = LocalTime.of(12, 0)
    }
}
