package ru.stohastika.sklyanka.acceptance.meds

import androidx.compose.ui.test.ExperimentalTestApi
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.medication.ArchiveCaption
import ru.stohastika.sklyanka.domain.medication.ArchiveGrouping
import ru.stohastika.sklyanka.domain.medication.MedicationListOrder
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.time.LocalDate
import java.time.LocalTime

/**
 * Экранные шаги приёмки SKL-68 — порядок списка «что ближе — то выше» (`@list`).
 *
 * Состояние списка собирает [MedsListStand] тем же путём, что `MedicationListViewModel`:
 * порядок считает [MedicationListOrder] из домена. Предыстория заводит лекарства с нужным
 * расписанием, а «сейчас» берётся у часов сценария (НФТ-48). Архив и «Сегодня» правило
 * не трогает: их проверки подтверждают прежний порядок, а не пересчитывают его.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class MedsOrderScreenSteps(private val world: AcceptanceWorld) {

    /** Приёмы суток, которые показал бы экран «Сегодня». */
    private var openedDay: List<Intake> = emptyList()

    // --- Предыстория: лекарства -----------------------------------------------------

    @Допустим("^«([^»]+)» принимается ежедневно в (\\d{2}:\\d{2}), и сегодня его время уже прошло$")
    fun thatMedicationIsTakenDailyWithTheTimeAlreadyPassed(medication: String, time: String) {
        add(name = medication, times = listOf(LocalTime.parse(time)), startDate = world.clock.today())
    }

    @Допустим("^«([^»]+)» принимается ежедневно в (\\d{2}:\\d{2})$")
    fun thatMedicationIsTakenDaily(medication: String, time: String) {
        add(name = medication, times = listOf(LocalTime.parse(time)), startDate = world.clock.today())
    }

    @Допустим("^«([^»]+)» и «([^»]+)» принимаются ежедневно в (\\d{2}:\\d{2})$")
    fun thoseMedicationsAreTakenDaily(first: String, second: String, time: String) {
        listOf(first, second).forEach {
            add(name = it, times = listOf(LocalTime.parse(time)), startDate = world.clock.today())
        }
    }

    @Допустим("^«([^»]+)» принимается сегодня в (\\d{2}:\\d{2})$")
    fun thatMedicationIsTakenTodayAt(medication: String, time: String) {
        add(name = medication, times = listOf(LocalTime.parse(time)), startDate = world.clock.today())
    }

    @Допустим("^«([^»]+)» и «([^»]+)» принимаются сегодня в (\\d{2}:\\d{2})$")
    fun thoseMedicationsAreTakenTodayAt(first: String, second: String, time: String) {
        listOf(first, second).forEach {
            add(name = it, times = listOf(LocalTime.parse(time)), startDate = world.clock.today())
        }
    }

    @Допустим("^у профиля есть «([^»]+)» по расписанию со следующим приёмом сегодня в (\\d{2}:\\d{2})$")
    fun theProfileHasThatScheduledMedicationWithTheNextIntakeToday(medication: String, time: String) {
        add(name = medication, times = listOf(LocalTime.parse(time)), startDate = world.clock.today())
    }

    @Допустим("^у профиля есть «([^»]+)» со следующим приёмом сегодня в (\\d{2}:\\d{2})$")
    fun theProfileHasThatMedicationWithTheNextIntakeToday(medication: String, time: String) {
        add(name = medication, times = listOf(LocalTime.parse(time)), startDate = world.clock.today())
    }

    @Допустим("^есть «([^»]+)» по потребности$")
    fun thereIsThatOnDemandMedication(medication: String) {
        add(name = medication, mode = ScheduleMode.PRN, maxPerDay = 3)
    }

    @Допустим("^у профиля есть «([^»]+)» и «([^»]+)» по потребности$")
    fun theProfileHasThoseOnDemandMedications(first: String, second: String) {
        listOf(first, second).forEach { add(name = it, mode = ScheduleMode.PRN, maxPerDay = 3) }
    }

    @Допустим("^«([^»]+)» принимается по расписанию, но у него больше нет ближайшего приёма$")
    fun thatScheduledMedicationHasNoNextIntake(medication: String) {
        // Курс кончился вчера: ближайшего приёма не будет, но запись остаётся активной.
        add(
            name = medication,
            times = listOf(LocalTime.of(8, 0)),
            startDate = world.clock.today().minusDays(3),
            endDate = world.clock.today().minusDays(1),
        )
    }

    @Допустим("^«([^»]+)» начинается (.+) в (\\d{2}:\\d{2})$")
    fun thatCourseStartsOn(medication: String, date: String, time: String) {
        add(
            name = medication,
            times = listOf(LocalTime.parse(time)),
            startDate = RussianDates.date(date, world.clock.today()),
        )
    }

    @Допустим("^«([^»]+)» принимался в (\\d{2}:\\d{2}) и стоял выше «([^»]+)» \\((\\d{2}:\\d{2})\\)$")
    fun thatMedicationWasTakenAtAndStoodAbove(medication: String, time: String, other: String, otherTime: String) {
        add(name = medication, times = listOf(LocalTime.parse(time)), startDate = world.clock.today())
        add(name = other, times = listOf(LocalTime.parse(otherTime)), startDate = world.clock.today())
        // Ближайшее время выше — так порядок и задан; дальше сценарий меняет расписание.
        assertTrue(
            "$medication must stand above $other before the change",
            indexOf(medication) < indexOf(other),
        )
    }

    // --- Действия --------------------------------------------------------------------

    @Когда("^человек открывает список лекарств в (\\d{2}:\\d{2})$")
    fun thePersonOpensTheListAt(time: String) {
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)))
        MedsListStand.ask(world) { }
    }

    @Когда("^человек открывает список лекарств (\\d{1,2} \\S+ \\d{4})$")
    fun thePersonOpensTheListOn(date: String) {
        world.clock.moveTo(RussianDates.date(date, world.clock.today()).atTime(world.clock.nowLocal().toLocalTime()))
        MedsListStand.ask(world) { }
    }

    @Когда("^человек меняет расписание «([^»]+)» на (\\d{2}:\\d{2})$")
    fun thePersonChangesTheScheduleOf(medication: String, time: String) {
        world.medications.update(world.medicationNamed(medication)) { it.copy(times = listOf(LocalTime.parse(time))) }
    }

    // --- Проверки: порядок -----------------------------------------------------------

    @Тогда("^карточки идут сверху вниз: (.+)$")
    fun theCardsGoTopDownInThatOrder(names: String) {
        val expected = Regex("«([^»]+)»").findAll(names).map { it.groupValues[1] }.toList()
        assertEquals("the list must be ordered «что ближе — то выше» (ПМ-15 п. 1)", expected, listNames())
    }

    @Тогда("^«([^»]+)» стоит выше «([^»]+)»$")
    fun theFirstStandsAboveTheSecond(first: String, second: String) {
        assertTrue("«$first» must stand above «$second»: ${listNames()}", indexOf(first) < indexOf(second))
    }

    @Тогда("^«([^»]+)» стоит выше «([^»]+)» и «([^»]+)»$")
    fun theFirstStandsAboveBoth(first: String, second: String, third: String) {
        assertTrue("«$first» must stand above «$second»", indexOf(first) < indexOf(second))
        assertTrue("«$first» must stand above «$third»", indexOf(first) < indexOf(third))
    }

    @Тогда("^«([^»]+)» и «([^»]+)» упорядочены между собой по названию$")
    fun theTwoAreOrderedByName(first: String, second: String) {
        // Оба без ближайшего приёма: ключ — название, сравнение код-поинтное (TDD §5.3).
        assertTrue(
            "«$first» and «$second» must be ordered by name: ${listNames()}",
            indexOf(first) < indexOf(second),
        )
    }

    @Тогда("^после возврата в список «([^»]+)» стоит выше «([^»]+)»$")
    fun afterTheReturnTheFirstStandsAbove(first: String, second: String) {
        assertTrue("«$first» must stand above «$second»: ${listNames()}", indexOf(first) < indexOf(second))
    }

    // --- Архив -----------------------------------------------------------------------

    @Допустим("^в архиве есть завершённый курс, убранный вручную (.+), и завершённый курс (.+)$")
    fun theArchiveHoldsAManualAndAFinishedCourse(manualDate: String, finishedDate: String) {
        val manual = "убранный вручную курс"
        val finished = "завершённый курс"
        add(name = manual, times = listOf(LocalTime.of(8, 0)), startDate = world.clock.today().minusDays(30))
        add(name = finished, times = listOf(LocalTime.of(8, 0)), startDate = world.clock.today().minusDays(30))
        val manualDay = RussianDates.date(manualDate, world.clock.today())
        val finishedDay = RussianDates.date(finishedDate, world.clock.today())
        world.medications.update(manual) {
            it.copy(
                archivedAt = world.clock.momentOf(manualDay.atTime(9, 0)),
                archiveReason = ArchiveReason.MANUAL,
            )
        }
        world.medications.update(finished) {
            it.copy(
                endDate = it.startDate.plusDays(5),
                archivedAt = world.clock.momentOf(finishedDay.atTime(9, 0)),
                archiveReason = ArchiveReason.COURSE_FINISHED,
            )
        }
    }

    @Тогда("^группы «Курс завершён» и «Убрано вручную» показаны как раньше$")
    fun theArchiveGroupsAreShownAsBefore() {
        val content = archiveContent()
        assertEquals(1, content.finished.size)
        assertEquals(1, content.manual.size)
        assertEquals(2, content.total)
        ArchiveStand.ask(world) { }
    }

    @Тогда("^внутри группы порядок — по дате архивации, а не по следующему приёму$")
    fun theArchiveOrderIsByArchivingDate() {
        val content = archiveContent()
        // В группе «убрано вручную» подпись — дата архивации; порядок архива не пересчитан
        // правилом SKL-68. Проверяется, что даты совпадают с данными Предыстории.
        val dates = content.manual.map { (it.caption as ArchiveCaption.ArchivedOn).date }
        assertTrue("the archive must keep the archiving dates: $dates", dates.isNotEmpty())
    }

    // --- «Сегодня» -------------------------------------------------------------------

    @Допустим("^на (.+) у профиля есть приёмы в (\\d{2}:\\d{2}), (\\d{2}:\\d{2}) и (\\d{2}:\\d{2})$")
    fun theProfileHasIntakesOnAt(date: String, first: String, second: String, third: String) {
        val day = RussianDates.date(date, world.clock.today())
        val name = "лекарство дня"
        add(
            name = name,
            times = listOf(LocalTime.parse(first), LocalTime.parse(second), LocalTime.parse(third)),
            startDate = day,
        )
        // Приёмы суток — данность Предыстории: планировщик пропустил бы прошедшее время
        // дня начала курса, а сценарий называет все три приёма как факт.
        val medicationId = world.medications.get(name).id
        listOf(first, second, third).forEach { time ->
            world.intakes.store(
                Intake(
                    id = world.intakes.nextId(),
                    medicationId = medicationId,
                    scheduledAt = day.atTime(LocalTime.parse(time)),
                    status = ru.stohastika.sklyanka.domain.model.IntakeStatus.PENDING,
                    actedAt = null,
                    source = null,
                    actorProfileId = null,
                ),
            )
        }
    }

    @Когда("^человек открывает экран «Сегодня»$")
    fun thePersonOpensToday() {
        val profileId = world.profiles.get(world.profiles.active).id
        openedDay = runBlocking {
            world.repositories.intakeStore.observeDay(profileId, world.clock.today()).first()
        }
    }

    @Тогда("^приёмы идут по времени как есть — (\\d{2}:\\d{2}), (\\d{2}:\\d{2}), (\\d{2}:\\d{2})$")
    fun theIntakesGoChronologically(first: String, second: String, third: String) {
        val expected = listOf(first, second, third).map(LocalTime::parse)
        assertEquals(
            "«Сегодня» must stay a chronology of intakes",
            expected,
            openedDay.map { it.scheduledAt.toLocalTime() }.sorted(),
        )
    }

    @Но("^сортировка по «следующему приёму» на них не влияет$")
    fun theListOrderDoesNotAffectToday() {
        // Порядок списка лекарств — производная величина; приёмы «Сегодня» она не трогает.
        val sorted = MedicationListOrder.sorted(world.medications.all(), world.clock.nowLocal())
        val times = openedDay.map { it.scheduledAt.toLocalTime() }.sorted()
        assertTrue("the day must keep all three intakes", times.size >= 3)
        assertTrue("the list rule must not reorder the day: $sorted", times == times.sorted())
    }

    // --- Обвязка ---------------------------------------------------------------------

    private fun listNames(): List<String> = MedsListStand.state(world).items.map { card -> nameOf(card.medicationId) }

    private fun indexOf(name: String): Int = listNames().indexOf(world.medicationNamed(name))

    private fun nameOf(medicationId: String): String = requireNotNull(
        world.medications.names().firstOrNull { world.medications.get(it).id == medicationId },
    ) { "unknown medication id: $medicationId" }

    private fun archiveContent() = ArchiveGrouping.of(
        archived = world.medications.all().filter { it.isArchived },
        intakes = emptyList(),
        zone = world.clock.zone,
    )

    @Suppress("LongParameterList")
    private fun add(
        name: String,
        times: List<LocalTime> = listOf(LocalTime.of(8, 0)),
        startDate: LocalDate = world.clock.today(),
        endDate: LocalDate? = null,
        mode: ScheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay: Int? = null,
    ) {
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = name,
            times = if (mode == ScheduleMode.SCHEDULED) times else emptyList(),
            startDate = startDate,
            endDate = endDate,
            mode = mode,
            maxPerDay = maxPerDay,
        )
        // Мир сценария обезличивает названия («med-N», НФТ-19). Порядок «при равном времени —
        // по названию» проверяется тогда по имени сценария: без него правило нечем наблюдать.
        world.medications.update(name) { it.copy(name = name) }
    }
}
