package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.datatable.DataTable
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.SweepReason
import ru.stohastika.sklyanka.domain.medication.ArchiveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.ArchiveOutcome
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.medication.SaveResult
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.domain.schedule.PlanReason
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Предыстория SKL-10: лекарство карточки со всеми своими величинами — расписанием,
 * курсом, остатком и неделей приёмов.
 *
 * Строки живут в общем артефакте, потому что они доменные: заводят лекарства, приёмы
 * и архив в мире сценария. Экранных утверждений здесь нет — они в `feature-meds`
 * и `app` (`SKL-26/TDD.md §4.3`).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class MedCardContextSteps(private val world: AcceptanceWorld) {

    /** Лекарство, о котором говорит последняя названная строка: «он», «его» следующих. */
    private var focused: String = ""

    // --- Предыстория: лекарство карточки -----------------------------------------

    /**
     * Лекарство со всеми полями карточки одной таблицей.
     *
     * Приёмы не планируются: недельные сценарии задают свою историю, а лишние записи
     * на сегодня и будущее сломали бы счётчик недели (ЭКР-6).
     */
    @Given("^у профиля есть лекарство «([^»]+)»:$")
    fun theProfileHasThatMedication(medication: String, table: DataTable) {
        // Таблица без шапки: первая колонка — поле карточки, вторая — значение.
        val facts = table.asMap(String::class.java, String::class.java)
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = medication,
        )
        focused = medication
        val strength = requireNotNull(facts[STRENGTH]) { "no «$STRENGTH» in ${facts.keys}" }
        // «9 таблеток из 60»: числитель — остаток, знаменатель (после «из») — размер упаковки.
        val stockFact = requireNotNull(facts[STOCK]) { "no «$STOCK» in ${facts.keys}" }
        val period = requireNotNull(facts[PERIOD]) { "no «$PERIOD» in ${facts.keys}" }
            .split(PERIOD_SEPARATOR).map { it.trim() }
        world.medications.update(medication) {
            it.copy(
                form = releaseForm(requireNotNull(facts[FORM])),
                shape = tabletShape(requireNotNull(facts[SHAPE])),
                strength = leadingNumber(strength),
                strengthUnit = strengthCode(strength.substringAfterLast(SPACE)),
                foodRelation = foodRelation(requireNotNull(facts[FOOD])),
                doseAmount = leadingNumber(requireNotNull(facts[DOSE])),
                times = RussianDates.times(requireNotNull(facts[SCHEDULE])),
                startDate = world.dateOf(period[0]),
                endDate = world.dateOf(period[1]),
                stockLeft = leadingNumber(stockFact),
                packageSize = packageSizeOf(stockFact),
                stockThresholdDays = leadingNumber(requireNotNull(facts[THRESHOLD])).toInt(),
            )
        }
    }

    @Given(
        "^у профиля есть лекарство «([^»]+)» формы выпуска «([^»]+)», дозировка (\\d+) (\\S+), связь с едой «([^»]+)»$",
    )
    fun theProfileHasThatMedicationWithForm(
        medication: String,
        form: String,
        strength: Int,
        strengthUnit: String,
        food: String,
    ) {
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = medication,
        )
        focused = medication
        world.medications.update(medication) {
            it.copy(
                form = releaseForm(form),
                strength = BigDecimal(strength),
                strengthUnit = strengthCode(strengthUnit),
                foodRelation = foodRelation(food),
            )
        }
    }

    @Given("^у профиля есть лекарство «([^»]+)» с началом курса (.+?) и без даты окончания$")
    fun theProfileHasThatOpenEndedMedication(medication: String, start: String) {
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = medication,
            times = listOf(DEFAULT_TIME),
            startDate = world.dateOf(start),
            endDate = null,
        )
        focused = medication
    }

    @Given("^у профиля «([^»]+)» есть лекарство «([^»]+)»$")
    fun theProfileHasThatMedicationInThatProfile(profile: String, medication: String) {
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = medication,
            times = listOf(DEFAULT_TIME),
        )
    }

    @Given("^у лекарства «([^»]+)» остаток не ведётся$")
    fun theStockOfThatMedicationIsNotTracked(medication: String) {
        world.medications.update(world.medicationNamed(medication)) {
            it.copy(stockLeft = null, stockThresholdDays = null)
        }
    }

    @Given("^расписание «([^»]+)» — «(\\d{2}:\\d{2})» и «(\\d{2}:\\d{2})»$")
    fun theScheduleOfThatMedicationIs(medication: String, first: String, second: String) {
        val name = world.medicationNamed(medication)
        focused = name
        world.medications.update(name) {
            it.copy(times = listOf(LocalTime.parse(first), LocalTime.parse(second)))
        }
    }

    @Given("^курс «([^»]+)» начался (\\d+ \\S+ \\d{4})$")
    fun theCourseOfThatMedicationStartedOn(medication: String, start: String) {
        val name = world.medicationNamed(medication)
        focused = name
        world.medications.update(name) { it.copy(startDate = world.dateOf(start)) }
    }

    /**
     * Курс заканчивается в названный день, и приёмы последнего дня остаются в расписании.
     *
     * Планирование здесь не украшение: реальная правка окончания курса перепланирует
     * приёмы, и сценарий проверяет именно следствие — последний день входит в курс целиком.
     */
    @Given("^курс «([^»]+)» заканчивается (.+)$")
    fun theCourseOfThatMedicationEndsOn(medication: String, end: String) {
        val name = world.medicationNamed(medication)
        focused = name
        world.medications.update(name) { it.copy(endDate = world.dateOf(end)) }
        world.planMedication(name, PlanReason.MEDICATION_SAVED)
    }

    // --- Предыстория: неделя приёмов ----------------------------------------------

    @Given("^приёмы «([^»]+)» с (\\d+) по (.+?) отмечены так:$")
    fun theIntakesOfThatMedicationAreMarkedLikeThis(medication: String, from: String, to: String, table: DataTable) {
        val name = world.medicationNamed(medication)
        focused = name
        val range = RussianDates.range(from, to, world.clock.today())
        table.entries().forEach { row ->
            val date = world.dateOf(requireNotNull(row[DATE_COLUMN]) { "no «дата» in $row" })
            assertTrue(date in range, "the row $date must be inside the range $range")
            val taken = requireNotNull(row[TAKEN_COLUMN]).toInt()
            val missed = requireNotNull(row[MISSED_COLUMN]).toInt()
            markDay(name, date, taken, missed)
        }
    }

    @Given("^(.+?) приём «([^»]+)» на (\\d{2}:\\d{2}) принят, а приём на (\\d{2}:\\d{2}) ещё впереди$")
    fun oneIntakeIsTakenAndTheNextIsPending(date: String, medication: String, takenTime: String, pendingTime: String) {
        val name = world.medicationNamed(medication)
        focused = name
        val day = world.dateOf(date)
        world.intakes.markTaken(name, day.atTime(LocalTime.parse(takenTime)))
        world.intakes.pending(name, day.atTime(LocalTime.parse(pendingTime)))
    }

    @Given("^(.+?) приём на (\\d{2}:\\d{2}) принят, а приём на (\\d{2}:\\d{2}) ещё впереди$")
    fun theFocusedIntakeIsTakenAndTheNextIsPending(date: String, takenTime: String, pendingTime: String) {
        val name = requireNotNull(
            focused.takeIf { it.isNotEmpty() } ?: world.medications.last.takeIf { it.isNotEmpty() },
        ) {
            "no medication was named"
        }
        focused = name
        val day = world.dateOf(date)
        world.intakes.markTaken(name, day.atTime(LocalTime.parse(takenTime)))
        world.intakes.pending(name, day.atTime(LocalTime.parse(pendingTime)))
    }

    @Given("^(.+?) приёмы «([^»]+)» на (\\d{2}:\\d{2}) и (\\d{2}:\\d{2}) приняты$")
    fun bothIntakesAreTaken(date: String, medication: String, first: String, second: String) {
        val name = world.medicationNamed(medication)
        focused = name
        val day = world.dateOf(date)
        listOf(first, second).forEach { time -> world.intakes.markTaken(name, day.atTime(LocalTime.parse(time))) }
    }

    @Given("^(.+?) приёмы «([^»]+)» приняты, а (.+?) остались без отметки$")
    fun theIntakesOfOneDayAreTakenAndOfAnotherWithoutMarks(takenDate: String, medication: String, missedDate: String) {
        val name = world.medicationNamed(medication)
        focused = name
        markDay(name, world.dateOf(takenDate), taken = ALL_TIMES, missed = 0)
        markDay(name, world.dateOf(missedDate), taken = 0, missed = ALL_TIMES)
    }

    @Given("^у «([^»]+)» есть отмеченные приёмы с (\\d+) по (.+)$")
    fun thatMedicationHasTakenIntakesFromTo(medication: String, from: String, to: String) {
        val name = world.medicationNamed(medication)
        focused = name
        val dates = RussianDates.range(from, to, world.clock.today())
        dates.forEach { date -> markDay(name, date, taken = ALL_TIMES, missed = 0) }
    }

    @Then("^приёмы на (.+?) в расписании остаются$")
    fun theIntakesOfThatDayStayInTheSchedule(date: String) {
        val name = requireNotNull(focused.takeIf { it.isNotEmpty() }) { "no medication was named" }
        val day = world.dateOf(date)
        val moments = world.medications.get(name).times.sorted().map { day.atTime(it) }
        assertTrue(moments.isNotEmpty(), "«$name» must have a schedule")
        moments.forEach { moment ->
            assertTrue(
                world.intakes.of(name).any { it.scheduledAt == moment },
                "the intake of «$name» at $moment must stay in the schedule",
            )
        }
    }

    // --- Архивация ----------------------------------------------------------------

    @Given("^лекарство «([^»]+)» отправлено в архив (.+?)(?: в (\\d{2}:\\d{2}))?$")
    fun thatMedicationWasArchivedOn(medication: String, date: String, time: String?) {
        archiveWithReminders(world.medicationNamed(medication), world.dateOf(date).atTime(clockTime(time)))
    }

    @When("^лекарство отправлено в архив (.+)$")
    fun theFocusedMedicationWasArchived(date: String) {
        archiveWithReminders(world.medications.last, world.dateOf(date).atTime(DEFAULT_TIME))
    }

    @Then("^лекарство помещается в архив с причиной «убрано вручную»$")
    fun theMedicationIsArchivedManually() {
        val name = requireNotNull(focused.takeIf { it.isNotEmpty() }) { "no medication was named" }
        val medication = world.medications.get(name)
        assertTrue(medication.isArchived, "«$name» must be archived")
        assertEquals(ArchiveReason.MANUAL, medication.archiveReason, "archived by hand (ИНВ-9)")
    }

    @Then("^новых приёмов «([^»]+)» не создаётся$")
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

    @Then("^отмеченные приёмы с (\\d+) по (.+?) остаются$")
    fun theTakenIntakesFromToStay(from: String, to: String) {
        val name = requireNotNull(focused.takeIf { it.isNotEmpty() }) { "no medication was named" }
        val dates = RussianDates.range(from, to, world.clock.today())
        dates.forEach { date ->
            assertTrue(
                world.intakes.of(name).any { it.scheduledAt.toLocalDate() == date && it.status == IntakeStatus.TAKEN },
                "the taken intake of «$name» on $date must stay (ПР-11)",
            )
        }
    }

    // --- Правка расписания и отмеченные приёмы (ОВ: направление зафиксировано сценарием) ---

    @Given("^(.+?) приём «([^»]+)» на (\\d{2}:\\d{2}) отмечен принятым$")
    fun thatIntakeOfThatDayIsTaken(date: String, medication: String, time: String) {
        val name = world.medicationNamed(medication)
        focused = name
        world.intakes.markTaken(name, world.dateOf(date).atTime(LocalTime.parse(time)))
    }

    @Given("^приём на (\\d{2}:\\d{2}) ожидается$")
    fun thatIntakeIsPending(time: String) {
        val name = requireNotNull(focused.takeIf { it.isNotEmpty() }) { "no medication was named" }
        world.intakes.pending(name, world.clock.today().atTime(LocalTime.parse(time)))
    }

    @When("^в (\\d{2}:\\d{2}) человек меняет расписание на «(\\d{2}:\\d{2})» и «(\\d{2}:\\d{2})» и сохраняет$")
    fun thePersonChangesTheScheduleAt(time: String, first: String, second: String) {
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)))
        val name = requireNotNull(focused.takeIf { it.isNotEmpty() }) { "no medication was named" }
        val edited = MedicationDraft.of(world.medications.get(name)).copy(
            times = listOf(LocalTime.parse(first), LocalTime.parse(second)),
        )
        val result = world.save(edited)
        assertTrue(result is SaveResult.Saved, "the edit must be saved: $result")
    }

    @Then("^отмеченный приём на (\\d{2}:\\d{2}) остаётся принятым$")
    fun theTakenIntakeStays(time: String) {
        val name = requireNotNull(focused.takeIf { it.isNotEmpty() }) { "no medication was named" }
        val intake = world.intakes.of(name)
            .firstOrNull { it.scheduledAt == world.clock.today().atTime(LocalTime.parse(time)) }
        assertNotNull(intake, "the marked intake at $time must stay")
        assertEquals(IntakeStatus.TAKEN, intake!!.status, "a mark is not rewritten by an edit (ПР-11)")
    }

    @Then("^на (.+?) появляется ожидающийся приём на (\\d{2}:\\d{2})$")
    fun thatPendingIntakeAppears(date: String, time: String) {
        val name = requireNotNull(focused.takeIf { it.isNotEmpty() }) { "no medication was named" }
        val moment = world.dateOf(date).atTime(LocalTime.parse(time))
        assertTrue(
            world.intakes.of(name).any { it.scheduledAt == moment && it.isPending },
            "a pending intake of «$name» at $moment must appear",
        )
    }

    @Then("^ожидающегося приёма на (\\d{2}:\\d{2}) больше нет$")
    fun thatPendingIntakeIsGone(time: String) {
        val name = requireNotNull(focused.takeIf { it.isNotEmpty() }) { "no medication was named" }
        val moment = world.clock.today().atTime(LocalTime.parse(time))
        assertTrue(
            world.intakes.of(name).none { it.scheduledAt == moment && it.isPending },
            "the pending intake of «$name» at $moment must be gone",
        )
    }

    // --- Вспомогательное ----------------------------------------------------------

    /**
     * Отметки одного дня: `taken` первых времён расписания приняты, следующие `missed`
     * остаются без отметки.
     *
     * Пропуск создаёт настоящий проход по просроченным: часы сдвигаются за границу суток
     * и возвращаются, как в шагах SKL-07. Запись статуса в обход правила сценарий бы
     * проверял не то, что увидит человек.
     */
    private fun markDay(name: String, date: LocalDate, taken: Int, missed: Int) {
        val times = world.medications.get(name).times.sorted()
        require(taken + missed <= times.size) { "«$name» has ${times.size} times, not ${taken + missed}" }
        times.take(taken).forEach { world.intakes.markTaken(name, date.atTime(it)) }
        if (missed > 0) {
            times.drop(taken).take(missed).forEach { world.intakes.pending(name, date.atTime(it)) }
            val scenarioMoment = world.clock.nowLocal()
            world.clock.moveTo(date.plusDays(1).atStartOfDay())
            world.sweep(SweepReason.TIMER)
            world.clock.moveTo(scenarioMoment)
        }
    }

    /**
     * Архивация настоящим `ArchiveMedicationUseCase` — с планировщиком, который снимает
     * будущие ожидающиеся приёмы и пересобирает будильники (ПР-11, НАП-11).
     *
     * Перед архивацией курс планируется: у него обязаны быть и приёмы, и будильники —
     * иначе проверка «напоминания не приходит» была бы про то, чего не было.
     */
    private fun archiveWithReminders(name: String, moment: LocalDateTime) {
        world.clock.moveTo(moment)
        focused = name
        val id = world.medications.get(name).id
        runBlocking { world.planner().plan(id, PlanReason.MEDICATION_ADDED) }
        assertTrue(
            world.reminders.scheduled().isNotEmpty(),
            "the course of «$name» must have alarms before archiving",
        )
        val outcome = runBlocking {
            ArchiveMedicationUseCase(world.repositories.medicationStore, world.planner())(id, ArchiveReason.MANUAL)
        }
        assertTrue(outcome is ArchiveOutcome.Archived, "«$name» must land in the archive, not $outcome")
    }

    private fun leadingNumber(text: String): BigDecimal =
        BigDecimal(requireNotNull(LEADING_NUMBER.find(text.trim())) { "no number in «$text»" }.value)

    /** Размер упаковки из строки «9 таблеток из 60»; `null`, если «из» нет — размер не задан. */
    private fun packageSizeOf(text: String): BigDecimal? =
        PACKAGE_SIZE.find(text)?.groupValues?.get(1)?.let(::BigDecimal)

    private fun clockTime(time: String?): LocalTime = time?.let(LocalTime::parse) ?: DEFAULT_TIME

    private fun releaseForm(label: String): MedicationForm = when (label) {
        "Таблетка" -> MedicationForm.TABLET
        "Капсула" -> MedicationForm.CAPSULE
        "Спрей" -> MedicationForm.SPRAY
        "Сироп" -> MedicationForm.SYRUP
        "Капли" -> MedicationForm.DROPS
        else -> MedicationForm.INJECTION
    }

    private fun tabletShape(label: String): TabletShape = when (label) {
        "Круглая" -> TabletShape.ROUND
        "Круглая с риской" -> TabletShape.ROUND_SCORED
        "Овальная" -> TabletShape.OVAL
        "Продолговатая" -> TabletShape.OBLONG
        else -> TabletShape.TRIANGULAR
    }

    /** Связь с едой в карточке записана строчными: «до еды», «во время еды», «после еды». */
    private fun foodRelation(label: String): FoodRelation = when (label.lowercase()) {
        "до еды", "до" -> FoodRelation.BEFORE_MEAL
        "во время", "во время еды" -> FoodRelation.WITH_MEAL
        "после", "после еды" -> FoodRelation.AFTER_MEAL
        else -> FoodRelation.ANY
    }

    private fun strengthCode(unit: String): String = if (unit == MILLIGRAM) MILLIGRAM_CODE else unit

    private companion object {
        const val FORM = "форма выпуска"
        const val SHAPE = "вид таблетки"
        const val STRENGTH = "дозировка"
        const val FOOD = "связь с едой"
        const val DOSE = "доза на приём"
        const val SCHEDULE = "расписание"
        const val PERIOD = "период курса"
        const val STOCK = "остаток"
        const val THRESHOLD = "порог покупки"

        const val DATE_COLUMN = "дата"
        const val TAKEN_COLUMN = "принято"
        const val MISSED_COLUMN = "пропущено"

        const val PERIOD_SEPARATOR = " — "
        const val SPACE = " "
        const val MILLIGRAM = "мг"
        const val MILLIGRAM_CODE = "mg"
        const val ALL_TIMES = 2

        val DEFAULT_TIME: LocalTime = LocalTime.of(8, 0)
        val LEADING_NUMBER = Regex("""^\d+(?:[.,]\d+)?""")

        /** «… из 60»: знаменатель карточки в таблице Предыстории. */
        val PACKAGE_SIZE = Regex("""из\s+(\d+(?:[.,]\d+)?)""")
    }
}
