package ru.heuri.sklyanka.acceptance

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.heuri.sklyanka.domain.intake.EndOfDayIntakeWindow
import ru.heuri.sklyanka.domain.intake.MarkOutcome
import ru.heuri.sklyanka.domain.intake.MarkResult
import ru.heuri.sklyanka.domain.intake.PrnOutcome
import ru.heuri.sklyanka.domain.intake.SweepReason
import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.ScheduleMode
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Шаги приёмки SKL-07 — отметка приёма и жизненный цикл приёма.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса: шаг обязан
 * совпадать с документом дословно, иначе прогон перестаёт быть контрактом задачи. Правило
 * RussianTextInCode для пакета ru.heuri.sklyanka.acceptance отключено именно поэтому
 * (config/detekt/detekt.yml).
 *
 * Сценарии гоняют настоящие доменные сценарии отметки, создания приёма по потребности и
 * прохода по просроченным приёмам поверх хранилища в памяти: домен для того и вынесен
 * в чистый Kotlin, чтобы приёмка не требовала эмулятора.
 *
 * Выражения шагов намеренно узкие. Прогон общий на все задачи процесса, и шаг, поймавший
 * чужую строку, превращает чужой нереализованный сценарий в упавший.
 */
@Suppress("TooManyFunctions", "LargeClass")
class IntakeLifecycleSteps {

    // Мир сценария общий со всеми классами шагов (см. AcceptanceWorlds): Предыстория
    // SKL-12 собирается шагами двух задач сразу, и смотреть они обязаны в одно состояние.
    private val world = AcceptanceWorlds.lifecycle()

    // --- Предыстория: лекарства, остаток, окно -------------------------------------------

    @Given(
        "^у профиля «(.+?)» есть лекарство «(.+?)» с (?:приёмом|приёмами) в " +
            "(\\d{2}:\\d{2}(?:(?:, | и )\\d{2}:\\d{2})*) по (\\S+) таблетк\\S+$",
    )
    fun theProfileHasAScheduledMedicationWithADose(profile: String, medication: String, times: String, dose: String) {
        world.addMedication(profileName = profile, name = medication, times = RussianDates.times(times))
        world.update(medication) { it.copy(doseAmount = RussianDates.amount(dose)) }
    }

    @Given("^у профиля «(.+?)» есть лекарство «(.+?)» с приёмом в (\\d{2}:\\d{2}) и без учёта остатка$")
    fun theProfileHasAMedicationWithoutStockTracking(profile: String, medication: String, time: String) {
        world.addMedication(profileName = profile, name = medication, times = listOf(LocalTime.parse(time)))
        world.update(medication) { it.copy(stockLeft = null) }
    }

    @Given(
        "^у профиля «(.+?)» есть лекарство «(.+?)» по потребности не чаще (\\d+) раз в сутки" +
            "(?:, по (\\S+) таблетке)?$",
    )
    fun theProfileHasAnOnDemandMedication(profile: String, medication: String, limit: Int, dose: String?) {
        world.addMedication(
            profileName = profile,
            name = medication,
            mode = ScheduleMode.PRN,
            maxPerDay = limit,
        )
        dose?.let { amount -> world.update(medication) { it.copy(doseAmount = RussianDates.amount(amount)) } }
    }

    /**
     * Строка про остаток встречается и в Предыстории, и в проверке — дословно одна и та же.
     * До первого действия сценария она задаёт остаток, после — проверяет его.
     */
    @Given("^остаток упаковки «(.+?)» — (\\d+) таблет\\S+$")
    fun theStockOfThatMedication(medication: String, amount: Int) {
        if (world.acted) {
            assertEquals(BigDecimal(amount), world.stockOf(medication), "stock of $medication")
        } else {
            world.update(medication) { it.copy(stockLeft = BigDecimal(amount)) }
        }
    }

