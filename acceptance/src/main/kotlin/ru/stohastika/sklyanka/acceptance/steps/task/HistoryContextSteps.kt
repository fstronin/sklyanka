package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.RussianNames
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.history.HistoryWindow
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.medication.ArchiveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.ArchiveOutcome
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.schedule.PlanReason
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/**
 * Шаги SKL-18: история приёмов и отчёт врачу как предметная область (заведены SKL-26).
 *
 * Здесь живёт всё, что не требует собранного экрана: Предыстория сценариев (расписания,
 * отметки, архивация курса), сборка отчёта и след отчёта в журнале. Экранные проверки —
 * в `feature-history`, переход из настроек — в `app`.
 *
 * Правила не копируются. Отметки идут настоящим `MarkIntakeUseCase` (`world.markExisting`,
 * `world.mark`), архивация — настоящим `ArchiveMedicationUseCase`, отчёт — настоящим
 * `BuildDoctorReportUseCase` (`world.history`), автоматический пропуск — настоящим
 * проходом по просроченным (`world.sweep`).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions")
class HistoryContextSteps(private val world: AcceptanceWorld) {

    // --- Предыстория: лекарства и открытие истории ------------------------------

    /** Открытие истории из настроек сам по себе шаг ничего не настраивает: это маршрут `app`. */
    @Given("^человек открыл историю из настроек$")
    fun thePersonOpenedHistoryFromSettings() = Unit

    /**
     * Лекарство Предыстории без имени профиля: «у профиля есть лекарство …».
     *
     * Строка называет активный профиль сценария — так она и читается: лекарство
     * у того профиля, который сейчас открыт.
     */
    @Given(
        "^у профиля есть лекарство «([^»]+)» с (?:приёмом в|приёмами в) " +
            "(\\d{2}:\\d{2}(?: и \\d{2}:\\d{2})*) ежедневно$",
    )
    fun theActiveProfileHasThatMedication(medication: String, times: String) {
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = medication,
            times = RussianDates.times(times),
        )
    }

    // --- Предыстория: сводка месяца ---------------------------------------------

    @Given("^в сентябре 2026 человек отметил пропущенными (\\d+) приём\\S*$")
    fun thePersonMarkedThatManyAsSkipped(count: Int) {
        septemberSlots().take(count).forEach { (name, moment) -> markSkipped(name, moment) }
    }

    /**
     * Автоматические пропуски — настоящим проходом по просроченным, а не записью статуса:
     * «признаны пропущенными» означает ровно то, что делает приложение (ПР-1).
     */
    @Given("^ещё (\\d+) приём\\S* сентября остались неотмеченными и были признаны пропущенными$")
    fun theRestWereMissedWithoutMark(count: Int) {
        val mentioned = world.intakes.ofProfile(world.profiles, world.profiles.active)
            .map { it.medicationId to it.scheduledAt }
            .toSet()
        septemberSlots()
            .filterNot { (name, moment) -> world.medications.get(name).id to moment in mentioned }
            .take(count)
            .forEach { (name, moment) -> world.intakes.pending(name, moment) }
        world.sweep()
        assertEquals(
            count,
            world.intakes.ofProfile(world.profiles, world.profiles.active).count { it.status == IntakeStatus.MISSED },
            "the sweep must have turned exactly $count pending intakes into automatic misses",
        )
    }

    @Given("^в сентябре 2026 есть (\\d+) дней, в которых приняты все запланированные приёмы$")
    fun thatManyDaysAreFullyTaken(count: Int) {
        val medications = scheduledMedications()
        assertTrue(medications.isNotEmpty(), "the active profile has no scheduled medication to accept")
        repeat(count) { index ->
            val date = SEPTEMBER.atDay(index + 1)
            medications.forEach { medication ->
                medication.times.sorted().forEach { time ->
                    world.intakes.markTaken(nameOf(medication.id), date.atTime(time))
                }
            }
        }
    }

    @Given("^в остальных днях сентября есть хотя бы один непринятый приём$")
    fun theRemainingDaysHaveAtLeastOneUnacceptedIntake() {
        val accepted = world.intakes.ofProfile(world.profiles, world.profiles.active)
            .filter { it.status == IntakeStatus.TAKEN }
            .map { it.scheduledAt.toLocalDate() }
            .toSet()
        val remaining = (1..SEPTEMBER.lengthOfMonth())
            .map(SEPTEMBER::atDay)
            .filterNot { it in accepted }
        assertTrue(remaining.isNotEmpty(), "every day of September is fully accepted: nothing is left to miss")
        val medication = scheduledMedications().first()
        remaining.forEach { date ->
            markSkipped(nameOf(medication.id), date.atTime(medication.times.min()))
        }
    }

    // --- Предыстория: календарь --------------------------------------------------

    /**
     * День календаря как данность Предыстории: все приёмы дня приняты, часть, все
     * пропущены или приёмов не было.
     *
     * «Приёмов не было» не делает день днём без приёмов само по себе: если истории
     * у профиля нет вовсе, все дни нейтральны как «до начала ведения». Поэтому
     * недостающая запись истории заводится здесь же — иначе состояние дня было бы
     * не тем, о котором говорит пример.
     */
    @Given("^15 сентября 2026 (.+)$")
    fun theFifteenthOfSeptemberIs(outcome: String) {
        val date = SEPTEMBER.atDay(DAY_STATE_DAY)
        val slots = daySlots(date)
        when (outcome) {
            ALL_TAKEN_EXAMPLE -> slots.forEach { (name, moment) -> world.intakes.markTaken(name, moment) }

            PARTIAL_EXAMPLE -> {
                assertTrue(slots.size >= 2, "the day must have at least two intakes for a partial day")
                world.intakes.markTaken(slots.first().first, slots.first().second)
                slots.drop(1).forEach { (name, moment) -> markSkipped(name, moment) }
            }

            MISSED_EXAMPLE -> slots.forEach { (name, moment) -> markSkipped(name, moment) }

            NO_INTAKES_EXAMPLE -> {
                assertTrue(
                    world.intakes.ofProfile(world.profiles, world.profiles.active)
                        .none { it.scheduledAt.toLocalDate() == date },
                    "the scenario declares a day without intakes, and the day has records",
                )
                val medication = scheduledMedications().first()
                world.intakes.markTaken(nameOf(medication.id), SEPTEMBER.atDay(1).atTime(medication.times.min()))
            }

            else -> error("unknown day outcome: $outcome")
        }
    }

    // --- Предыстория: переключение месяца ---------------------------------------

    @Given("^в (.+?) 2026 человек пропустил (\\d+) приём\\S*$")
    fun thePersonMissedThatManyIn(monthName: String, count: Int) {
        val month = RussianDates.month("$monthName 2026", world.clock.today())
        monthSlots(month).take(count).forEach { (name, moment) -> markSkipped(name, moment) }
    }

    // --- Предыстория: дни до начала ведения истории ------------------------------

    @Given("^первая запись истории профиля «([^»]+)» — (.+)$")
    fun theFirstHistoryRecordOfThatProfileIsOn(profile: String, dateText: String) {
        val date = world.dateOf(dateText)
        val medications = scheduledMedications(profile)
        assertTrue(medications.isNotEmpty(), "«$profile» has no scheduled medication")
        val medication = medications.first()
        world.intakes.markTaken(nameOf(medication.id), date.atTime(medication.times.min()))
        assertTrue(
            world.intakes.ofProfile(world.profiles, profile).none { it.scheduledAt.toLocalDate().isBefore(date) },
            "the scenario declares the first record of «$profile», earlier records must not exist",
        )
    }

    // --- Предыстория: разбивка по лекарствам -------------------------------------

    /**
     * Доля принятого по лекарству: все запланированные приёмы месяца заводятся,
     * принятыми отмечаются первые `taken` — ровно так долю и посчитает домен.
     */
    @Given("^(?:в сентябре 2026 )?у «([^»]+)» принято (\\d+) приём\\S* из (\\d+)$")
    fun thatMedicationTookThatManyOf(medication: String, taken: Int, planned: Int) {
        val name = world.medicationNamed(medication)
        val moments = septemberSlots().filter { it.first == name }.map { it.second }
        assertEquals(planned, moments.size, "the schedule of «$medication» gives ${moments.size} intakes, not $planned")
        world.intakes.removeOf(world.medications.get(name).id)
        moments.forEach { moment -> world.intakes.pending(name, moment) }
        moments.take(taken).forEach { moment -> world.intakes.markTaken(name, moment) }
    }

    @Given("^в сентябре 2026 у «([^»]+)» доля принятого заметно ниже, чем у остальных лекарств$")
    fun thatMedicationTookVisiblyLessThanTheOthers(medication: String) {
        val name = world.medicationNamed(medication)
        val named = world.medications.get(name)
        val others = scheduledMedications().filterNot { it.id == named.id }
        assertTrue(others.isNotEmpty(), "the profile must have another medication to compare with")
        others.forEach { other ->
            putShare(nameOf(other.id), septemberSlots().filter { it.first == nameOf(other.id) }) {
                it -
                    1
            }
        }
        putShare(name, septemberSlots().filter { it.first == name }) { it / LOW_SHARE_DIVISOR }
    }

    /**
     * Курс с приёмами: лекарство заводится, если Предыстория его не называла, и
     * разворачивается в принятые приёмы — «принималось с … по …» значит именно это.
     */
    @Given("^лекарство «([^»]+)» принималось с (.+?) по (.+)$")
    fun thatMedicationWasTakenFromTo(medication: String, from: String, to: String) {
        val name = ensureMedication(medication)
        val dates = RussianDates.range(from, to, world.clock.today())
        val medicationTimes = world.medications.get(name).times.ifEmpty { listOf(MORNING) }
        world.medications.update(name) {
            it.copy(startDate = dates.first(), endDate = dates.last(), archivedAt = null, archiveReason = null)
        }
        world.intakes.removeOf(world.medications.get(name).id)
        dates.forEach { date ->
            medicationTimes.forEach { time -> world.intakes.markTaken(name, date.atTime(time)) }
        }
    }

    /**
     * Архивированный курс без слова «принималось»: сценарий называет только архивацию.
     *
     * Завершённый курс к этому моменту успел пройти, поэтому фикстура разворачивает его
     * в принятые приёмы: иначе «курс не приписывается месяцам после архивации» проверялся
     * бы на пустоте, а «сентябрь остаётся прежним» сравнивал бы ничего с ничем.
     */
    @Given("^курс «([^»]+)» архивирован (.+)$")
    fun thatCourseIsArchivedOn(medication: String, dateText: String) {
        val name = ensureMedication(medication)
        val archivedOn = world.dateOf(dateText)
        thatMedicationWasTakenFromTo(
            medication = name,
            from = archivedOn.minusDays((COURSE_DAYS - 1).toLong()).dayOfMonth.toString(),
            to = dateText,
        )
        thatCourseWasArchivedAsFinished(dateText = dateText, medication = name)
    }

    @Given("^(.+) курс «([^»]+)» отправлен в архив как завершённый$")
    fun thatCourseWasArchivedAsFinished(dateText: String, medication: String) {
        val name = world.medicationNamed(medication)
        val moment = world.localMoment(dateText, ARCHIVE_TIME)
        val scenarioMoment = world.clock.nowLocal()
        val id = world.medications.get(name).id
        world.clock.moveTo(moment)
        // Тот же путь, что у продукта: планировщик доводит курс до архивации, архивация
        // идёт настоящим сценарием домена, момент берётся у часов сценария (SKL-11).
        runBlocking { world.planner().plan(id, PlanReason.MEDICATION_ADDED) }
        val outcome = runBlocking {
            ArchiveMedicationUseCase(
                world.repositories.medicationStore,
                world.planner(),
            )(id, ArchiveReason.COURSE_FINISHED)
        }
        assertTrue(outcome is ArchiveOutcome.Archived, "«$name» must land in the archive, not $outcome")
        world.medications.focusOn(name)
        world.clock.moveTo(scenarioMoment)
    }

    // --- Предыстория: строка-вывод ----------------------------------------------

    @Given("^в сентябре 2026 из (\\d+) пропусков (\\d+) приходятся на приём в (\\d{2}:\\d{2})$")
    fun thatManyOfTheMissesAreAt(total: Int, count: Int, time: String) {
        val at = LocalTime.parse(time)
        val slots = septemberSlots()
        slots.filter { it.second.toLocalTime() == at }.take(count)
            .forEach { (name, moment) -> markSkipped(name, moment) }
        slots.filter { it.second.toLocalTime() != at }.take(total - count)
            .forEach { (name, moment) -> markSkipped(name, moment) }
    }

    @Given("^в сентябре 2026 пропуски распределены между приёмами в (\\d{2}:\\d{2}) и (\\d{2}:\\d{2}) поровну$")
    fun theMissesAreEquallySpreadBetween(first: String, second: String) {
        listOf(LocalTime.parse(first), LocalTime.parse(second)).forEach { at ->
            septemberSlots().filter { it.second.toLocalTime() == at }.take(EQUAL_MISSES)
                .forEach { (name, moment) -> markSkipped(name, moment) }
        }
    }

    // --- Предыстория: пустой месяц -----------------------------------------------

    @Given("^у профиля «([^»]+)» нет ни одного приёма в (.+)$")
    fun thatProfileHasNoIntakesIn(profile: String, monthName: String) {
        val window = HistoryWindow.of(RussianDates.month(monthName, world.clock.today()))
        assertTrue(
            world.intakes.ofProfile(world.profiles, profile)
                .none { it.scheduledAt.toLocalDate() in window.from..window.to },
            "the scenario declares an empty month, and «$profile» has records in it",
        )
    }

    // --- Предыстория: профили -----------------------------------------------------

    /**
     * Отметка владельца телефона в чужом профиле.
     *
     * Отметку ставит настоящий сценарий домена, а исполнителя определяет `ActorResolver`
     * по активному профилю: чтобы подпись появилась, владелец отмечает приём чужого
     * профиля — и активным на момент отметки обязан быть он (ПРФ-14).
     */
    @Given("^приём «([^»]+)» профиля «([^»]+)» на (.+?), (\\d{2}:\\d{2}) отмечен принятым владельцем телефона$")
    fun thatIntakeOfThatProfileIsMarkedByTheOwner(medication: String, profile: String, date: String, time: String) {
        val owner = requireNotNull(world.profiles.self()) { "the scenario has no phone owner" }
        val name = ensureMedication(medication, profile)
        val ownerId = world.profiles.get(profile).id
        if (world.medications.get(name).profileId != ownerId) {
            world.medications.update(name) { it.copy(profileId = ownerId) }
        }
        val moment = world.localMoment(date, time)
        world.intakes.pending(name, moment)
        val scenarioMoment = world.clock.nowLocal()
        world.profiles.use(owner.name)
        world.markExisting(name, moment, LocalTime.parse(time), MarkResult.TAKEN)
        world.profiles.use(profile)
        world.clock.moveTo(scenarioMoment)
    }

    @Given("^все отметки профиля «([^»]+)» сделаны самим человеком$")
    fun allMarksOfThatProfileAreMadeByThePerson(profile: String) {
        val medications = scheduledMedications(profile)
        assertTrue(medications.isNotEmpty(), "«$profile» has no scheduled medication")
        val medication = medications.first()
        world.intakes.markTaken(nameOf(medication.id), SEPTEMBER.atDay(OWN_HISTORY_DAY).atTime(medication.times.min()))
        val profileId = world.profiles.get(profile).id
        world.intakes.ofProfile(world.profiles, profile).filterNot { it.isPending }.forEach { intake ->
            assertTrue(
                intake.actorProfileId == null || intake.actorProfileId == profileId,
                "a mark of «$profile» is signed by someone else: ${intake.actorProfileId}",
            )
        }
    }

    @Given("^у профиля «([^»]+)» в сентябре 2026 есть (\\d+) принятых приём\\S*$")
    fun thatProfileHasThatManyTakenIntakes(profile: String, count: Int) {
        val medications = scheduledMedications(profile).ifEmpty {
            world.medications.add(
                profile = world.profiles,
                profileName = profile,
                name = "$profile, лекарство",
                times = listOf(MORNING, EVENING),
            )
            scheduledMedications(profile)
        }
        val slots = monthSlots(SEPTEMBER, profile)
        assertTrue(slots.size >= count, "«$profile» has only ${slots.size} intakes in September")
        slots.take(count).forEach { (name, moment) -> world.intakes.markTaken(name, moment) }
        assertEquals(
            count,
            world.intakes.ofProfile(world.profiles, profile).count { it.status == IntakeStatus.TAKEN },
            "exactly $count intakes of «$profile» must be taken",
        )
    }

    // --- Отчёт врачу: Предыстория и действие ---------------------------------------

    /**
     * Активные лекарства и завершённый архивный курс в одном месяце.
     *
     * Сценарий перечисляет состав, а не числа; чтобы в отчёте было что перечислять,
     * фикстура доводит активные лекарства до одного принятого и одного пропущенного
     * приёма (пропуск даёт строку дат пропусков) и проворачивает курс через архив.
     */
    @Given("^в сентябре 2026 у профиля «([^»]+)» есть активные лекарства и архивированный курс «([^»]+)»$")
    fun thatProfileHasActiveMedicationsAndAnArchivedCourse(profile: String, medication: String) {
        val active = scheduledMedications(profile).first()
        world.intakes.markTaken(nameOf(active.id), SEPTEMBER.atDay(TAKEN_REFERENCE_DAY).atTime(active.times.min()))
        markSkipped(nameOf(active.id), SEPTEMBER.atDay(MISSED_REFERENCE_DAY).atTime(active.times.min()))
        val name = ensureMedication(medication, profile)
        thatMedicationWasTakenFromTo(medication = name, from = "1", to = "10 сентября 2026")
        thatCourseWasArchivedAsFinished(dateText = "10 сентября 2026", medication = name)
    }

    @Given("^на устройстве выключена передача данных и Wi-Fi$")
    fun theDeviceIsOffline() = Unit

    /**
     * Отчёт — настоящим сценарием домена (`world.history.buildReport`).
     *
     * Шаг один на три сценария: «отчёт формируется», «отчёт без сети» и «след в журнале»
     * говорят об одном действии, и второй его реализации в приёмке быть не должно.
     */
    @When("^человек формирует отчёт врачу за (.+)$")
    fun thePersonBuildsTheDoctorReportFor(monthName: String) {
        val month = RussianDates.month(monthName, world.clock.today())
        assertNotNull(world.history.buildReport(month), "the report of $month must be built")
    }

    // --- Отчёт врачу: проверки -----------------------------------------------------

    @Then("^отчёт формируется полностью$")
    fun theReportIsBuiltCompletely() {
        val report = requireNotNull(world.history.report) { "the scenario never built a report" }
        assertEquals(SEPTEMBER.atDay(1), report.period.from, "the report must cover its month completely")
        assertEquals(SEPTEMBER.atDay(LAST_DAY), report.period.to, "the report must cover its month completely")
        assertEquals(SEPTEMBER.lengthOfMonth(), report.period.days.size, "the report must hold every day of the month")
        assertTrue(report.profileName.isNotEmpty(), "the report must name its profile")
        assertNotNull(report.generatedAt, "the report must carry the moment it was built")
    }

    /**
     * Сцены истории и отчёта сети не требуют, и это утверждение о сборке, а не о настроении.
     *
     * Сеть в продукте есть, но только аналитическим контуром (ПЛ-3, НФТ-15, ПМ-19,
     * SKL-82): `INTERNET` объявлен единственным сетевым разрешением, до согласия SDK
     * не активируется, а отчёт и журнал не уходят наружу ни при каких условиях.
     * Разрешения состояния сети запрещены, конвейер требует `INTERNET` вместе с контуром;
     * разрешение, приехавшее из чужого манифеста, снимается явно (`tools:node="remove"`) —
     * так обработано `ACCESS_NETWORK_STATE` из `androidx.work`.
     */
    @Then("^приложение не обращается в сеть ни на одном шаге$")
    fun theApplicationNeverReachesTheNetwork() {
        val manifest = File(RepositoryLayout.root, MANIFEST).readText()
        val declarations = Regex(USES_PERMISSION).findAll(manifest)
            .map { it.value }
            .filterNot { it.contains(REMOVE_DIRECTIVE) }
            .toList()
        FORBIDDEN_NETWORK_PERMISSIONS.forEach { permission ->
            assertFalse(
                declarations.any { it.contains(permission) },
                "the application manifest must not declare $permission (НФТ-15)",
            )
        }
        assertTrue(
            declarations.any { it.contains(ANALYTICS_PERMISSION) },
            "the only network permission must be $ANALYTICS_PERMISSION, and it belongs to the analytics contour",
        )
        val pipelineCheck = File(RepositoryLayout.root, CONTOUR_RULES).readText()
        FORBIDDEN_NETWORK_PERMISSIONS.forEach { permission ->
            assertTrue(
                pipelineCheck.contains(permission),
                "the build must still fail on $permission, otherwise the absence is not guarded",
            )
        }
        assertTrue(
            pipelineCheck.contains(ANALYTICS_PERMISSION),
            "the build must require $ANALYTICS_PERMISSION together with the analytics contour (SKL-82)",
        )
    }

    @Then("^в локальном журнале приложения есть запись о формировании отчёта с периодом и числом записей$")
    fun theJournalHasTheReportRecord() {
        val record = requireNotNull(world.log.records.lastOrNull { it.event == OpEvent.DOCTOR_REPORT_BUILT }) {
            "the journal must keep the report build: ${world.log.events}"
        }
        assertEquals(
            "${SEPTEMBER.atDay(1)}..${SEPTEMBER.atDay(LAST_DAY)}",
            record.after,
            "the journal record must carry the period",
        )
        assertTrue(
            record.detail.orEmpty().matches(REPORT_DETAIL),
            "the journal record must carry the number of records: ${record.detail}",
        )
    }

    /**
     * Медицинских данных в журнале нет — и агрегированные числа приверженности тоже
     * медицинские данные (НФТ-19, `TDD.md §11`). Имена и названия ищутся по всем полям
     * каждой записи: имена профилей и названия лекарств мира известны сценарию.
     */
    @Then("^названия лекарств, дозировки и имена профилей в журнал не попадают$")
    fun theJournalHasNoMedicalData() {
        assertTrue(world.log.records.isNotEmpty(), "the scenario must have left at least one journal record")
        val names = buildList {
            addAll(world.profiles.all().map { it.name })
            addAll(world.medications.names())
            addAll(world.medications.all().map { it.name })
        }.filter { it.length > SHORTEST_SENSITIVE }
        world.log.records.forEach { record ->
            val fields = listOfNotNull(
                record.profileId,
                record.medicationId,
                record.intakeId,
                record.before,
                record.after,
                record.source,
                record.detail,
            )
            fields.forEach { field ->
                names.forEach { name ->
                    assertFalse(
                        field.contains(name, ignoreCase = true),
                        "the journal record «${record.event}» carries «$name»: $field",
                    )
                }
            }
        }
    }

    // --- Обвязка ---------------------------------------------------------------------

    /** Лекарства профиля с расписанием: только они порождают приёмы. */
    private fun scheduledMedications(profile: String = world.profiles.active): List<Medication> {
        val profileId = world.profiles.get(profile).id
        return world.medications.all()
            .filter { it.profileId == profileId && it.times.isNotEmpty() && it.scheduleMode == ScheduleMode.SCHEDULED }
            .sortedBy { it.id }
    }

    /** Все приёмы сентября активного профиля по расписанию: пары «лекарство, момент». */
    private fun septemberSlots(): List<Pair<String, LocalDateTime>> = monthSlots(SEPTEMBER)

    private fun monthSlots(
        month: YearMonth,
        profile: String = world.profiles.active,
    ): List<Pair<String, LocalDateTime>> = scheduledMedications(profile)
        .flatMap { medication ->
            (1..month.lengthOfMonth()).flatMap { day ->
                val date = month.atDay(day)
                medication.times.sorted().map { time -> nameOf(medication.id) to date.atTime(time) }
            }
        }
        .distinctBy { (name, moment) -> world.medications.get(name).id to moment }
        .sortedBy { it.second }

    /** Приёмы одного дня: чем их наполнять в примерах структуры. */
    private fun daySlots(date: LocalDate): List<Pair<String, LocalDateTime>> =
        septemberSlots().filter { it.second.toLocalDate() == date }

    /**
     * Доля принятого у лекарства: все приёмы сентября заводятся ожидающимися,
     * принятыми отмечаются первые `shareOf`.
     */
    private fun putShare(name: String, slots: List<Pair<String, LocalDateTime>>, shareOf: (Int) -> Int) {
        world.intakes.removeOf(world.medications.get(name).id)
        slots.forEach { (medication, moment) -> world.intakes.pending(medication, moment) }
        slots.take(shareOf(slots.size).coerceIn(0, slots.size)).forEach { (medication, moment) ->
            world.intakes.markTaken(medication, moment)
        }
    }

    private fun markSkipped(name: String, moment: LocalDateTime) {
        val scenarioMoment = world.clock.nowLocal()
        world.markExisting(name, moment, moment.toLocalTime(), MarkResult.SKIPPED)
        world.clock.moveTo(scenarioMoment)
    }

    /** Лекарство из текста шага: заводится, если Предыстория его не называла. */
    private fun ensureMedication(medication: String, profile: String = world.profiles.active): String {
        val known = RussianNames.resolve(medication, world.medications.names())
        if (known != null) return known
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = medication,
            times = listOf(MORNING),
        )
        return medication
    }

    private fun nameOf(medicationId: String): String = requireNotNull(
        world.medications.names().firstOrNull { world.medications.get(it).id == medicationId },
    ) { "unknown medication id: $medicationId" }

    private companion object {
        val SEPTEMBER: YearMonth = YearMonth.of(2026, 9)
        val MORNING: LocalTime = LocalTime.of(8, 0)
        val EVENING: LocalTime = LocalTime.of(20, 0)

        const val ALL_TAKEN_EXAMPLE = "все приёмы приняты"
        const val PARTIAL_EXAMPLE = "часть приёмов принята, часть пропущена"
        const val MISSED_EXAMPLE = "все приёмы пропущены"
        const val NO_INTAKES_EXAMPLE = "приёмов не было запланировано"

        const val DAY_STATE_DAY = 15
        const val OWN_HISTORY_DAY = 20
        const val TAKEN_REFERENCE_DAY = 28
        const val MISSED_REFERENCE_DAY = 29
        const val LAST_DAY = 30
        const val EQUAL_MISSES = 6
        const val LOW_SHARE_DIVISOR = 10
        const val COURSE_DAYS = 10
        const val ARCHIVE_TIME = "12:00"
        const val SHORTEST_SENSITIVE = 1
        const val MANIFEST = "app/src/main/AndroidManifest.xml"
        const val CONTOUR_RULES =
            "build-logic/convention/src/main/kotlin/ru/stohastika/sklyanka/gradle/AnalyticsContourRules.kt"
        const val USES_PERMISSION = """<uses-permission[^>]*>"""
        const val REMOVE_DIRECTIVE = """tools:node="remove""""
        const val ANALYTICS_PERMISSION = "android.permission.INTERNET"

        val FORBIDDEN_NETWORK_PERMISSIONS = listOf(
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
        )
        val REPORT_DETAIL = Regex("""records=\d+;medications=\d+""")
    }
}
