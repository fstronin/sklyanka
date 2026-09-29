package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.RussianNames
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.today.PartOfDay
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Предыстория плотного дня и групповой отметки (SKL-08, группы `@dense` и `@group-marking`).
 *
 * Объявлена в общем артефакте, а не в модуле экрана (SKL-26, TDD §4.5): эти строки
 * говорят о приёмах и остатках, то есть о домене, и читает их не только экранный прогон.
 *
 * Числа приёмов сценарий называет словами («восемь», «тринадцать»), а сутки описаны
 * поверх общей Предыстории задачи: у профиля уже есть пять приёмов по расписанию,
 * и фикстура доводит их до названного сценарием состояния, а не молча его подменяет.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class TodayDenseSteps(private val world: AcceptanceWorld) {

    // --- Плотный день -------------------------------------------------------------------------

    @Given("^у профиля (\\d+) запланированных приёмов на (.+?) у (\\d+) лекарств(?: по расписанию)?$")
    fun theProfileHasThatManyScheduledIntakes(intakeCount: Int, date: String, medicationCount: Int) {
        val day = world.dateOf(date)
        val scheduled = scheduledIntakesOf(day)
        val active = world.medications.all().count { it.scheduleMode == ScheduleMode.SCHEDULED && !it.isArchived }
        val missingMedications = medicationCount - active
        require(missingMedications > 0) {
            "the background already holds $active scheduled medications, no room for $medicationCount"
        }
        val missingTimes = missingIntakeTimes(scheduled, intakeCount)
        val perMedication = List(missingMedications) { mutableListOf<LocalTime>() }
        missingTimes.forEachIndexed { index, time -> perMedication[index % missingMedications] += time }
        perMedication.forEachIndexed { index, times ->
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = "Дополнительное ${index + 1}",
                times = times,
            )
        }
        world.plan()
        assertEquals(intakeCount, scheduledIntakesOf(day).size, "the day must hold $intakeCount scheduled intakes")
    }

    /**
     * Времена, которых не хватает суткам до сценария.
     *
     * Раскладка по времени суток — из самих сценариев: восемь утра, девять дня,
     * тринадцать вечера. Часы выбраны свободными от приёмов Предыстории (08:00, 14:00,
     * 21:00) и позже момента постановки на планирование (07:30): приём, время которого
     * уже прошло, планировщик не создаёт, и сутки оказались бы короче названных.
     */
    private fun missingIntakeTimes(scheduled: List<Intake>, intakeCount: Int): List<LocalTime> {
        val missing = TARGETS.flatMap { (part, target) ->
            val present = scheduled.count { PartOfDay.of(it.scheduledAt.toLocalTime()) == part }
            val slots = requireNotNull(SLOTS[part])
            require(slots.size >= target - present) {
                "$part already holds $present intakes, and ${slots.size} slots cannot reach $target"
            }
            slots.take(target - present).map(LocalTime::parse)
        }
        assertEquals(
            intakeCount - scheduled.size,
            missing.size,
            "the fixture must add exactly the missing intakes",
        )
        return missing
    }

    @Given("^приёмы утра с (\\d{2}:\\d{2}) до (\\d{2}:\\d{2}) — ([а-я]+), и все они отмечены$")
    fun theMorningIntakesAreAllMarked(from: String, to: String, count: String) {
        val intakes = intakesBetween(from, to)
        val expected = RussianDates.count(count)
        assertEquals(expected, intakes.size, "the morning must hold $expected intakes")
        intakes.forEach(::markTaken)
    }

    @Given("^три приёма дня уже отмечены принятыми$")
    fun threeDayIntakesAreTaken() {
        val day = intakesBetween(DAY_FROM, DAY_TO)
        require(day.size >= GROUP_SIZE) { "only ${day.size} day intakes, cannot mark $GROUP_SIZE" }
        day.sortedBy { it.scheduledAt }.take(GROUP_SIZE).forEach(::markTaken)
    }

    @Given("^приёмы вечера с (\\d{2}:\\d{2}) до (\\d{2}:\\d{2}) — ([а-я]+), и ни один ещё не наступил$")
    fun theEveningIntakesAreAllAhead(from: String, to: String, count: String) {
        val intakes = intakesBetween(from, to)
        val expected = RussianDates.count(count)
        assertEquals(expected, intakes.size, "the evening must hold $expected intakes")
        intakes.forEach { intake ->
            assertTrue(intake.isPending, "the evening intake at ${intake.scheduledAt} must still be pending")
            assertFalse(
                intake.scheduledAt.isBefore(world.clock.nowLocal()),
                "the evening intake at ${intake.scheduledAt} must not have come yet",
            )
        }
    }

    @Given("^(\\d+) из них уже отмечены принятыми$")
    fun thatManyOfThemAreTaken(count: Int) {
        scheduledIntakesOf(world.clock.today()).sortedBy { it.scheduledAt }.take(count).forEach(::markTaken)
    }

    @Given("^кроме них у профиля есть (\\d+) лекарства по потребности$")
    fun theProfileAlsoHasThatManyOnDemandMedications(count: Int) {
        val existing = onDemandMedications().size
        require(existing <= count) { "the background already holds $existing on-demand medications" }
        for (index in existing until count) {
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = "По потребности ${index + 1}",
                mode = ScheduleMode.PRN,
                maxPerDay = 3,
            )
        }
        assertEquals(count, onDemandMedications().size, "the profile must hold $count on-demand medications")
    }

    // --- Одновременные приёмы ------------------------------------------------------------------

    @Given("^на (.+?), (\\d{2}:\\d{2}) (?:назначены|ожидаются) приёмы «([^»]+)», «([^»]+)» и «([^»]+)»$")
    fun thoseNamedIntakesAreExpected(date: String, time: String, first: String, second: String, third: String) {
        val moment = world.localMoment(date, time)
        listOf(first, second, third).map(::ensureMedication).forEach { name -> schedule(name, moment) }
        world.plan()
        listOf(first, second, third).map(::ensureMedication).forEach { name ->
            assertTrue(
                world.intakes.of(name).any { it.scheduledAt == moment },
                "no intake of $name at $moment",
            )
        }
    }

    @Given("^на (.+?), (\\d{2}:\\d{2}) назначено (\\d+) одновременных приёмов$")
    fun thatManySimultaneousIntakesAreExpected(date: String, time: String, count: Int) {
        val moment = world.localMoment(date, time)
        val present = intakesAt(moment).size
        require(present <= count) { "the background already holds $present intakes at $moment" }
        for (index in present until count) {
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = "Одновременное ${index + 1}",
                times = listOf(moment.toLocalTime()),
            )
        }
        world.plan()
        assertEquals(count, intakesAt(moment).size, "the time $moment must hold $count simultaneous intakes")
    }

    // --- Групповая отметка -----------------------------------------------------------------------

    @Given("^запись отметки «([^»]+)» не может быть выполнена$")
    fun theWriteOfThatIntakeCannotBePerformed(medication: String) {
        world.failWriteFor(world.medicationNamed(medication))
    }

    @Given("^приём «([^»]+)» отмечен пропущенным осознанно в (\\d{2}:\\d{2})$")
    fun thatIntakeIsSkippedOnPurpose(medication: String, time: String) {
        val name = world.medicationNamed(medication)
        val intake = requireNotNull(todayIntakesOf(name).firstOrNull()) { "no intake of $name today" }
        world.markExisting(
            medication = name,
            at = intake.scheduledAt,
            markedAt = LocalTime.parse(time),
            result = MarkResult.SKIPPED,
        )
    }

    // --- Проверки групповой отметки --------------------------------------------------------------

    @Then("^все три приёма считаются принятыми$")
    fun allThreeIntakesAreTaken() {
        val ids = groupIntakes()
        assertEquals(GROUP_SIZE, ids.size, "the group of the scenario must hold three intakes, it holds ${ids.size}")
        ids.forEach { id -> assertEquals(IntakeStatus.TAKEN, statusOf(id), "intake $id") }
    }

    @Then("^ни один из трёх приёмов не считается принятым$")
    fun noneOfTheThreeIntakesIsTaken() {
        val ids = groupIntakes()
        assertEquals(GROUP_SIZE, ids.size, "the group of the scenario must hold three intakes, it holds ${ids.size}")
        ids.forEach { id ->
            assertNotEquals(IntakeStatus.TAKEN, statusOf(id), "intake $id must not be taken")
        }
    }

    @Then("^приёмы «([^»]+)» и «([^»]+)» считаются принятыми$")
    fun thoseTwoIntakesAreTaken(first: String, second: String) {
        listOf(first, second).forEach { name ->
            val intake = todayIntakeOf(world.medicationNamed(name))
            assertEquals(IntakeStatus.TAKEN, intake.status, "the intake of $name")
        }
    }

    @Then("^приём «([^»]+)» остаётся пропущенным осознанно$")
    fun thatIntakeStaysSkippedOnPurpose(medication: String) {
        val intake = todayIntakeOf(world.medicationNamed(medication))
        assertEquals(IntakeStatus.SKIPPED, intake.status, "the intake of $medication")
    }

    @Then("^приёмы «([^»]+)» и «([^»]+)» остаются ожидающимися$")
    fun thoseTwoIntakesStayPending(first: String, second: String) {
        listOf(first, second).forEach { name ->
            val intake = todayIntakeOf(world.medicationNamed(name))
            assertEquals(IntakeStatus.PENDING, intake.status, "the intake of $name")
        }
    }

    @Then("^ни один приём не отмечен повторно$")
    fun noIntakeIsMarkedAgain() {
        // Повторная отметка уже принятого приёма отброшена доменом (ПР-2): состояние
        // приёмов не изменилось — по-прежнему приняты, у каждого свой момент отметки.
        val ids = groupIntakes()
        assertTrue(ids.isNotEmpty(), "the scenario never marked a group")
        ids.forEach { id ->
            val intake = requireNotNull(world.intakes.get(id)) { "unknown intake: $id" }
            assertEquals(IntakeStatus.TAKEN, intake.status, "intake $id")
            assertNotNull(intake.actedAt, "intake $id must keep its mark moment")
        }
    }

    @Then("^временем отметки каждого остаётся (.+?), (\\d{2}:\\d{2})$")
    fun theMomentOfEachMarkStays(date: String, time: String) {
        val moment = world.clock.momentOf(world.localMoment(date, time))
        val ids = groupIntakes()
        assertTrue(ids.isNotEmpty(), "the scenario never marked a group")
        ids.forEach { id ->
            assertEquals(moment, requireNotNull(world.intakes.get(id)).actedAt, "intake $id")
        }
    }

    @Then("^остатки всех трёх лекарств не изменились$")
    fun theStocksOfAllThreeStay() {
        val ids = groupIntakes()
        assertEquals(GROUP_SIZE, ids.size, "the group of the scenario must hold three intakes, it holds ${ids.size}")
        ids.forEach { id ->
            val intake = requireNotNull(world.intakes.get(id)) { "unknown intake: $id" }
            val name = scenarioNameOf(intake.medicationId)
            assertEquals(world.medications.stockBefore(name), world.medications.stockOf(name), "the stock of $name")
        }
    }

    @Then("^временем отметки каждого записано (.+?), (\\d{2}:\\d{2})$")
    fun theMomentOfEachMarkIs(date: String, time: String) {
        val moment = world.clock.momentOf(world.localMoment(date, time))
        val ids = groupIntakes()
        assertTrue(ids.isNotEmpty(), "the scenario never marked a group")
        ids.forEach { id ->
            assertEquals(moment, requireNotNull(world.intakes.get(id)).actedAt, "intake $id")
        }
    }

    // --- Остаток упаковки ------------------------------------------------------------------------

    @Given("^остаток «([^»]+)» — (\\d+) таблет\\S+ при дозе (\\S+) таблетк\\S+$")
    fun theStockAndTheDoseAre(medication: String, amount: Int, dose: String) {
        world.medications.update(world.medicationNamed(medication)) {
            it.copy(stockLeft = BigDecimal(amount), doseAmount = RussianDates.amount(dose))
        }
    }

    @Given("^остаток «([^»]+)» не ведётся$")
    fun theStockIsNotTracked(medication: String) {
        world.medications.update(world.medicationNamed(medication)) { it.copy(stockLeft = null) }
    }

    @Then("^остаток «([^»]+)» — (\\d+) таблет\\S+$")
    fun theStockIs(medication: String, amount: Int) {
        assertEquals(
            BigDecimal(amount),
            world.medications.stockOf(world.medicationNamed(medication)),
            "the stock of $medication",
        )
    }

    @Then("^остаток «([^»]+)» не изменяется$")
    fun theStockStays(medication: String) {
        val name = world.medicationNamed(medication)
        assertEquals(world.medications.stockBefore(name), world.medications.stockOf(name), "the stock of $name")
    }

    // --- Вспомогательное -------------------------------------------------------------------------

    private fun intakesBetween(from: String, to: String): List<Intake> {
        val start = LocalTime.parse(from)
        val end = LocalTime.parse(to)
        return scheduledIntakesOf(world.clock.today()).filter {
            it.scheduledAt.toLocalTime() in start..end
        }
    }

    private fun scheduledIntakesOf(day: LocalDate): List<Intake> =
        world.intakes.ofProfile(world.profiles, world.profiles.active)
            .filter { it.scheduledAt.toLocalDate() == day && isScheduled(it) }

    private fun isScheduled(intake: Intake): Boolean =
        world.medications.byId(intake.medicationId)?.scheduleMode == ScheduleMode.SCHEDULED

    private fun onDemandMedications() =
        world.medications.all().filter { it.scheduleMode == ScheduleMode.PRN && !it.isArchived }

    private fun intakesAt(moment: LocalDateTime): List<Intake> =
        world.intakes.ofProfile(world.profiles, world.profiles.active).filter { it.scheduledAt == moment }

    /** Отметка как данность Предыстории: остаток такая строка не трогает. */
    private fun markTaken(intake: Intake) {
        world.intakes.markTaken(scenarioNameOf(intake.medicationId), intake.scheduledAt)
    }

    private fun todayIntakesOf(name: String): List<Intake> =
        world.intakes.of(name).filter { it.scheduledAt.toLocalDate() == world.clock.today() }

    private fun todayIntakeOf(name: String): Intake = requireNotNull(todayIntakesOf(name).firstOrNull()) {
        "no intake of $name today"
    }

    /**
     * Приёмы группы, о которой говорит сценарий: «все три» и «каждый».
     *
     * После второго нажатия их называет домен (`lastGroupIds`), до него — арм кнопки
     * (`lastArmedIds`): первое нажатие группе ещё ничего не отметило (ОВ-32).
     */
    private fun groupIntakes(): List<String> = world.intakes.lastGroupIds.ifEmpty { world.intakes.lastArmedIds }

    private fun statusOf(intakeId: String): IntakeStatus =
        requireNotNull(world.intakes.get(intakeId)) { "unknown intake: $intakeId" }.status

    /**
     * Лекарство из фразы сценария — у **активного** профиля.
     *
     * Известное имя разрешается по падежу; если оно уже занято другим профилем,
     * заводится своё: экран показывает приёмы активного профиля, и приёмы чужого
     * группы не создадут (ПРФ-10). Новое имя заводится как есть.
     */
    private fun ensureMedication(text: String): String {
        val key = RussianNames.resolve(text, world.medications.names()) ?: text
        val owner = world.profiles.get(world.profiles.active).id
        if (world.medications.has(key) && world.medications.get(key).profileId == owner) return key
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = key,
        )
        return key
    }

    private fun schedule(name: String, moment: LocalDateTime) {
        world.medications.update(name) { medication ->
            if (moment.toLocalTime() in medication.times) {
                medication
            } else {
                medication.copy(times = medication.times + moment.toLocalTime())
            }
        }
    }

    private fun scenarioNameOf(medicationId: String): String = requireNotNull(
        world.medications.names().firstOrNull { world.medications.get(it).id == medicationId },
    ) { "unknown medication id: $medicationId" }

    private companion object {
        const val DAY_FROM = "12:00"
        const val DAY_TO = "17:00"

        /** Три приёма — размер группы, о которой говорят сценарии. */
        const val GROUP_SIZE = 3

        /** Раскладка суток из сценария: восемь утра, девять дня, тринадцать вечера. */
        val TARGETS = mapOf(PartOfDay.MORNING to 8, PartOfDay.DAY to 9, PartOfDay.EVENING to 13)

        /**
         * Свободные часы каждого времени суток.
         *
         * Начинаются позже 07:30 — момента, в котором сценарий ставит часы, — иначе
         * планировщик не создал бы приём: его время уже прошло. Часы Предыстории
         * (08:00, 14:00, 21:00) занимать не обязательно — приёмы одного времени
         * идут одной группой, — но оставлены свободными ради читаемости сценария.
         *
         * Часов столько, сколько нужно самому скудному фону: у фона SKL-08 утро и вечер
         * уже держат приёмы, у фона SKL-31 — нет, и добавка доводит сутки до восьми утра
         * и тринадцати вечера. Новые часы дописаны в конец: сценарии предыдущих задач
         * берут первые недостающие, и их сутки не меняются.
         */
        val SLOTS = mapOf(
            PartOfDay.MORNING to listOf("07:45", "08:30", "09:00", "09:30", "10:00", "10:30", "11:00", "11:30"),
            PartOfDay.DAY to listOf("12:00", "12:30", "13:00", "13:30", "15:00", "15:30", "16:00", "16:30"),
            PartOfDay.EVENING to listOf(
                "18:00", "18:15", "18:30", "19:00", "19:30", "20:00",
                "20:30", "21:15", "22:00", "22:30", "23:00", "18:45", "19:45",
            ),
        )
    }
}