    @Given("^доза одного приёма «(.+?)» — (\\S+) таблетк\\S+$")
    fun theDoseOfOneIntakeIs(medication: String, dose: String) {
        // Доза приходит и цифрой, и русской дробью «0,5» — таблица примеров SKL-12.
        world.update(medication) { it.copy(doseAmount = RussianDates.amount(dose)) }
    }

    @Given("^окно приёма — до конца суток$")
    fun theIntakeWindowRunsUntilTheEndOfTheDay() {
        world.window = EndOfDayIntakeWindow()
    }

    // --- Предыстория: приёмы --------------------------------------------------------------

    @Given("^приём «(.+?)» на (.+?), (\\d{2}:\\d{2}) (?:ожидается|не отмечался)$")
    fun thatIntakeIsPending(medication: String, date: String, time: String) {
        world.pending(medication, moment(date, time))
    }

    @Given("^человек его не отмечал$")
    fun thePersonNeverMarkedIt() {
        assertTrue(world.current().isPending, "the intake is already marked")
    }

    @Given("^приём «(.+?)» на (.+?), (\\d{2}:\\d{2}) отмечен принятым в (\\d{2}:\\d{2})(?: в телефоне)?$")
    fun thatIntakeIsAlreadyTaken(medication: String, date: String, time: String, markedAt: String) {
        markExisting(medication, moment(date, time), markedAt, MarkResult.TAKEN)
    }

    @Given("^приём «(.+?)» на (.+?), (\\d{2}:\\d{2}) отмечен пропущенным человеком в (\\d{2}:\\d{2})$")
    fun thatIntakeIsAlreadySkipped(medication: String, date: String, time: String, markedAt: String) {
        markExisting(medication, moment(date, time), markedAt, MarkResult.SKIPPED)
    }

    @Given(
        "^приём «(.+?)» на (.+?), (\\d{2}:\\d{2}) стал пропущенным без отметки (.+?) в (\\d{2}:\\d{2})$",
    )
    fun thatIntakeBecameMissedAt(medication: String, date: String, time: String, day: String, at: String) {
        val intake = world.pending(medication, moment(date, time))
        world.moveTo(moment(day, at))
        world.sweep(SweepReason.TIMER)
        world.focusOn(intake)
    }

    @Given("^у профиля «(.+?)» есть (\\S+) неотмеченных приёма за (.+)$")
    fun theProfileHasThatManyUnmarkedIntakes(profile: String, count: String, date: String) {
        val day = date(date)
        val medication = world.lastMedication
        // Сценарий говорит о числе неотмеченных приёмов за сутки, а не о расписании:
        // приёмы раскладываются по суткам равномерно, лишь бы их было ровно столько.
        assertEquals(
            world.profile(profile).id,
            world.medication(medication).profileId,
            "the intakes must belong to the profile the scenario names",
        )
        repeat(RussianDates.count(count)) { index ->
            world.pending(medication, day.atTime(MORNING.plusHours(HOURS_BETWEEN * index.toLong())))
        }
    }

    @Given("^устройство было выключено с (.+?), (\\d{2}:\\d{2})$")
    fun theDeviceWasSwitchedOffAt(date: String, time: String) {
        // Выключенное устройство ничего не делает: ни один проход по просроченным приёмам
        // в это время не выполняется (сценарий И-10 из docs/spec/04-reminders.md).
        world.moveTo(moment(date, time))
    }

    @Given("^приёмов «(.+?)» за (.+?) ещё не было$")
    fun thereWereNoIntakesOfThatMedicationYet(medication: String, date: String) {
        assertEquals(0, intakesOn(medication, date(date)).size)
    }

    @Given("^за (.+?) (?:уже )?отмечено (\\d+) приём\\S* «(.+?)»(?:, последний в (\\d{2}:\\d{2}))?$")
    fun thatManyOnDemandIntakesAreAlreadyMarked(date: String, count: Int, medication: String, last: String?) {
        val day = date(date)
        repeat(count) { index ->
            val at = if (last != null && index == count - 1) {
                LocalTime.parse(last)
            } else {
                MORNING.plusHours(index.toLong())
            }
            world.moveTo(day.atTime(at))
            world.pressTakeOnDemand(medication)
        }
        // Предыстория задаёт число приёмов, а не расход упаковки: остаток сценарий
        // задаёт отдельной строкой после этой.
        world.update(medication) { it.copy(stockLeft = null) }
    }

