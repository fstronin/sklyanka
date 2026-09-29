package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.ScenarioTransactor
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.medication.ArchiveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.ArchiveOutcome
import ru.stohastika.sklyanka.domain.medication.RestoreMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.RestoreOutcome
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.schedule.PlanReason
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Предыстория SKL-11: архив курсов — состав, причины, история и возврат.
 *
 * Строки живут в общем артефакте, потому что они доменные: раскладывают лекарства
 * по причинам архивации, ведут их историю и возвращают курсы настоящим
 * `RestoreMedicationUseCase`. Экранные проверки — в `feature-meds` и `app`.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class ArchiveContextSteps(private val world: AcceptanceWorld) {

    /** Лекарство, о котором говорит последняя названная строка: «он», «его» следующих. */
    private var focused: String = ""

    /** Записи приёмов до архивации и до возврата: ими проверяется сохранность истории. */
    private var beforeArchiving: Map<String, Intake> = emptyMap()
    private var beforeRestoring: Map<String, Intake> = emptyMap()

    // --- Предыстория: архив из двенадцати лекарств --------------------------------

    /**
     * Архив таблицей: названные лекарства получают причину, итог и дату, остальные
     * добираются до двенадцати.
     *
     * Итог завершённого курса строится данными, а не подписью: курс разворачивается
     * в приёмы, `taken` из них отмечаются принятыми, остальные — пропущенными вручную.
     * Так подпись карточки считает домен, а не таблица сценария.
     */
    @Given("^в архиве двенадцать лекарств, среди них:$")
    fun theArchiveHoldsTwelveMedicationsAmongThem(table: DataTable) {
        val scenarioMoment = world.clock.nowLocal()
        table.entries().forEach { row ->
            val medication = requireNotNull(row[MEDICATION_COLUMN]) { "no «$MEDICATION_COLUMN» in $row" }
            val reason = requireNotNull(row[REASON_COLUMN]) { "no «$REASON_COLUMN» in $row" }
            val archivedOn = world.dateOf(requireNotNull(row[ARCHIVED_COLUMN]) { "no «$ARCHIVED_COLUMN» in $row" })
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = medication,
                times = TWO_TIMES,
                startDate = archivedOn.minusDays((COURSE_DAYS - 1).toLong()),
                endDate = archivedOn,
            )
            focused = medication
            if (reason == FINISHED_LABEL) {
                val (days, taken, planned) = outcomeNumbers(
                    requireNotNull(row[OUTCOME_COLUMN]) {
                        "no outcome in $row"
                    },
                )
                buildCourse(medication, archivedOn, days, taken, planned)
            }
            archiveWithReason(medication, archivedOn.atTime(ARCHIVE_HOUR, 0), archiveReason(reason))
            world.clock.moveTo(scenarioMoment)
        }
        repeat(ARCHIVE_SIZE - table.entries().size) { index ->
            val medication = "архивный курс ${index + 1}"
            val archivedOn = world.clock.today().minusDays((index + 2).toLong())
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = medication,
                times = listOf(MORNING),
                startDate = archivedOn.minusDays((COURSE_DAYS - 1).toLong()),
                endDate = archivedOn,
            )
            archiveWithReason(medication, archivedOn.atTime(ARCHIVE_HOUR, 0), ArchiveReason.COURSE_FINISHED)
        }
    }

    @Given("^в архиве профиля нет ни одного лекарства с причиной «убрано вручную»$")
    fun theArchiveHasNoManuallyArchivedMedications() {
        archivedOfTheActiveProfile()
            .filterNot { it.archiveReason == ArchiveReason.COURSE_FINISHED }
            .forEach { world.medications.remove(keyOf(it.id)) }
        focused = ""
    }

    @Given("^в архиве профиля нет ни одного лекарства$")
    fun theArchiveIsEmpty() {
        archivedOfTheActiveProfile().forEach { world.medications.remove(keyOf(it.id)) }
        focused = ""
    }

    @Given("^в архиве профиля семь завершённых курсов и пять убранных вручную$")
    fun theArchiveHoldsSevenFinishedAndFiveManuallyArchived() {
        val archived = archivedOfTheActiveProfile()
        assertEquals(ARCHIVE_SIZE, archived.size, "the archive must hold twelve medications")
        archived.forEachIndexed { index, medication ->
            world.medications.update(keyOf(medication.id)) {
                it.copy(
                    archiveReason = if (index <
                        FINISHED_COUNT
                    ) {
                        ArchiveReason.COURSE_FINISHED
                    } else {
                        ArchiveReason.MANUAL
                    },
                )
            }
        }
    }

    @Given("^у профиля «([^»]+)» есть архивированное лекарство «([^»]+)»$")
    fun thatProfileHasAnArchivedMedication(profile: String, medication: String) {
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = medication,
            times = listOf(MORNING),
            startDate = world.clock.today().minusDays((COURSE_DAYS - 1).toLong()),
            endDate = world.clock.today(),
        )
        archiveWithReason(medication, world.clock.nowLocal().withHour(ARCHIVE_HOUR).withMinute(0), ArchiveReason.MANUAL)
    }

    // --- Предыстория: архивация и возврат одного лекарства -------------------------

    @Given("^лекарство «([^»]+)» с приёмами в (\\d{2}:\\d{2}) и (\\d{2}:\\d{2}) отправлено в архив (.+)$")
    fun thatMedicationWithTwoTimesIsArchived(medication: String, first: String, second: String, date: String) {
        val name = world.medicationNamed(medication)
        makeRunning(name, listOf(LocalTime.parse(first), LocalTime.parse(second)))
        archiveAt(name, world.dateOf(date).atTime(ARCHIVE_HOUR, 0))
    }

    @Given("^лекарство «([^»]+)» с приёмом в (\\d{2}:\\d{2}) отправлено в архив (.+)$")
    fun thatMedicationWithOneTimeIsArchived(medication: String, time: String, date: String) {
        val name = world.medicationNamed(medication)
        makeRunning(name, listOf(LocalTime.parse(time)))
        archiveAt(name, world.dateOf(date).atTime(ARCHIVE_HOUR, 0))
    }

    @When("^лекарство отправлено в архив$")
    fun theFocusedMedicationIsArchived() {
        val name = requireNotNull(focused.takeIf { it.isNotEmpty() }) { "no medication was named" }
        beforeArchiving = world.intakes.of(name).associateBy { it.id }
        archiveAt(name, world.clock.nowLocal())
    }

    @Then("^новых приёмов «([^»]+)» не создано$")
    fun noNewIntakesOfThatMedicationAreCreated(medication: String) {
        val name = world.medicationNamed(medication)
        val before = world.intakes.of(name).map { it.id }.toSet()
        // Планировщик — тот же, что в продукте: архивный курс не порождает приёмов (ПР-11).
        world.plan()
        assertEquals(
            before,
            world.intakes.of(name).map { it.id }.toSet(),
            "an archived medication must not produce new intakes (ПР-11)",
        )
    }

    @Then("^будильников «([^»]+)» в ближайшем окне нет$")
    fun noAlarmsOfThatMedicationAreScheduled(medication: String) {
        val name = world.medicationNamed(medication)
        val ownIntakes = world.intakes.of(name).map { it.id }.toSet()
        assertTrue(
            world.reminders.scheduled().keys.none { it in ownIntakes },
            "an archived medication must stay out of the window of alarms (ПР-11)",
        )
    }

    // --- Предыстория: история приёмов ----------------------------------------------

    @Given("^у «([^»]+)» есть (\\d+) принятых и (\\d+) пропущенн\\S* приём\\S* с (\\d+) по (.+)$")
    fun thatMedicationHasTakenAndSkippedIntakes(
        medication: String,
        taken: Int,
        skipped: Int,
        from: String,
        to: String,
    ) {
        val name = world.medicationNamed(medication)
        focused = name
        val dates = RussianDates.range(from, to, world.clock.today())
        val moments = courseMoments(dates)
        assertEquals(
            taken + skipped,
            moments.size,
            "the scenario names ${taken + skipped} intakes, the course gives ${moments.size}",
        )
        world.medications.update(name) {
            it.copy(startDate = dates.first(), endDate = dates.last(), archivedAt = null, archiveReason = null)
        }
        // История Предыстории заменяется историей сценария, а не дополняется ею.
        world.intakes.removeOf(world.medications.get(name).id)
        markCourse(name, moments, taken = taken)
    }

    @Given("^у «([^»]+)» есть (\\d+) записей приёмов с (\\d+) по (.+)$")
    fun thatMedicationHasThatManyIntakeRecords(medication: String, records: Int, from: String, to: String) {
        val name = world.medicationNamed(medication)
        focused = name
        val dates = RussianDates.range(from, to, world.clock.today())
        val moments = courseMoments(dates)
        assertEquals(records, moments.size, "the scenario names $records records, the course gives ${moments.size}")
        // Возврат возможен только у незакончившегося курса: сценарий говорит о возврате,
        // и предпосылка доводит курс до возвращаемого состояния.
        world.medications.update(name) {
            it.copy(startDate = dates.first(), endDate = null, archivedAt = null, archiveReason = null)
        }
        val scenarioMoment = world.clock.nowLocal()
        world.intakes.removeOf(world.medications.get(name).id)
        moments.forEach { moment -> world.intakes.markTaken(name, moment) }
        archiveWithReason(name, scenarioMoment, ArchiveReason.COURSE_FINISHED)
        world.clock.moveTo(scenarioMoment)
    }

    @Then("^все (\\d+) записей приёмов остаются$")
    fun allThoseRecordsStay(records: Int) {
        assertEquals(records, beforeArchiving.size, "the archive must not touch the history")
        beforeArchiving.keys.forEach { id ->
            assertNotNull(world.intakes.get(id), "the intake $id must stay after archiving (ПР-11)")
        }
    }

    @Then("^их отметки и время отметок не меняются$")
    fun theirStatusesAndMarkMomentsStay() {
        beforeArchiving.forEach { (id, before) ->
            val after = requireNotNull(world.intakes.get(id)) { "the intake $id disappeared" }
            assertEquals(before.status, after.status, "the status of $id must not change")
            assertEquals(before.actedAt, after.actedAt, "the mark moment of $id must not change (НАП-31)")
        }
    }

    @Then("^назначенное время каждого приёма не переписывается$")
    fun theScheduledTimeOfEveryIntakeStays() {
        beforeArchiving.forEach { (id, before) ->
            val after = requireNotNull(world.intakes.get(id)) { "the intake $id disappeared" }
            assertEquals(before.scheduledAt, after.scheduledAt, "the scheduled time of $id must not be rewritten")
        }
    }

    // --- Возврат из архива -----------------------------------------------------------

    @Given("^«([^»]+)» находится в архиве с причиной «курс завершён»$")
    fun thatMedicationIsArchivedAsFinished(medication: String) {
        val name = world.medicationNamed(medication)
        focused = name
        // Возврат возможен только у незакончившегося курса: сценарий проверяет возврат,
        // а не отказ по прошедшей дате окончания.
        world.medications.update(name) { it.copy(endDate = null, archiveReason = ArchiveReason.COURSE_FINISHED) }
        assertTrue(world.medications.get(name).isArchived, "«$medication» must already be archived")
    }

    @Given(
        "^«([^»]+)» был архивирован с расписанием «(\\d{2}:\\d{2})» и «(\\d{2}:\\d{2})», " +
            "дозой (\\d+) таблетка и связью с едой «([^»]+)»$",
    )
    fun thatMedicationWasArchivedWithThoseFacts(
        medication: String,
        first: String,
        second: String,
        dose: Int,
        food: String,
    ) {
        val name = world.medicationNamed(medication)
        focused = name
        world.medications.update(name) {
            it.copy(
                times = listOf(LocalTime.parse(first), LocalTime.parse(second)),
                doseAmount = BigDecimal(dose),
                foodRelation = foodRelation(food),
                endDate = null,
            )
        }
        assertTrue(world.medications.get(name).isArchived, "«$medication» must already be archived")
    }

    @Given("^«([^»]+)» с приёмами в (\\d{2}:\\d{2}) и (\\d{2}:\\d{2}) находится в архиве$")
    fun thatMedicationWithTwoTimesIsArchived(medication: String, first: String, second: String) {
        val name = world.medicationNamed(medication)
        focused = name
        world.medications.update(name) {
            it.copy(
                times = listOf(LocalTime.parse(first), LocalTime.parse(second)),
                endDate = null,
            )
        }
        assertTrue(world.medications.get(name).isArchived, "«$medication» must already be archived")
    }

    @Given("^«([^»]+)» был бессрочным курсом и находится в архиве$")
    fun thatOpenEndedMedicationIsArchived(medication: String) {
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = medication,
            times = listOf(MORNING),
            startDate = world.clock.today().minusDays((COURSE_DAYS - 1).toLong()),
            endDate = null,
        )
        focused = medication
        archiveWithReason(medication, world.clock.nowLocal().withHour(ARCHIVE_HOUR).withMinute(0), ArchiveReason.MANUAL)
    }

    @Given("^«([^»]+)» находится в архиве, и его дата окончания — (.+)$")
    fun thatMedicationIsArchivedWithThatEndDate(medication: String, end: String) {
        val name = world.medicationNamed(medication)
        focused = name
        world.medications.update(name) { it.copy(endDate = world.dateOf(end)) }
        assertTrue(world.medications.get(name).isArchived, "«$medication» must already be archived")
    }

    @When("^человек возвращает его из архива (.+?) в (\\d{2}:\\d{2})$")
    fun thePersonRestoresItAt(date: String, time: String) {
        restoreFocused(world.dateOf(date).atTime(LocalTime.parse(time)))
    }

    @When("^человек возвращает его из архива$")
    fun thePersonRestoresIt() {
        restoreFocused(world.clock.nowLocal())
    }

    @Then("^у «([^»]+)» расписание «(\\d{2}:\\d{2})» и «(\\d{2}:\\d{2})»$")
    fun thatMedicationKeepsItsSchedule(medication: String, first: String, second: String) {
        val name = world.medicationNamed(medication)
        assertEquals(
            listOf(LocalTime.parse(first), LocalTime.parse(second)),
            world.medications.get(name).times.sorted(),
            "the restore must keep the schedule",
        )
    }

    @Then("^доза (\\d+) таблетка и связь с едой «([^»]+)»$")
    fun theDoseAndFoodRelationStay(dose: Int, food: String) {
        val name = requireNotNull(focused.takeIf { it.isNotEmpty() }) { "no medication was named" }
        val medication = world.medications.get(name)
        assertEquals(BigDecimal(dose), medication.doseAmount, "the restore must keep the dose")
        assertEquals(foodRelation(food), medication.foodRelation, "the restore must keep the food relation")
    }

    @Then("^приёмы «([^»]+)» порождаются на ближайшее окно$")
    fun theIntakesOfThatMedicationAreGenerated(medication: String) {
        val name = world.medicationNamed(medication)
        assertEquals(RestoreOutcome.Restored::class.java, restoredOutcome()::class.java, "the restore must succeed")
        assertTrue(
            world.intakes.of(name).any { it.isPending },
            "the restore must generate the intakes of the nearest window (SKL-06)",
        )
    }

    @Then("^все (\\d+) августовских записей остаются на месте$")
    fun allThoseAugustRecordsStay(records: Int) {
        assertEquals(records, beforeRestoring.size, "the restore must not touch the history")
        beforeRestoring.forEach { (id, before) ->
            val after = requireNotNull(world.intakes.get(id)) { "the intake $id disappeared" }
            assertEquals(before.scheduledAt, after.scheduledAt, "the history of $id must stay in place")
        }
    }

    @Then("^новые приёмы добавляются к ним, а не заменяют их$")
    fun newIntakesAreAddedToTheHistory() {
        val name = requireNotNull(focused.takeIf { it.isNotEmpty() }) { "no medication was named" }
        val after = world.intakes.of(name).map { it.id }.toSet()
        assertTrue(after.containsAll(beforeRestoring.keys), "the restore must not replace the old records")
        assertTrue(
            after.size > beforeRestoring.size,
            "the restore must add the intakes of the nearest window",
        )
    }

    @Then("^у «([^»]+)» по-прежнему нет даты окончания$")
    fun thatMedicationHasStillNoEndDate(medication: String) {
        val name = world.medicationNamed(medication)
        assertNull(world.medications.get(name).endDate, "an open-ended course must stay open (ПР-6)")
    }

    @Then("^планировщик порождает его приёмы без конечной даты$")
    fun thePlannerGeneratesItsIntakesWithoutAnEndDate() {
        val name = requireNotNull(focused.takeIf { it.isNotEmpty() }) { "no medication was named" }
        assertNull(world.medications.get(name).endDate, "the generation has no end date to stop at")
        assertTrue(world.intakes.of(name).isNotEmpty(), "the restore must generate intakes")
    }

    @Then("^у лекарства нет ни момента архивации, ни причины архивации$")
    fun theMedicationHasNeitherArchiveMomentNorReason() {
        val name = requireNotNull(focused.takeIf { it.isNotEmpty() }) { "no medication was named" }
        val medication = world.medications.get(name)
        assertNull(medication.archivedAt, "the archive moment must be cleared")
        assertNull(medication.archiveReason, "the archive reason must be cleared")
        assertFalse(medication.isArchived, "the course must be active again")
    }

    @Then("^частично очищенного состояния не возникает$")
    fun noPartiallyClearedStateAppears() {
        val name = requireNotNull(focused.takeIf { it.isNotEmpty() }) { "no medication was named" }
        val medication = world.medications.get(name)
        // ИНВ-9: момент и причина — пара; очищаются вместе или не очищаются вовсе.
        assertEquals(
            medication.archivedAt == null,
            medication.archiveReason == null,
            "the archive moment and reason must be cleared together (ИНВ-9)",
        )
    }

    // --- Вспомогательное -------------------------------------------------------------

    private var restored: RestoreOutcome? = null

    private fun restoredOutcome(): RestoreOutcome = requireNotNull(restored) {
        "the scenario never restored a medication"
    }

    private fun restoreFocused(moment: LocalDateTime) {
        val name = requireNotNull(focused.takeIf { it.isNotEmpty() }) { "no medication was named" }
        beforeRestoring = world.intakes.of(name).associateBy { it.id }
        world.clock.moveTo(moment)
        val id = world.medications.get(name).id
        restored = runBlocking {
            RestoreMedicationUseCase(
                medications = world.repositories.medicationStore,
                planner = world.planner(),
                transactor = ScenarioTransactor(world.intakes, world.medications),
                time = world.clock.time,
                log = world.log,
            )(id)
        }
        world.medications.focusOn(name)
        assertEquals(
            RestoreOutcome.Restored::class.java,
            restoredOutcome()::class.java,
            "the scenario returns a restorable course, not ${restoredOutcome()}",
        )
    }

    /** Архивация настоящим use-case после планирования: курс приходит с приёмами и будильниками. */
    private fun archiveAt(name: String, moment: LocalDateTime) {
        focused = name
        world.clock.moveTo(moment)
        val id = world.medications.get(name).id
        runBlocking { world.planner().plan(id, PlanReason.MEDICATION_ADDED) }
        val outcome = runBlocking {
            ArchiveMedicationUseCase(world.repositories.medicationStore, world.planner())(id, ArchiveReason.MANUAL)
        }
        assertTrue(outcome is ArchiveOutcome.Archived, "«$name» must land in the archive, not $outcome")
    }

    /** Курс без архивации: сценарию нужен действующий курс с названным расписанием. */
    private fun makeRunning(name: String, times: List<LocalTime>) {
        world.medications.update(name) {
            it.copy(times = times, endDate = null, archivedAt = null, archiveReason = null)
        }
    }

    private fun archiveWithReason(name: String, moment: LocalDateTime, reason: ArchiveReason) {
        world.medications.update(name) {
            it.copy(archivedAt = world.clock.momentOf(moment), archiveReason = reason)
        }
    }

    /** Курс из дней и двух времён приёма в каждый день. */
    private fun courseMoments(dates: List<LocalDate>): List<LocalDateTime> =
        dates.flatMap { date -> TWO_TIMES.map(date::atTime) }

    /**
     * Расставляет отметки курса: первые `taken` приняты, остальные — пропущены вручную.
     *
     * Пропуск идёт настоящей отметкой (`markExisting`), а не записью статуса: подпись
     * карточки считает домен, и запись в обход правила дала бы другой итог.
     */
    private fun markCourse(name: String, moments: List<LocalDateTime>, taken: Int) {
        val scenarioMoment = world.clock.nowLocal()
        moments.take(taken).forEach { moment -> world.intakes.markTaken(name, moment) }
        moments.drop(taken).forEach { moment ->
            world.markExisting(name, moment, moment.toLocalTime(), MarkResult.SKIPPED)
        }
        world.clock.moveTo(scenarioMoment)
    }

    /** Завершённый курс названной длительности с названным итогом. */
    private fun buildCourse(name: String, archivedOn: LocalDate, days: Int, taken: Int, planned: Int) {
        val start = archivedOn.minusDays((days - 1).toLong())
        world.medications.update(name) { it.copy(startDate = start, endDate = archivedOn) }
        val moments = generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(archivedOn) }
            .flatMap { date -> TWO_TIMES.map(date::atTime) }
            .toList()
        assertEquals(
            planned,
            moments.size,
            "the table declares $planned intakes over $days days, the schedule gives ${moments.size}",
        )
        markCourse(name, moments, taken)
    }

    private fun archivedOfTheActiveProfile(): List<Medication> {
        val profileId = world.profiles.get(world.profiles.active).id
        return world.medications.all().filter { it.isArchived && it.profileId == profileId }
    }

    private fun keyOf(medicationId: String): String = requireNotNull(
        world.medications.names().firstOrNull { name -> world.medications.get(name).id == medicationId },
    ) { "unknown medication id: $medicationId" }

    private fun outcomeNumbers(outcome: String): Triple<Int, Int, Int> {
        val match = requireNotNull(OUTCOME.find(outcome)) { "no outcome numbers in «$outcome»" }
        return Triple(
            match.groupValues[GROUP_DAYS].toInt(),
            match.groupValues[GROUP_TAKEN].toInt(),
            match.groupValues[GROUP_PLANNED].toInt(),
        )
    }

    private fun archiveReason(label: String): ArchiveReason =
        if (label == FINISHED_LABEL) ArchiveReason.COURSE_FINISHED else ArchiveReason.MANUAL

    private fun foodRelation(label: String): FoodRelation = when (label.lowercase()) {
        "до еды", "до" -> FoodRelation.BEFORE_MEAL
        "во время", "во время еды" -> FoodRelation.WITH_MEAL
        "после", "после еды" -> FoodRelation.AFTER_MEAL
        else -> FoodRelation.ANY
    }

    private companion object {
        const val MEDICATION_COLUMN = "лекарство"
        const val REASON_COLUMN = "причина архивации"
        const val OUTCOME_COLUMN = "итог курса"
        const val ARCHIVED_COLUMN = "дата архивации"

        const val FINISHED_LABEL = "курс завершён"
        const val ARCHIVE_SIZE = 12
        const val FINISHED_COUNT = 7
        const val COURSE_DAYS = 7
        const val ARCHIVE_HOUR = 10

        val MORNING: LocalTime = LocalTime.of(8, 0)
        val EVENING: LocalTime = LocalTime.of(20, 0)
        val TWO_TIMES: List<LocalTime> = listOf(MORNING, EVENING)

        val OUTCOME = Regex("""(\d+) дней · принято (\d+) из (\d+)""")
        const val GROUP_DAYS = 1
        const val GROUP_TAKEN = 2
        const val GROUP_PLANNED = 3
    }
}
