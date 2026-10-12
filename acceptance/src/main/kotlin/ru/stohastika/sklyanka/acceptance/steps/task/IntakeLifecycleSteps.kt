package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.steps.resultOf
import ru.stohastika.sklyanka.acceptance.steps.sourceOf
import ru.stohastika.sklyanka.acceptance.steps.statusOf
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.intake.EndOfDayIntakeWindow
import ru.stohastika.sklyanka.domain.intake.MarkOutcome
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.intake.PrnOutcome
import ru.stohastika.sklyanka.domain.intake.SweepReason
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Шаги приёмки SKL-07 — окно приёма, проход по просроченным приёмам и лимит
 * приёмов по потребности.
 *
 * Здесь остался предмет самой задачи. Допущения предметной области — лекарство с дозой,
 * остаток упаковки, ожидающийся приём, отметка и её поля — переехали в общий словарь
 * `steps/shared`: те же строки написаны в Предысториях других задач, и означать в них
 * они обязаны одно и то же.
 *
 * Шаги «наступает …», «устройство включают …», «системные часы переводят на …» остались
 * задачными намеренно: кроме движения часов они гоняют проход по просроченным приёмам,
 * а это предмет SKL-07, а не словарь времени. Чистое движение часов живёт
 * в `steps/shared/ClockSteps`.
 *
 * Состояние сценария приезжает параметром конструктора: оно общее со всеми классами шагов
 * этого сценария. Обходные пути, которыми это состояние раньше добиралось до чужих шагов —
 * копия момента Предыстории и профиль владельца, заведённый в `init`, — исчезли вместе
 * с причиной.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса: шаг обязан
 * совпадать с документом дословно, иначе прогон перестаёт быть контрактом задачи. Правило
 * RussianTextInCode разрешает пакет ru.stohastika.sklyanka.acceptance вместе с подпакетами
 * именно поэтому (config/detekt/detekt.yml).
 *
 * Сценарии гоняют настоящие доменные сценарии отметки, создания приёма по потребности и
 * прохода по просроченным приёмам поверх хранилища в памяти: домен для того и вынесен
 * в чистый Kotlin, чтобы приёмка не требовала эмулятора.
 *
 * Выражения шагов намеренно узкие. Прогон общий на все задачи процесса, и шаг, поймавший
 * чужую строку, превращает чужой нереализованный сценарий в упавший.
 */
@Suppress("TooManyFunctions", "LargeClass")
class IntakeLifecycleSteps(private val world: AcceptanceWorld) {

    // --- Предыстория: лекарства, остаток, окно -------------------------------------------

    @Given("^окно приёма — до конца суток$")
    fun theIntakeWindowRunsUntilTheEndOfTheDay() {
        world.intakes.window = EndOfDayIntakeWindow()
    }

    // --- Предыстория: приёмы --------------------------------------------------------------

    // «Человеком» необязательно: тот же переход пишут и без этого слова (SKL-30).
    @Given("^приём «(.+?)» на (.+?), (\\d{2}:\\d{2}) отмечен пропущенным(?: человеком)? в (\\d{2}:\\d{2})$")
    fun thatIntakeIsAlreadySkipped(medication: String, date: String, time: String, markedAt: String) {
        world.markExisting(medication, moment(date, time), LocalTime.parse(markedAt), MarkResult.SKIPPED)
    }

    @Given(
        "^приём «(.+?)» на (.+?), (\\d{2}:\\d{2}) стал пропущенным без отметки (.+?) в (\\d{2}:\\d{2})$",
    )
    fun thatIntakeBecameMissedAt(medication: String, date: String, time: String, day: String, at: String) {
        val intake = world.intakes.pending(medication, moment(date, time))
        world.clock.moveTo(moment(day, at))
        world.sweep(SweepReason.TIMER)
        world.intakes.focusOn(intake)
    }

    @Given("^у профиля «(.+?)» есть (\\S+) неотмеченных приёма за (.+)$")
    fun theProfileHasThatManyUnmarkedIntakes(profile: String, count: String, date: String) {
        val day = date(date)
        val medication = world.medications.last
        // Сценарий говорит о числе неотмеченных приёмов за сутки, а не о расписании:
        // приёмы раскладываются по суткам равномерно, лишь бы их было ровно столько.
        assertEquals(
            world.profiles.get(profile).id,
            world.medications.get(medication).profileId,
            "the intakes must belong to the profile the scenario names",
        )
        repeat(RussianDates.count(count)) { index ->
            world.intakes.pending(medication, day.atTime(MORNING.plusHours(HOURS_BETWEEN * index.toLong())))
        }
    }