    @Given("^у профиля «(.+?)» есть приём лекарства «(.+?)» на (.+?), (\\d{2}:\\d{2})$")
    fun theProfileHasAnIntakeOfThatMedication(profile: String, medication: String, date: String, time: String) {
        world.addMedication(profileName = profile, name = medication, times = listOf(LocalTime.parse(time)))
        world.pending(medication, moment(date, time))
    }

    @Given("^часы не подключены$")
    fun theWatchIsNotConnected() {
        // Недоступность часов — не ошибка сценария: приём отмечается на телефоне,
        // и доменный слой о часах вообще не знает (docs/spec/05-watch.md).
    }

    @Given("^в (\\d{2}:\\d{2}) человек отложил напоминание на (\\d+) минут$")
    fun thePersonSnoozedTheReminder(time: String, minutes: Int) {
        // Откладывание — поведение будильника (SKL-13). Здесь важно одно: назначенное
        // время приёма при этом не переписывается (НАП-31), и именно это проверяет шаг «Тогда».
        world.moveTo(world.today().atTime(LocalTime.parse(time)).plusMinutes(minutes.toLong()))
    }

    @Given("^в (\\d{2}:\\d{2}) человек нажал «(Принято|Пропустить)» на часах$")
    fun thePersonPressedOnTheWatch(time: String, button: String) {
        world.pressOnWatch(resultOfButton(button), world.today().atTime(LocalTime.parse(time)))
    }

    @Given("^это нажатие доходит до телефона в (\\d{2}:\\d{2})$")
    fun thatPressReachesThePhoneAt(time: String) {
        world.delayLastPress(world.today().atTime(LocalTime.parse(time)))
    }

    // --- Действия ---------------------------------------------------------------------------

    @When("^(.+?) в (\\d{2}:\\d{2}) человек отмечает приём (принятым|пропущенным)(?: в телефоне)?$")
    fun thePersonMarksTheIntakeOnThePhone(date: String, time: String, result: String) {
        markFromPhone(moment(date, time), resultOf(result))
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) владелец телефона отмечает приём (принятым|пропущенным)$")
    fun theOwnerOfThePhoneMarksTheIntake(date: String, time: String, result: String) {
        markFromPhone(moment(date, time), resultOf(result))
    }

    @When("^владелец телефона отмечает этот приём принятым (.+?) в (\\d{2}:\\d{2})$")
    fun theOwnerOfThePhoneMarksThatIntakeTaken(date: String, time: String) {
        markFromPhone(moment(date, time), MarkResult.TAKEN)
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) тот же приём отмечают (принятым|пропущенным)(?: в телефоне| ещё раз)?$")
    fun theSameIntakeIsMarkedAgain(date: String, time: String, result: String) {
        markFromPhone(moment(date, time), resultOf(result))
    }

    @When("^в (\\d{2}:\\d{2}) человек отмечает тот же приём (принятым|пропущенным) в телефоне$")
    fun thePersonMarksTheSameIntakeAt(time: String, result: String) {
        markFromPhone(world.today().atTime(LocalTime.parse(time)), resultOf(result))
    }

    @When("^в (\\d{2}:\\d{2}) этот приём отмечают (принятым|пропущенным) на часах$")
    fun thatIntakeIsMarkedOnTheWatch(time: String, result: String) {
        world.rememberState()
        val at = world.today().atTime(LocalTime.parse(time))
        val intake = world.currentOrScheduled()
        world.moveTo(at)
        world.mark(intake.id, resultOf(result), IntakeSource.WATCH, world.momentOf(at))
    }

