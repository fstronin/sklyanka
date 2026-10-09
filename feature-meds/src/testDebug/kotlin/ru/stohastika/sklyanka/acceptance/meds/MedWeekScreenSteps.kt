package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.medication.ArchiveCaption
import ru.stohastika.sklyanka.domain.medication.DayCell
import ru.stohastika.sklyanka.domain.medication.WeekCounter
import ru.stohastika.sklyanka.domain.medication.counter
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.archive.TAG_ARCHIVE_CAPTION
import ru.stohastika.sklyanka.feature.meds.card.TAG_BLOCK_TRAILING
import ru.stohastika.sklyanka.feature.meds.card.TAG_WEEK_BLOCK
import ru.stohastika.sklyanka.ui.component.TAG_ADHERENCE_LEGEND
import ru.stohastika.sklyanka.ui.component.TAG_ADHERENCE_WEEK
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Экранные шаги приёмки SKL-77 — сводка «Последние 7 дней» без вырожденной дроби
 * (`@card`, `@archive`).
 *
 * Состояние карточки собирает [MedCardStand] тем же путём, что `MedicationCardViewModel`;
 * правило «доля не рисуется» — доменное [WeekCounter]. Сценарий называет лекарство
 * ключом, а модель знает только идентификатор: имя переводит [MedsListStand.displayName]
 * (НФТ-19).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class MedWeekScreenSteps(private val world: AcceptanceWorld) {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private var focused: String = ""

    // --- Предыстория: неделя без записей --------------------------------------------

    @Допустим("^существует лекарство «([^»]+)», и за последние семь дней у него нет ни одного приёма$")
    fun thatMedicationHasNoIntakesForSevenDays(medication: String) {
        add(name = medication)
    }

    @Допустим("^лекарство «([^»]+)» заведено (.+) с приёмом в (\\d{2}:\\d{2})$")
    fun thatMedicationWasCreatedOnWithAnIntakeAt(medication: String, date: String, time: String) {
        add(
            name = medication,
            startDate = RussianDates.date(date, world.clock.today()),
            times = listOf(LocalTime.parse(time)),
        )
    }

    @Допустим("^сейчас (\\d{2}:\\d{2}), завершённых дней с приёмами у него нет$")
    fun itIsThatTimeAndThereAreNoCompletedDaysWithIntakes(time: String) {
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)))
        val week = state().week
        assertTrue(
            "the premise requires no completed day with intakes: ${week.days}",
            week.days.none { it is DayCell.Completed && it.planned > 0 },
        )
    }

    // --- Предыстория: наблюдение было ------------------------------------------------

    @Допустим("^у лекарства «([^»]+)» за последние семь дней приёмы были запланированы в завершённых днях$")
    fun thatMedicationHadPlannedIntakesInCompletedDays(medication: String) {
        add(name = medication, startDate = world.clock.today().minusDays(7))
        planWeek(missed = true, takenInCompletedDays = 0, todayPending = true)
    }

    @Допустим("^все они пропущены$")
    fun allOfThemAreMissed() {
        // Сегодняшний план ещё впереди и пропущенным не считается (ЭКР-6).
        val completed = world.intakes.of(focused).filter { it.scheduledAt.toLocalDate() < world.clock.today() }
        assertTrue("the premise requires planned intakes: $completed", completed.isNotEmpty())
        assertTrue(
            "the premise requires the planned intakes to be missed: $completed",
            completed.all { it.status == IntakeStatus.MISSED },
        )
    }

    @Допустим("^у лекарства «([^»]+)» на неделе (\\d+) приёмов, из них (\\d+) приняты в завершённых днях$")
    fun thatMedicationHasThatManyIntakesWithSoManyTaken(medication: String, planned: Int, taken: Int) {
        add(name = medication, startDate = world.clock.today().minusDays(7))
        // Шесть завершённых дней по два приёма плюс сегодняшний план: 14 в знаменателе.
        assertEquals(14, planned)
        planWeek(missed = false, takenInCompletedDays = taken, todayPending = true)
    }

    // --- Предыстория: архивный курс без приёмов --------------------------------------

    @Допустим("^завершённый курс убран в архив, но в нём нет ни одного приёма$")
    fun aFinishedCourseWasArchivedWithoutAnyTakenIntake() {
        val name = "завершённый курс"
        add(name = name, startDate = world.clock.today().minusDays(5), endDate = world.clock.today().minusDays(1))
        // Приёмы курса были пропущены: знаменатель не пуст, числитель — ноль.
        (1..3).forEach { back ->
            store(name, world.clock.today().minusDays(back.toLong()).atTime(LocalTime.of(8, 0)), IntakeStatus.MISSED)
        }
        world.medications.update(name) {
            it.copy(
                archivedAt = world.clock.momentOf(world.clock.today().atTime(10, 0)),
                archiveReason = ArchiveReason.COURSE_FINISHED,
            )
        }
        focused = name
    }

    @Допустим("^существует лекарство «([^»]+)» без приёмов за последние семь дней$")
    fun thatNamedMedicationWithoutIntakesExists(medication: String) {
        add(name = medication)
    }

    @Допустим("^существует лекарство без приёмов за последние семь дней$")
    fun thatMedicationWithoutIntakesExists() {
        add(name = "лекарство без записей")
    }

    @Допустим("^у лекарства «([^»]+)» на неделе нет ни одного приёма$")
    fun thatMedicationHasNoIntakesInTheWeek(medication: String) {
        add(name = medication)
    }

    // --- Проверки: фраза вместо дроби ------------------------------------------------

    @Тогда("^в блоке «Последние 7 дней» показана фраза «Приёмов пока нет»$")
    fun theWeekBlockShowsThePhrase() {
        val phrase = context.getString(R.string.card_week_empty)
        assertTrue(
            "the week block must read «$phrase»: ${weekTexts()}",
            phrase in weekTexts(),
        )
        theWeekStripAndLegendAreAbsent()
    }

    @Тогда("^в блоке «Последние 7 дней» показана фраза$")
    fun theWeekBlockShowsAPhrase() {
        assertTrue(
            "the week block must read «${context.getString(R.string.card_week_empty)}»: ${weekTexts()}",
            context.getString(R.string.card_week_empty) in weekTexts(),
        )
        theWeekStripAndLegendAreAbsent()
    }

    /**
     * Фраза вместо дроби — это сжатая неделя (§5.2): ни ряда столбиков, ни легенды.
     *
     * Компонент решает это сам: завершённых дней с исходом нет — сравнивать не с чем,
     * и ряд с легендой не рисуются (V-1).
     */
    private fun theWeekStripAndLegendAreAbsent() {
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onAllNodesWithTag(TAG_ADHERENCE_WEEK, useUnmergedTree = true).assertCountEquals(0)
            test.onAllNodesWithTag(TAG_ADHERENCE_LEGEND, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Тогда("^«0 из 0 приёмов» не показано$")
    fun zeroOfZeroIsNotShown() {
        val shown = weekTexts()
        assertTrue("a degenerate fraction must not be drawn: $shown", zeroFraction() !in shown)
    }

    @Тогда("^«0 %» не показано$")
    fun zeroPercentIsNotShown() {
        assertTrue("the week block must not carry a percentage: ${weekTexts()}", weekTexts().none { "%" in it })
    }

    @Но("^дробь «N из M приёмов» не показана$")
    fun theFractionIsNotShown() {
        assertTrue(
            "the week must be a phrase, not a fraction: ${state().week}",
            state().week.counter() == WeekCounter.NoRecords,
        )
    }

    @Тогда("^фразы об отсутствии записей нет$")
    fun thereIsNoPhraseAboutMissingRecords() {
        assertTrue(
            "a week with a plan must keep its numbers: ${weekTexts()}",
            context.getString(R.string.card_week_empty) !in weekTexts(),
        )
    }

    @Тогда("^в блоке «Последние 7 дней» показан счётчик вида «0 из 14 приёмов»$")
    fun theWeekBlockShowsAZeroOfFourteenCounter() {
        val week = state().week
        assertEquals(0, week.taken)
        assertEquals(14, week.planned)
        val expected = counterText(week.taken, week.planned)
        assertTrue("the week block must show «$expected»: ${weekTexts()}", expected in weekTexts())
    }

    @Тогда("^в блоке «Последние 7 дней» показано «(\\d+) из (\\d+) приёмов»$")
    fun theWeekBlockShowsThatCounter(taken: Int, planned: Int) {
        val week = state().week
        assertEquals(taken, week.taken)
        assertEquals(planned, week.planned)
        val expected = counterText(taken, planned)
        assertTrue("the week block must show «$expected»: ${weekTexts()}", expected in weekTexts())
    }

    // --- Проверки: полоса и архив ----------------------------------------------------

    @Тогда("^карточка курса не показывает «0 из 0»$")
    fun theCourseCardDoesNotShowZeroOfZero() {
        val content = requireNotNull(ArchiveStand.state(world).content) { "the archive must hold the course" }
        val caption = content.finished.single().caption
        assertTrue(
            "the archive must measure the missed intakes, not the absence: $caption",
            caption is ArchiveCaption.Outcome && caption.planned > 0,
        )
        val zeroOutcome = context.resources.getQuantityString(R.plurals.archive_outcome, 0, 0, 0, 0)
        ArchiveStand.ask(world) { test ->
            val node = test
                .onNode(hasTestTag(TAG_ARCHIVE_CAPTION), useUnmergedTree = true)
                .fetchSemanticsNode()
            val shown = MedsProbe.textsOf(node).joinToString(" ")
            assertTrue("the course card must not show «0 из 0»: $shown", zeroOutcome !in shown)
        }
    }

    // --- Доступность -----------------------------------------------------------------

    @Когда("^программа чтения с экрана зачитывает сводку «Последние 7 дней»$")
    fun theScreenReaderReadsTheWeekBlock() {
        MedCardStand.ask(world, focusedMedication()) { test ->
            test.onNodeWithTag(TAG_WEEK_BLOCK, useUnmergedTree = true).assertExists()
        }
    }

    @Тогда("^она произносит фразу об отсутствии записей$")
    fun itSpeaksThePhrase() {
        assertTrue(
            "the reader must speak the phrase: ${weekTexts()}",
            context.getString(R.string.card_week_empty) in weekTexts(),
        )
    }

    @Но("^не произносит «ноль из нуля»$")
    fun itDoesNotSpeakZeroOfZero() {
        assertTrue("the reader must not speak a zero fraction: ${weekTexts()}", zeroFraction() !in weekTexts())
    }

    // --- Обвязка ---------------------------------------------------------------------

    private fun state() = MedCardStand.state(world, focusedMedication())

    private fun focusedMedication(): String = focused.ifEmpty { world.medications.last }

    private fun weekTexts(): List<String> = MedCardStand.ask(world, focusedMedication()) { test ->
        val node = test
            .onNode(
                hasTestTag(TAG_BLOCK_TRAILING) and hasAnyAncestor(hasTestTag(TAG_WEEK_BLOCK)),
                useUnmergedTree = true,
            )
            .fetchSemanticsNode()
        MedsProbe.textsOf(node)
    }

    private fun counterText(taken: Int, planned: Int): String =
        context.resources.getQuantityString(R.plurals.card_week_counter, planned, taken, planned)

    private fun zeroFraction(): String = counterText(0, 0)

    private fun add(
        name: String,
        startDate: LocalDate = world.clock.today().minusDays(7),
        endDate: LocalDate? = null,
        times: List<LocalTime> = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
    ) {
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = name,
            times = times,
            startDate = startDate,
            endDate = endDate,
        )
        focused = name
    }

    /**
     * Шесть завершённых дней по два приёма и сегодняшний план.
     *
     * @param missed все запланированные приёмы пропущены
     * @param takenInCompletedDays сколько принято в завершённых днях (при [missed] = false)
     */
    private fun planWeek(missed: Boolean, takenInCompletedDays: Int, todayPending: Boolean) {
        val times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
        var takenLeft = takenInCompletedDays
        (1..6).forEach { back ->
            times.forEach { time ->
                val status = when {
                    missed -> IntakeStatus.MISSED
                    takenLeft > 0 -> {
                        takenLeft--
                        IntakeStatus.TAKEN
                    }

                    else -> IntakeStatus.MISSED
                }
                store(focused, world.clock.today().minusDays(back.toLong()).atTime(time), status)
            }
        }
        if (todayPending) {
            times.forEach { time -> store(focused, world.clock.today().atTime(time), IntakeStatus.PENDING) }
        }
    }

    private fun store(medication: String, moment: LocalDateTime, status: IntakeStatus) {
        val medicationId = world.medications.get(medication).id
        world.intakes.store(
            Intake(
                id = world.intakes.nextId(),
                medicationId = medicationId,
                scheduledAt = moment,
                status = status,
                actedAt = if (status == IntakeStatus.PENDING) null else world.clock.momentOf(moment),
                source = if (status == IntakeStatus.PENDING) null else IntakeSource.PHONE,
                actorProfileId = null,
            ),
        )
    }
}