    @Given("^устройство было выключено с (.+?), (\\d{2}:\\d{2})$")
    fun theDeviceWasSwitchedOffAt(date: String, time: String) {
        // Выключенное устройство ничего не делает: ни один проход по просроченным приёмам
        // в это время не выполняется (сценарий И-10 из docs/norm/04-behavior.md).
        world.clock.moveTo(moment(date, time))
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
            world.clock.moveTo(day.atTime(at))
            world.pressTakeOnDemand(medication)
        }
        // Предыстория задаёт число приёмов, а не расход упаковки: остаток сценарий
        // задаёт отдельной строкой после этой.
        world.medications.update(medication) { it.copy(stockLeft = null) }
    }

    @Given("^у профиля «(.+?)» есть приём лекарства «(.+?)» на (.+?), (\\d{2}:\\d{2})$")
    fun theProfileHasAnIntakeOfThatMedication(profile: String, medication: String, date: String, time: String) {
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = medication,
            times = listOf(LocalTime.parse(time)),
        )
        world.intakes.pending(medication, moment(date, time))
    }

    @Given("^в (\\d{2}:\\d{2}) человек отложил напоминание на (\\d+) минут$")
    fun thePersonSnoozedTheReminder(time: String, minutes: Int) {
        // Откладывание — поведение будильника (SKL-13). Здесь важно одно: назначенное
        // время приёма при этом не переписывается (НАП-31), и именно это проверяет шаг «Тогда».
        world.clock.moveTo(world.clock.today().atTime(LocalTime.parse(time)).plusMinutes(minutes.toLong()))
    }

    // --- Действия ---------------------------------------------------------------------------

    @When("^(.+?) в (\\d{2}:\\d{2}) владелец телефона отмечает приём (принятым|пропущенным)$")
    fun theOwnerOfThePhoneMarksTheIntake(date: String, time: String, result: String) {
        world.markFromPhone(moment(date, time), resultOf(result))
    }

    @When("^владелец телефона отмечает этот приём принятым (.+?) в (\\d{2}:\\d{2})$")
    fun theOwnerOfThePhoneMarksThatIntakeTaken(date: String, time: String) {
        world.markFromPhone(moment(date, time), MarkResult.TAKEN)
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) тот же приём отмечают (принятым|пропущенным)(?: в телефоне| ещё раз)?$")
    fun theSameIntakeIsMarkedAgain(date: String, time: String, result: String) {
        world.markFromPhone(moment(date, time), resultOf(result))
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) человек пытается отметить его (принятым|пропущенным)$")
    fun thePersonTriesToMarkIt(date: String, time: String, result: String) {
        world.markFromPhone(moment(date, time), resultOf(result))
    }

    @When("^(.+?) в (\\d{2}:\\d{2}) человек пытается отменить свою отметку$")
    fun thePersonTriesToUndoTheMark(date: String, time: String) {
        // Операции отмены в домене нет (ОВ-6, необратимость переходов). Единственное,
        // что может сделать вызывающий, — обратиться к той же точке входа ещё раз,
        // и она молча отказывает. Ограничение временное, а не свойство предметной области.
        world.markFromPhone(moment(date, time), MarkResult.TAKEN)
    }

    @When("^приём завершается исходом «(.+?)»$")
    fun theIntakeEndsWithThatOutcome(outcome: String) {
        world.rememberState()
        val intake = world.intakes.currentOrScheduled()
        when (statusOf(outcome)) {
            IntakeStatus.MISSED -> {
                world.clock.moveTo(world.intakes.window.deadlineFor(intake.scheduledAt))
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

    @When("^(.+?) в (\\d{2}:\\d{2}) человек дважды подряд нажимает «Принять» для «(.+?)»$")
    fun thePersonPressesTakeTwiceInARow(date: String, time: String, medication: String) {
        world.rememberState()
        world.clock.moveTo(moment(date, time))
        repeat(2) { world.pressTakeOnDemand(medication) }
    }

    /**
     * Наступил момент: часы сдвинулись, просроченные переведены, будильники сработали.
     *
     * Доставка срабатываний и полночный проход добавлены SKL-13: момент — это не только
     * статус приёмов, но и показ напоминаний. У сценариев без будильников и уведомлений
     * обе части ничего не делают.
     */
    // Секунды необязательны: сценарии короткого окна отмены двигают часы на «14:05:05»
    // и «14:05:07» (SKL-30), остальным задачам хватает минут.
    @When("^наступает (.+?), (\\d{2}:\\d{2}(?::\\d{2})?)$")
    fun thatMomentComes(date: String, time: String) {
        val at = moment(date, time)
        advanceAndSweep(at, SweepReason.TIMER)
        world.reminders.deliverThrough(at)
        // Граница суток снимает карточки приёмов, которые перестали ожидаться (НАП-34).
        if (LocalTime.parse(time) == LocalTime.MIDNIGHT) world.reminders.dayBoundarySweep()
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
        world.clock.moveTo(moment(date, time))
        // Перевод часов — повод перепланировать, а не пересчитать историю задним числом
        // (НФТ-35): проход выполняется, но сравнивает границу окна каждого приёма.
        advanceAndSweep(moment(toDate, toTime), SweepReason.REPLAN)
    }

    // --- Проверки: состояние приёма -------------------------------------------------------------

    @Then("^(утренний|вечерний) приём считается (принятым|пропущенным осознанно|пропущенным без отметки)$")
    fun theMorningOrEveningIntakeIsInThatState(half: String, state: String) {
        val intakes = world.intakes.of(world.medications.last)
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
        val intake = world.intakes.at(world.medications.last, moment(date, time))
        assertEquals(statusOf(state), intake.status, "intake at ${intake.scheduledAt}")
    }

    @Then("^у приёма ровно одно состояние — (принято|пропущено осознанно|пропущено без отметки)$")
    fun theIntakeHasExactlyOneState(state: String) {
        assertEquals(statusOf(state), world.intakes.current().status, describe())
    }

    @Then("^все (\\S+) приёма считаются пропущенными без отметки$")
    fun allOfThemAreMissed(count: String) {
        val intakes = world.intakes.ofProfile(world.profiles, world.profiles.active)
        assertEquals(RussianDates.count(count), intakes.size)
        assertTrue(intakes.all { it.status == IntakeStatus.MISSED }, "states: ${intakes.map { it.status }}")
    }

    @Then("^человек видит приём принятым$")
    fun thePersonSeesTheIntakeAsTaken() {
        assertEquals(IntakeStatus.TAKEN, world.intakes.current().status, describe())
    }

    @Then("^второй записи об этом приёме не появляется$")
    fun noSecondRecordAppears() {
        val current = world.intakes.current()
        assertEquals(
            1,
            world.intakes.of(world.medications.last).count { it.scheduledAt == current.scheduledAt },
            describe(),
        )
    }

    // --- Проверки: поля отметки --------------------------------------------------------------------

    @Then("^источником отметки запис\\S+ (телефон|автоматическое действие приложения)$")
    fun theSourceOfTheMarkIs(source: String) {
        assertEquals(sourceOf(source), world.intakes.current().source, describe())
    }

    @Then("^назначенным временем приёма остаётся (.+?), (\\d{2}:\\d{2})$")
    fun theScheduledMomentStays(date: String, time: String) {
        assertEquals(moment(date, time), world.intakes.current().scheduledAt, describe())
    }

    @Then("^назначенное время приёма равно (?:времени отметки — )?(.+?), (\\d{2}:\\d{2})$")
    fun theScheduledMomentEqualsThat(date: String, time: String) {
        val intake = world.intakes.current()
        assertEquals(moment(date, time), intake.scheduledAt, describe())
        assertEquals(
            world.clock.momentOf(intake.scheduledAt),
            intake.actedAt,
            "an on-demand intake is marked when created",
        )
    }

    @Then("^момент отметки остаётся тем же моментом времени$")
    fun theMomentOfTheMarkIsUnchanged() {
        val intake = world.intakes.current()
        val recorded = world.intakes.appliedMarks[intake.id]?.actedAt
        assertNotNull(recorded, "the scenario marked nothing before the action")
        // Момент отметки — факт в UTC: ни смена пояса, ни перелёт его не сдвигают (НФТ-34).
        assertEquals(recorded, intake.actedAt)
    }

    @Then("^тем, кто отметил, никто не записан$")
    fun nobodyIsRecordedBehindTheMark() {
        assertNull(world.intakes.current().actorProfileId, describe())
    }

    @Then("^в истории подписи «отметили вы» нет$")
    fun theHistoryShowsNoSuchSignature() {
        assertNull(world.intakes.current().actorProfileId, describe())
    }

    @Then("^активным на телефоне остаётся профиль «(.+?)»$")
    fun theActiveProfileStays(profile: String) {
        assertEquals(profile, world.profiles.active)
    }

    // --- Проверки: остаток и приём по потребности -------------------------------------------------------

    @Then("^появляется приём «(.+?)», считающийся принятым$")
    fun anIntakeOfThatMedicationAppearsTaken(medication: String) {
        val outcome = world.intakes.lastPrnOutcome
        assertTrue(outcome is PrnOutcome.Created, "expected a new on-demand intake, got $outcome")
        assertEquals(IntakeStatus.TAKEN, (outcome as PrnOutcome.Created).intake.status)
        assertEquals(world.medications.get(medication).id, outcome.intake.medicationId)
    }

    @Then("^число приёмов «(.+?)» за (.+?) (?:становится|—) (\\d+)$")
    fun theNumberOfIntakesOfThatDayIs(medication: String, date: String, expected: Int) {
        assertEquals(expected, intakesOn(medication, date(date)).size, "intakes of $medication on $date")
    }

    @Then("^среди приёмов «(.+?)» нет ни одного ожидающегося$")
    fun noPendingIntakeOfThatMedicationExists(medication: String) {
        assertTrue(world.intakes.of(medication).none { it.isPending })
    }

    @Then("^ни один приём «(.+?)» не становится пропущенным без отметки$")
    fun noIntakeOfThatMedicationBecomesMissed(medication: String) {
        assertTrue(world.intakes.of(medication).none { it.status == IntakeStatus.MISSED })
    }

    // --- Проверки: чего человек не видит ---------------------------------------------------------------

    @Then("^человек не видит ни сообщения об ошибке, ни предупреждения о конфликте$")
    fun thePersonSeesNeitherAnErrorNorAConflictWarning() {
        assertTrue(world.intakes.messagesToHuman.isEmpty(), "messages shown: ${world.intakes.messagesToHuman}")
        assertTrue(
            world.intakes.outcomes.any { it is MarkOutcome.Ignored || it is MarkOutcome.Superseded },
            "the scenario never produced a losing mark",
        )
    }

    @Then("^человеку понятно, что отметка уже сделана$")
    fun thePersonCanTellTheIntakeIsAlreadyMarked() {
        val outcome = world.intakes.outcomes.last()
        assertTrue(outcome is MarkOutcome.Ignored, "expected a silent refusal, got $outcome")
        assertNotNull(outcome.intake, "the refusal must carry the state the person sees")
    }

    @Then("^человек не получает ни одного уведомления об этом$")
    fun thePersonGetsNoNotification() {
        assertTrue(world.intakes.messagesToHuman.isEmpty(), "messages shown: ${world.intakes.messagesToHuman}")
        // У прохода по просроченным приёмам нет порта уведомлений вовсе: в журнале
        // остаётся один перевод и ни одной попытки что-то показать человеку (НАП-34).
        assertEquals(listOf(OpEvent.INTAKE_AUTO_MISSED), world.log.events)
    }

    // --- Вспомогательное --------------------------------------------------------------------------------

    private fun advanceAndSweep(at: LocalDateTime, reason: SweepReason) {
        world.rememberState()
        world.clock.moveTo(at)
        world.sweep(reason)
    }

    /**
     * Записи лекарства за сутки, видимые продукту: снятые исключены (Р-46, SKL-127).
     *
     * Число приёмов суток — тот же источник, что у суточного лимита и блока «По потребности»:
     * условие `revoked_at IS NULL` выборок Room зеркалит `ScenarioIntakes.visibleOf`.
     */
    private fun intakesOn(medication: String, date: LocalDate): List<Intake> =
        world.intakes.visibleOf(medication).filter { it.scheduledAt.toLocalDate() == date }

    private fun date(text: String): LocalDate = RussianDates.date(text, world.clock.today())

    private fun moment(date: String, time: String): LocalDateTime = date(date).atTime(LocalTime.parse(time))

    private fun describe(): String = "intake: " + world.intakes.current()

    private companion object {
        val MORNING: LocalTime = LocalTime.of(8, 0)
        val NOON: LocalTime = LocalTime.of(12, 0)

        /** Шаг про четыре неотмеченных приёма раскладывает их по суткам с этим шагом. */
        const val HOURS_BETWEEN = 4L

        const val MORNING_WORD = "утренний"
    }
}