    @When("^отметка с часов и отметка с телефона приходят одновременно в (\\d{2}:\\d{2})$")
    fun bothMarksArriveAtTheSameMoment(time: String) {
        world.rememberState()
        val at = world.today().atTime(LocalTime.parse(time))
        val intake = world.currentOrScheduled()
        world.moveTo(at)
        // Один и тот же момент действия: правило первенства обязано оставить первую
        // запись и отбросить вторую, а не сложить оба списания (ЧАС-23).
        world.mark(intake.id, MarkResult.TAKEN, IntakeSource.WATCH, world.momentOf(at))
        world.mark(intake.id, MarkResult.TAKEN, IntakeSource.PHONE, world.momentOf(at))
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) человек пытается отметить его (принятым|пропущенным)$")
    fun thePersonTriesToMarkIt(date: String, time: String, result: String) {
        markFromPhone(moment(date, time), resultOf(result))
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) человек пытается отменить свою отметку$")
    fun thePersonTriesToUndoTheMark(date: String, time: String) {
        // Операции отмены в домене нет (ОВ-6, необратимость переходов). Единственное,
        // что может сделать вызывающий, — обратиться к той же точке входа ещё раз,
        // и она молча отказывает. Ограничение временное, а не свойство предметной области.
        markFromPhone(moment(date, time), MarkResult.TAKEN)
    }

    @When("^приём завершается исходом «(.+?)»$")
    fun theIntakeEndsWithThatOutcome(outcome: String) {
        world.rememberState()
        val intake = world.currentOrScheduled()
        when (statusOf(outcome)) {
            IntakeStatus.MISSED -> {
                world.moveTo(world.window.deadlineFor(intake.scheduledAt))
                world.sweep(SweepReason.TIMER)
            }

            IntakeStatus.SKIPPED -> world.mark(
                intake.id,
                MarkResult.SKIPPED,
                IntakeSource.PHONE,
                world.clock.now(),
            )

            else -> world.mark(intake.id, MarkResult.TAKEN, IntakeSource.PHONE, world.clock.now())
        }
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) человек нажимает «Принять» для «(.+?)»$")
    fun thePersonPressesTakeForThatMedication(date: String, time: String, medication: String) {
        world.rememberState()
        world.moveTo(moment(date, time))
        world.pressTakeOnDemand(medication)
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) человек дважды подряд нажимает «Принять» для «(.+?)»$")
    fun thePersonPressesTakeTwiceInARow(date: String, time: String, medication: String) {
        world.rememberState()
        world.moveTo(moment(date, time))
        repeat(2) { world.pressTakeOnDemand(medication) }
    }

    @When("^наступает (.+?), (\\d{2}:\\d{2})$")
    fun thatMomentComes(date: String, time: String) {
        advanceAndSweep(moment(date, time), SweepReason.TIMER)
    }

    @When("^устройство включают (.+?) в (\\d{2}:\\d{2})$")
    fun theDeviceIsSwitchedOnAt(date: String, time: String) {
        advanceAndSweep(moment(date, time), SweepReason.APP_START)
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) приложение снова проверяет просроченные приёмы$")
    fun theApplicationSweepsAgain(date: String, time: String) {
        advanceAndSweep(moment(date, time), SweepReason.APP_START)
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) системные часы переводят на (.+?), (\\d{2}:\\d{2})$")
    fun theSystemClockIsMovedTo(date: String, time: String, toDate: String, toTime: String) {
        world.rememberState()
        world.moveTo(moment(date, time))
        // Перевод часов — повод перепланировать, а не пересчитать историю задним числом
        // (НФТ-35): проход выполняется, но сравнивает границу окна каждого приёма.
        advanceAndSweep(moment(toDate, toTime), SweepReason.REPLAN)
    }

    // --- Проверки: состояние приёма -------------------------------------------------------------

    @Then("^приём (?:считается|остаётся) (принятым|пропущенным осознанно|пропущенным без отметки|ожидающимся)$")
    fun theIntakeIsInThatState(state: String) {
        assertEquals(statusOf(state), world.current().status, describe())
    }

    @Then("^(утренний|вечерний) приём считается (принятым|пропущенным осознанно|пропущенным без отметки)$")
    fun theMorningOrEveningIntakeIsInThatState(half: String, state: String) {
        val intakes = world.intakesOf(world.lastMedication)
        val intake = if (half == MORNING_WORD) {
            intakes.first { it.scheduledAt.toLocalTime() < NOON }
        } else {
            intakes.first { it.scheduledAt.toLocalTime() >= NOON }
        }
        assertEquals(statusOf(state), intake.status, "intake at ${intake.scheduledAt}")
    }

    @Then(
        "^приём на (.+?), (\\d{2}:\\d{2}) (?:считается|остаётся) " +
            "(принятым|пропущенным осознанно|пропущенным без отметки|ожидающимся)$",
    )
    fun theIntakeOfThatMomentIsInThatState(date: String, time: String, state: String) {
        val intake = world.intakeAt(world.lastMedication, moment(date, time))
        assertEquals(statusOf(state), intake.status, "intake at ${intake.scheduledAt}")
    }

    @Then("^приём профиля «(.+?)» считается (принятым|пропущенным осознанно|пропущенным без отметки)$")
    fun theIntakeOfThatProfileIsInThatState(profile: String, state: String) {
        val intake = world.intakesOfProfile(profile).first()
        assertEquals(statusOf(state), intake.status, "intake of $profile")
    }

    @Then("^у приёма ровно одно состояние — (принято|пропущено осознанно|пропущено без отметки)$")
    fun theIntakeHasExactlyOneState(state: String) {
        assertEquals(statusOf(state), world.current().status, describe())
    }

    @Then("^все (\\S+) приёма считаются пропущенными без отметки$")
    fun allOfThemAreMissed(count: String) {
        val intakes = world.intakesOfProfile(world.activeProfile)
        assertEquals(RussianDates.count(count), intakes.size)
        assertTrue(intakes.all { it.status == IntakeStatus.MISSED }, "states: ${intakes.map { it.status }}")
    }

    @Then("^человек видит приём принятым$")
    fun thePersonSeesTheIntakeAsTaken() {
        assertEquals(IntakeStatus.TAKEN, world.current().status, describe())
    }

    @Then("^второй записи об этом приёме не появляется$")
    fun noSecondRecordAppears() {
        val current = world.current()
        assertEquals(
            1,
            world.intakesOf(world.lastMedication).count { it.scheduledAt == current.scheduledAt },
            describe(),
        )
    }

    // --- Проверки: поля отметки --------------------------------------------------------------------

    @Then("^временем отметки (?:записано|остаётся) (.+?), (\\d{2}:\\d{2})$")
    fun theMomentOfTheMarkIs(date: String, time: String) {
        assertEquals(world.momentOf(moment(date, time)), world.current().actedAt, describe())
    }

    @Then("^источником отметки запис\\S+ (телефон|часы|автоматическое действие приложения)$")
    fun theSourceOfTheMarkIs(source: String) {
        assertEquals(sourceOf(source), world.current().source, describe())
    }

    @Then("^назначенным временем приёма остаётся (.+?), (\\d{2}:\\d{2})$")
    fun theScheduledMomentStays(date: String, time: String) {
        assertEquals(moment(date, time), world.current().scheduledAt, describe())
    }

    @Then("^назначенное время приёма равно (?:времени отметки — )?(.+?), (\\d{2}:\\d{2})$")
    fun theScheduledMomentEqualsThat(date: String, time: String) {
        val intake = world.current()
        assertEquals(moment(date, time), intake.scheduledAt, describe())
        assertEquals(world.momentOf(intake.scheduledAt), intake.actedAt, "an on-demand intake is marked when created")
    }

    @Then("^момент отметки остаётся тем же моментом времени$")
    fun theMomentOfTheMarkIsUnchanged() {
        val intake = world.current()
        val recorded = world.appliedMarks[intake.id]?.actedAt
        assertNotNull(recorded, "the scenario marked nothing before the action")
        // Момент отметки — факт в UTC: ни смена пояса, ни перелёт его не сдвигают (НФТ-34).
        assertEquals(recorded, intake.actedAt)
    }

    @Then("^тем, кто отметил, никто не записан$")
    fun nobodyIsRecordedBehindTheMark() {
        assertNull(world.current().actorProfileId, describe())
    }

    @Then("^в истории отметка подписана «отметили вы»$")
    fun theHistoryShowsThatYouMarkedIt() {
        assertNotNull(world.current().actorProfileId, "the mark of a foreign profile must carry its actor")
    }

    @Then("^в истории подписи «отметили вы» нет$")
    fun theHistoryShowsNoSuchSignature() {
        assertNull(world.current().actorProfileId, describe())
    }

    @Then("^активным на телефоне остаётся профиль «(.+?)»$")
    fun theActiveProfileStays(profile: String) {
        assertEquals(profile, world.activeProfile)
    }

    @Then("^приёмы профиля «(.+?)» не изменяются$")
    fun theIntakesOfThatProfileAreUnchanged(profile: String) {
        world.intakesOfProfile(profile).forEach { intake ->
            assertEquals(world.before[intake.id], intake, "intake at ${intake.scheduledAt}")
        }
    }

    // --- Проверки: остаток и приём по потребности -------------------------------------------------------

    @Then("^учёт остатка «(.+?)» остаётся невключённым$")
    fun theStockOfThatMedicationStaysUntracked(medication: String) {
        assertNull(world.stockOf(medication), "stock of $medication")
    }

    @Then("^появляется приём «(.+?)», считающийся принятым$")
    fun anIntakeOfThatMedicationAppearsTaken(medication: String) {
        val outcome = world.lastPrnOutcome
        assertTrue(outcome is PrnOutcome.Created, "expected a new on-demand intake, got $outcome")
        assertEquals(IntakeStatus.TAKEN, (outcome as PrnOutcome.Created).intake.status)
        assertEquals(world.medication(medication).id, outcome.intake.medicationId)
    }

    @Then("^число приёмов «(.+?)» за (.+?) (?:становится|—) (\\d+)$")
    fun theNumberOfIntakesOfThatDayIs(medication: String, date: String, expected: Int) {
        assertEquals(expected, intakesOn(medication, date(date)).size, "intakes of $medication on $date")
    }

    @Then("^среди приёмов «(.+?)» нет ни одного ожидающегося$")
    fun noPendingIntakeOfThatMedicationExists(medication: String) {
        assertTrue(world.intakesOf(medication).none { it.isPending })
    }

    @Then("^ни один приём «(.+?)» не становится пропущенным без отметки$")
    fun noIntakeOfThatMedicationBecomesMissed(medication: String) {
        assertTrue(world.intakesOf(medication).none { it.status == IntakeStatus.MISSED })
    }

    // --- Проверки: чего человек не видит ---------------------------------------------------------------

    @Then("^человек не видит ни сообщения об ошибке, ни предупреждения о конфликте$")
    fun thePersonSeesNeitherAnErrorNorAConflictWarning() {
        assertTrue(world.messagesToHuman.isEmpty(), "messages shown: ${world.messagesToHuman}")
        assertTrue(
            world.outcomes.any { it is MarkOutcome.Ignored || it is MarkOutcome.Superseded },
            "the scenario never produced a losing mark",
        )
    }

    @Then("^человек не видит сообщения об ошибке связи с часами$")
    fun thePersonSeesNoWatchError() {
        assertTrue(world.messagesToHuman.isEmpty(), "messages shown: ${world.messagesToHuman}")
    }

    @Then("^человеку понятно, что отметка уже сделана$")
    fun thePersonCanTellTheIntakeIsAlreadyMarked() {
        val outcome = world.outcomes.last()
        assertTrue(outcome is MarkOutcome.Ignored, "expected a silent refusal, got $outcome")
        assertNotNull(outcome.intake, "the refusal must carry the state the person sees")
    }

    @Then("^человек не получает ни одного уведомления об этом$")
    fun thePersonGetsNoNotification() {
        assertTrue(world.messagesToHuman.isEmpty(), "messages shown: ${world.messagesToHuman}")
        // У прохода по просроченным приёмам нет порта уведомлений вовсе: в журнале
        // остаётся один перевод и ни одной попытки что-то показать человеку (НАП-34).
        assertEquals(listOf(OpEvent.INTAKE_AUTO_MISSED), world.log.events)
    }

    // --- Вспомогательное --------------------------------------------------------------------------------

    private fun markExisting(medication: String, at: LocalDateTime, markedAt: String, result: MarkResult) {
        val intake = world.pending(medication, at)
        val moment = at.toLocalDate().atTime(LocalTime.parse(markedAt))
        world.moveTo(moment)
        world.mark(intake.id, result, IntakeSource.PHONE, world.momentOf(moment))
        world.focusOn(intake)
    }

    /**
     * Отметка с телефона.
     *
     * Перед действием доходят нажатия с часов, чей срок доставки уже наступил, после —
     * все остальные: сообщение с часов приходит позже нажатия, и именно на этом стоит
     * правило первенства по времени действия (ПР-3).
     */
    private fun markFromPhone(at: LocalDateTime, result: MarkResult) {
        world.rememberState()
        world.deliverPressesUntil(world.momentOf(at))
        world.markCurrent(result, IntakeSource.PHONE, at)
        world.deliverRemainingPresses()
    }

    private fun advanceAndSweep(at: LocalDateTime, reason: SweepReason) {
        world.rememberState()
        world.moveTo(at)
        world.sweep(reason)
    }

    private fun intakesOn(medication: String, date: LocalDate): List<Intake> =
        world.intakesOf(medication).filter { it.scheduledAt.toLocalDate() == date }

    private fun resultOf(text: String): MarkResult =
        if (text.startsWith(TAKEN_WORD)) MarkResult.TAKEN else MarkResult.SKIPPED

    private fun resultOfButton(button: String): MarkResult =
        if (button == TAKE_BUTTON) MarkResult.TAKEN else MarkResult.SKIPPED

    private fun statusOf(text: String): IntakeStatus = when {
        text.startsWith(TAKEN_WORD) || text.startsWith(TAKEN_NEUTER) -> IntakeStatus.TAKEN
        text.endsWith(WITHOUT_A_MARK) -> IntakeStatus.MISSED
        text.startsWith(PENDING_WORD) -> IntakeStatus.PENDING
        else -> IntakeStatus.SKIPPED
    }

    private fun sourceOf(text: String): IntakeSource = when (text) {
        PHONE_WORD -> IntakeSource.PHONE
        WATCH_WORD -> IntakeSource.WATCH
        else -> IntakeSource.AUTO
    }

    private fun date(text: String): LocalDate = RussianDates.date(text, world.today())

    private fun moment(date: String, time: String): LocalDateTime = date(date).atTime(LocalTime.parse(time))

    private fun describe(): String = "intake: " + world.current()

    private companion object {
        val MORNING: LocalTime = LocalTime.of(8, 0)
        val NOON: LocalTime = LocalTime.of(12, 0)

        /** Шаг про четыре неотмеченных приёма раскладывает их по суткам с этим шагом. */
        const val HOURS_BETWEEN = 4L

        const val MORNING_WORD = "утренний"
        const val TAKEN_WORD = "принят"
        const val TAKEN_NEUTER = "принято"
        const val PENDING_WORD = "ожидающ"
        const val WITHOUT_A_MARK = "без отметки"
        const val PHONE_WORD = "телефон"
        const val WATCH_WORD = "часы"
        const val TAKE_BUTTON = "Принято"
    }
}
