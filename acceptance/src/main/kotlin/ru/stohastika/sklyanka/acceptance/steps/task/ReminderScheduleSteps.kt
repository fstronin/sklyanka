package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Но
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.steps.localMoment
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.ScenarioTransactor
import ru.stohastika.sklyanka.domain.intake.MarkOutcome
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.medication.ArchiveMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.ArchiveOutcome
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.medication.RestoreMedicationUseCase
import ru.stohastika.sklyanka.domain.medication.RestoreOutcome
import ru.stohastika.sklyanka.domain.medication.SaveResult
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.reminder.PersistentRepeatPolicy
import ru.stohastika.sklyanka.domain.reminder.ReminderCard
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import java.io.File
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Доменные шаги приёмки SKL-13: планирование, настойчивость, переход в пропущенные
 * и деградация без разрешений.
 *
 * Правила не копируются. Окно пересобирает настоящий `RescheduleRemindersUseCase`,
 * срабатывание — `HandleFiredReminderUseCase`, ночной проход — `SweepAtDayBoundaryUseCase`,
 * архивация, возврат и правка расписания — настоящие сценарии лекарства с планировщиком
 * мира. Здесь живут только Предыстория и последствия, которых у сценария не выпросить
 * портами: показ карточки, счёт повторов и ответ «напоминание снято из шторки».
 *
 * Экранные группы `@reminder-screen` и `@diagnostics` исполняются стендами модуля
 * `feature-reminder`; сюда заглядывают только их Предыстории.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@Suppress("TooManyFunctions", "LargeClass")
class ReminderScheduleSteps(private val world: AcceptanceWorld) {

    /** Лекарство, о котором говорит текущая проверка. */
    private var medication: String = DEFAULT_MEDICATION

    /** Остаток упаковки до ручной отметки: им проверяется «остаток списывается». */
    private var stockBefore: BigDecimal? = null

    // --- Предыстория ---------------------------------------------------------------------------

    /**
     * Ежедневный курс с приёмом: лекарство, его сегодняшний приём и поставленное напоминание.
     *
     * Предыстория говорит «сейчас 25 сентября 2026, 09:00» — приложение работает, окно
     * собрано. Поэтому сегодняшний приём и будильник ставятся здесь же; сценарий, которому
     * нужно обратное («человек не открывал приложение»), снимает их явно.
     */
    @Допустим(
        "^у профиля есть лекарство «([^»]+)» с приёмом в (\\d{2}:\\d{2}) ежедневно, " +
            "доза (\\S+) таблетк\\S+, дозировка (\\S+) (\\S+)$",
    )
    @Suppress("LongParameterList")
    fun theProfileHasADailyMedication(name: String, time: String, dose: String, strength: String, unit: String) {
        medication = name
        world.medications.add(
            profile = world.profiles,
            profileName = world.profiles.active,
            name = name,
            times = listOf(LocalTime.parse(time)),
        )
        world.medications.update(name) {
            it.copy(
                // Имя из сценария становится именем лекарства: экран напоминания показывает
                // его человеку, и проверка «в журнале нет названий» сверяется с ним.
                name = name,
                doseAmount = RussianDates.amount(dose),
                strength = RussianDates.amount(strength),
                strengthUnit = if (unit == MILLIGRAM_WORDS) MILLIGRAM_CODE else unit,
                stockLeft = BigDecimal(STOCK),
            )
        }
        ensureIntake(name, world.clock.today(), time)
        reschedule()
    }

    @Допустим("^курс «([^»]+)» идёт с (.+?) без конечной даты$")
    fun theCourseRunsSince(name: String, date: String) {
        medication = name
        world.medications.update(name) { it.copy(startDate = world.dateOf(date), endDate = null) }
    }

    @Допустим("^разрешение на уведомления выдано$")
    fun notificationsAreGranted() {
        world.system.grant(ReminderPermission.NOTIFICATIONS)
    }

    @Допустим("^разрешение на точные будильники выдано$")
    fun exactAlarmsAreGranted() {
        world.system.grant(ReminderPermission.EXACT_ALARMS)
    }

    @Допустим("^право на точные будильники выдано$")
    fun exactAlarmsRightIsGranted() {
        world.system.grant(ReminderPermission.EXACT_ALARMS)
    }

    @Допустим("^настройка «Отложить на» — (\\d+) минут$")
    fun theSnoozeSettingIs(minutes: Int) {
        runBlocking { world.reminders.settingsStore.setSnoozeMinutes(minutes) }
    }

    @Допустим("^настройка «Настойчивое напоминание» (включена|выключена)$")
    fun thePersistentSettingIs(state: String) {
        runBlocking { world.reminders.settingsStore.setPersistentEnabled(state == PERSISTENT_ON) }
    }

    @Допустим("^у профиля «([^»]+)» есть лекарство «([^»]+)» без расписания, не чаще (\\d+) раз в сутки$")
    fun theProfileHasAnOnDemandMedication(profile: String, name: String, limit: Int) {
        medication = name
        world.medications.add(
            profile = world.profiles,
            profileName = profile,
            name = name,
            mode = ScheduleMode.PRN,
            maxPerDay = limit,
        )
    }

    // --- Планирование --------------------------------------------------------------------------

    @Допустим("^приёмы «([^»]+)» запланированы на (.+?), каждый в (\\d{2}:\\d{2})$")
    fun theIntakesArePlannedForThoseDates(name: String, dates: String, time: String) {
        medication = name
        RussianDates.dates(dates, world.clock.today()).forEach { day -> ensureIntake(name, day, time) }
    }

    /**
     * Пересборка окна — тем же полным сценарием домена, что в продукте.
     *
     * «Ещё раз» в строке сценария — часть текста, а не другой шаг: окно обязано быть
     * идемпотентным, и сценарий проверяет ровно повторный вызов.
     */
    @Когда("^приложение пересобирает план напоминаний (?:ещё раз )?(.+?) в (\\d{2}:\\d{2})$")
    fun theApplicationReschedulesThePlan(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        reschedule()
    }

    @Допустим("^приложение пересобрало план напоминаний (.+?) в (\\d{2}:\\d{2})$")
    fun theApplicationRescheduledThePlan(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        reschedule()
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}) приложение пересобирает план напоминаний$")
    fun theApplicationReschedulesThePlanAt(date: String, time: String) {
        world.clock.moveTo(world.localMoment(date, time))
        reschedule()
    }

    @Тогда("^напоминания поставлены на (.+?), (\\d{2}:\\d{2}) и на (.+?), (\\d{2}:\\d{2})$")
    fun theRemindersAreScheduled(first: String, firstTime: String, second: String, secondTime: String) {
        assertNotNull(alarmOf(medication, world.dateOf(first), firstTime), "the reminder of $first must be armed")
        assertNotNull(alarmOf(medication, world.dateOf(second), secondTime), "the reminder of $second must be armed")
    }

    @И("^напоминания поставлены на (\\d+) и (\\d+) (\\S+) (\\d{4})$")
    @Suppress("LongParameterList")
    fun theRemindersAreScheduledOnThoseDates(firstDay: Int, secondDay: Int, month: String, year: Int) {
        val second = LocalDate.of(year, monthNumber(month), secondDay)
        val first = LocalDate.of(year, monthNumber(month), firstDay)
        listOf(first, second).forEach { day -> ensureIntake(medication, day, DAILY_TIME) }
        reschedule()
    }

    @Но("^напоминания на (\\d+) и (\\d+) (\\S+) (\\d{4}) не поставлены$")
    @Suppress("LongParameterList")
    fun theRemindersOfThoseDatesAreNotScheduled(firstDay: Int, secondDay: Int, month: String, year: Int) {
        val monthValue = monthNumber(month)
        listOf(firstDay, secondDay).forEach { day ->
            val date = LocalDate.of(year, monthValue, day)
            val armed = world.intakes.of(medication)
                .filter { it.scheduledAt.toLocalDate() == date }
                .mapNotNull { world.reminders.alarmOf(it.id) }
            assertEquals(emptyList<Instant>(), armed, "no reminder of $date may be armed")
        }
    }

    @Тогда("^ни одного напоминания о «([^»]+)» не запланировано$")
    fun noReminderOfThatMedicationIsScheduled(name: String) {
        val id = world.medications.get(name).id
        val armed = world.reminders.scheduled().keys
            .mapNotNull { world.intakes.get(it) }
            .filter { it.medicationId == id }
        assertTrue(armed.isEmpty(), "the on-demand medication must not remind, but $armed are armed")
    }

    /**
     * Напоминание поставлено — данность Предыстории либо следствие действия.
     *
     * Одна и та же строка сценария стоит и до действия («дано»), и после него
     * («напоминание перенесено»). До действия она приём заводит и окно пересобирает,
     * после — только проверяет: шаг, который сам ставит то, что проверяет, ничего
     * не проверяет.
     */
    @Допустим("^напоминание о «([^»]+)» на (.+?), (\\d{2}:\\d{2}) запланировано$")
    fun theReminderOfThatMedicationIsScheduled(name: String, date: String, time: String) {
        medication = name
        val day = world.dateOf(date)
        if (!world.acted) {
            ensureIntake(name, day, time)
            reschedule()
        }
        assertNotNull(alarmOf(name, day, time), "the reminder of $name at $date $time must be armed")
    }

    // --- Архивация, возврат и правка расписания ------------------------------------------------

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек отправляет «([^»]+)» в архив$")
    fun thePersonArchivesTheMedication(date: String, time: String, name: String) {
        medication = name
        world.rememberState()
        world.clock.moveTo(world.localMoment(date, time))
        val id = world.medications.get(name).id
        val outcome = runBlocking {
            ArchiveMedicationUseCase(world.repositories.medicationStore, world.planner())(id, ArchiveReason.MANUAL)
        }
        assertTrue(outcome is ArchiveOutcome.Archived, "the medication must be archived: $outcome")
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек возвращает «([^»]+)» из архива$")
    fun thePersonRestoresTheMedication(date: String, time: String, name: String) {
        medication = name
        world.rememberState()
        world.clock.moveTo(world.localMoment(date, time))
        restore(name)
    }

    @Тогда("^напоминание о «([^»]+)» на (.+?), (\\d{2}:\\d{2}) больше не запланировано$")
    fun theReminderIsNoLongerScheduled(name: String, date: String, time: String) {
        val moment = world.localMoment(date, time)
        val intake = world.intakes.of(name).firstOrNull { it.scheduledAt == moment }
        if (intake != null) {
            assertEquals(null, world.reminders.alarmOf(intake.id), "the archived medication must not remind")
        }
    }

    @Тогда("^напоминание о «([^»]+)» на (.+?), (\\d{2}:\\d{2}) снова запланировано$")
    fun theReminderIsScheduledAgain(name: String, date: String, time: String) {
        val moment = world.localMoment(date, time)
        val intake = requireNotNull(
            world.intakes.of(name).firstOrNull { it.scheduledAt == moment && it.isPending },
        ) { "the intake of $name at $date $time must be planned again after the restore" }
        assertNotNull(world.reminders.alarmOf(intake.id), "the restored medication must remind again")
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек меняет время приёма «([^»]+)» с (\\d{2}:\\d{2}) на (\\d{2}:\\d{2})$")
    @Suppress("LongParameterList")
    fun thePersonChangesTheTimeOf(date: String, time: String, name: String, from: String, to: String) {
        medication = name
        world.rememberState()
        world.clock.moveTo(world.localMoment(date, time))
        val device = world.medications.get(name)
        assertEquals(listOf(LocalTime.parse(from)), device.times, "the premise names the current time")
        val result = world.save(MedicationDraft.of(device).copy(times = listOf(LocalTime.parse(to))))
        assertTrue(result is SaveResult.Saved, "the edit must be saved: $result")
    }

    @И("^в (\\d{2}:\\d{2}) напоминание не приходит$")
    fun noReminderComesAt(time: String) {
        val before = cardsOf(medication).size
        advanceTo(LocalTime.parse(time))
        assertEquals(before, cardsOf(medication).size, "no reminder of $medication may arrive")
    }

    /** Напоминание пришло в свой час: карточка о приёме предъявлена человеку. */
    @И("^в (\\d{2}:\\d{2}) напоминание приходит$")
    fun theReminderComesAt(time: String) {
        val before = cardsOf(medication).size
        advanceTo(LocalTime.parse(time))
        assertEquals(before + 1, cardsOf(medication).size, "the reminder of $medication must arrive")
    }

    @И("^данные о лекарствах и расписании за это время не менялись$")
    fun theDataDidNotChange() = Unit

    // --- После срабатывания и ночная пересборка ------------------------------------------------

    @Тогда("^на (.+?), (\\d{2}:\\d{2}) запланировано ровно одно напоминание о «([^»]+)»$")
    fun exactlyOneReminderIsScheduledFor(date: String, time: String, medicationName: String) {
        val id = world.medications.get(medicationName).id
        val moment = world.localMoment(date, time)
        val armed = world.reminders.scheduled().keys
            .mapNotNull { world.intakes.get(it) }
            .count { it.medicationId == id && it.scheduledAt == moment }
        assertEquals(1, armed, "exactly one reminder of $medicationName at $date $time")
    }

    @И("^в (\\d{2}:\\d{2}) приходит ровно одно напоминание$")
    fun exactlyOneReminderComesAt(time: String) {
        advanceTo(LocalTime.parse(time))
        assertEquals(1, cardsOf(medication).size, "exactly one reminder of $medication must arrive")
    }

    /**
     * Срабатывание будильника — как у системы: показ, затем пересборка окна.
     *
     * Пересборку после показа ставит приёмник (`AlarmReceiver`, НАП-11): план пополняется
     * приёмом, который ещё не попал в окно. Сценарий проверяет именно это следствие.
     */
    @Когда("^(.+?) в (\\d{2}:\\d{2}) напоминание приходит$")
    fun theReminderComesAtThatMoment(date: String, time: String) {
        val at = world.localMoment(date, time)
        world.clock.moveTo(at)
        world.reminders.deliverThrough(at)
        world.reminders.reschedule(RescheduleReason.ALARM_FIRED)
    }

    @Тогда("^напоминание о приёме (.+?), (\\d{2}:\\d{2}) становится запланированным$")
    fun theReminderOfThatIntakeBecomesScheduled(date: String, time: String) {
        val moment = world.localMoment(date, time)
        val intake = requireNotNull(
            world.intakes.of(medication).firstOrNull { it.scheduledAt == moment && it.isPending },
        ) { "the intake of $medication at $date $time must exist and still be pending" }
        assertNotNull(world.reminders.alarmOf(intake.id), "the window must be replenished after the firing")
    }

    @И("^список запланированных напоминаний не остаётся пустым$")
    fun theScheduleDoesNotBecomeEmpty() {
        assertFalse(world.reminders.scheduled().isEmpty(), "the schedule must not run empty")
    }

    /**
     * Человек не открывал приложение: будильников нет, срабатывать нечему.
     *
     * Приёмы на названный день и следующий уже в плане — их породил планировщик, пока
     * приложение работало; пересборка окна, которая поставила бы будильники, не запускалась.
     */
    @Допустим("^(.+?) человек не открывал приложение$")
    fun thePersonDidNotOpenTheApp(date: String) {
        val day = world.dateOf(date)
        ensureIntake(medication, day, DAILY_TIME)
        ensureIntake(medication, day.plusDays(1), DAILY_TIME)
        world.reminders.scheduled().keys.forEach { intakeId -> runBlocking { world.reminders.alarms.cancel(intakeId) } }
        assertEquals(emptyMap<String, Instant>(), world.reminders.scheduled(), "the app did not run, nothing is armed")
    }

    @И("^за (.+?) не сработало ни одного напоминания$")
    fun noReminderFiredOn(date: String) {
        assertTrue(world.reminders.shownCards.isEmpty(), "nothing may have fired on $date")
    }

    @Когда("^наступает ночь с (.+?) на (.+?)$")
    fun theNightComes(from: String, to: String) {
        val night = world.dateOf(to)
        require(world.dateOf(from).plusDays(1) == night) { "the scenario must name an adjacent night" }
        world.clock.moveTo(night.atTime(NIGHT_HOUR, 0))
        // Ночной якорный будильник пересобирает план, когда не случилось ничего другого (НАП-8).
        world.reminders.reschedule(RescheduleReason.ANCHOR)
    }

    @Тогда("^план напоминаний пересобирается сам$")
    fun thePlanIsRebuiltByItself() {
        val last = requireNotNull(runBlocking { world.reminders.journal.last(ReminderEvent.RESCHEDULED) })
        assertEquals(RescheduleReason.ANCHOR.code, last.reason, "the night anchor must be the reason")
        assertFalse(world.reminders.scheduled().isEmpty(), "the night rebuild must arm the window")
    }

    @И("^напоминание о приёме (.+?), (\\d{2}:\\d{2}) приходит вовремя$")
    fun theReminderComesOnTime(date: String, time: String) {
        val id = world.medications.get(medication).id
        val at = world.localMoment(date, time)
        world.clock.moveTo(at)
        world.reminders.deliverThrough(at)
        val card = requireNotNull(
            world.reminders.shownCards.lastOrNull { it.items.any { item -> item.medicationId == id } },
        ) { "the reminder must arrive" }
        val planned = card.scheduledAt.atZone(world.clock.zone).toInstant()
        val deviation = Duration.between(planned, world.clock.now()).seconds
        assertTrue(deviation in 0..ON_TIME_SECONDS, "the reminder must arrive on time, deviation was $deviation s")
    }

    // --- Настойчивое напоминание ---------------------------------------------------------------

    @Допустим("^напоминание о приёме (.+?), (\\d{2}:\\d{2}) пришло и человек ничего не нажал$")
    fun theReminderCameAndNothingWasPressed(date: String, time: String) {
        deliverReminder(date, time, pressNothing = true)
    }

    @И("^напоминание о приёме (.+?), (\\d{2}:\\d{2}) пришло$")
    fun theReminderCame(date: String, time: String) {
        deliverReminder(date, time)
    }

    @Тогда("^напоминание о приёме приходит повторно$")
    fun theReminderRepeats() {
        assertTrue(
            world.reminders.shownWithChannels().any { it.second == ReminderChannel.PERSISTENT },
            "a persistent repeat must arrive",
        )
        assertNotNull(
            runBlocking { world.reminders.journal.last(ReminderEvent.REPEATED) },
            "the repeat must leave a record",
        )
    }

    @И("^повторное напоминание звучит и вибрирует заметнее первого$")
    fun theRepeatIsLouderThanTheFirst() {
        val channels = world.reminders.shownWithChannels().map { it.second }
        assertEquals(ReminderChannel.REGULAR, channels.first(), "the first reminder is regular")
        assertEquals(ReminderChannel.PERSISTENT, channels.last(), "the repeat is persistent")
    }

    @Тогда("^повторного напоминания не приходит$")
    fun noRepeatComes() {
        assertFalse(
            world.reminders.shownWithChannels().any { it.second == ReminderChannel.PERSISTENT },
            "no persistent repeat may arrive",
        )
    }

    @Тогда("^первое напоминание о приёме приходит обычным сигналом$")
    fun theFirstReminderIsRegular() {
        assertEquals(
            ReminderChannel.REGULAR,
            world.reminders.shownWithChannels().last().second,
            "persistence belongs to the repeat, not to the first reminder",
        )
    }

    @Тогда("^в (.+?), (\\d{2}:\\d{2}) повторного напоминания не приходит$")
    fun noRepeatComesAt(date: String, time: String) {
        val at = world.localMoment(date, time)
        world.clock.moveTo(at)
        world.reminders.deliverThrough(at)
        noRepeatComes()
    }

    @Допустим("^человек не отмечает приём (.+?), (\\d{2}:\\d{2}) и не трогает напоминания$")
    fun thePersonLeavesTheIntakeUnmarked(date: String, time: String) {
        deliverReminder(date, time, pressNothing = true)
    }

    @Когда("^проходит время до (.+?), (\\d{2}:\\d{2})$")
    fun theTimePassesUntil(date: String, time: String) {
        val at = world.localMoment(date, time)
        world.clock.moveTo(at)
        world.reminders.deliverThrough(at)
    }

    @Тогда("^повторных напоминаний о приёме приходит не больше двух$")
    fun atMostTwoRepeatsArrive() {
        val repeats = world.reminders.shownWithChannels().count { it.second == ReminderChannel.PERSISTENT }
        assertTrue(
            repeats <= PersistentRepeatPolicy.MAX_REPEATS,
            "repeats must be capped at ${PersistentRepeatPolicy.MAX_REPEATS}, got $repeats",
        )
    }

    @И("^после исчерпания повторов напоминание больше не показывается$")
    fun afterTheLimitNothingIsShown() {
        val repeats = world.reminders.shownWithChannels().count { it.second == ReminderChannel.PERSISTENT }
        assertEquals(PersistentRepeatPolicy.MAX_REPEATS, repeats, "the limit must be exhausted")
        val cards = world.reminders.shownCards.size
        world.reminders.deliverDue()
        assertEquals(cards, world.reminders.shownCards.size, "nothing may be shown after the limit")
        assertEquals(IntakeStatus.PENDING, currentIntake().status)
    }

    // --- Переход в пропущенные -----------------------------------------------------------------

    @Допустим("^напоминание о приёме (.+?), (\\d{2}:\\d{2}) пришло и осталось в шторке$")
    fun theReminderCameAndStayedInTheShade(date: String, time: String) {
        val intake = deliverReminder(date, time, pressNothing = true)
        assertTrue(world.reminders.isNotificationShown(intake.id), "the card must stay in the shade")
    }

    @Тогда("^приём считается пропущенным$")
    fun theIntakeIsMissed() {
        assertEquals(IntakeStatus.MISSED, currentIntake().status)
    }

    @И("^уведомление о приёме снимается из шторки$")
    fun theNotificationIsRemoved() {
        assertFalse(world.reminders.isNotificationShown(currentIntake().id), "the card must leave the shade")
    }

    @Но("^нового уведомления при этом не появляется$")
    fun noNewNotificationAppears() {
        assertEquals(1, world.reminders.shownCards.size, "the midnight sweep must not show anything")
    }

    @Допустим("^приём «([^»]+)» (\\d+) (\\S+) (\\d{4}), (\\d{2}:\\d{2}) стал пропущенным (.+?) в (\\d{2}:\\d{2})$")
    @Suppress("LongParameterList")
    fun theIntakeBecameMissed(
        name: String,
        day: Int,
        month: String,
        year: Int,
        time: String,
        sweepDate: String,
        sweepTime: String,
    ) {
        medication = name
        val intake = ensureIntake(name, LocalDate.of(year, monthNumber(month), day), time)
        world.clock.moveTo(world.localMoment(sweepDate, sweepTime))
        world.reminders.dayBoundarySweep()
        assertEquals(IntakeStatus.MISSED, world.intakes.get(intake.id)?.status, "the midnight sweep must miss it")
        world.intakes.focusOn(intake)
    }

    @Тогда("^напоминания о приёме (.+?), (\\d{2}:\\d{2}) не запланировано$")
    fun noReminderOfThatIntakeIsScheduled(date: String, time: String) {
        val moment = world.localMoment(date, time)
        val intake = world.intakes.of(medication).firstOrNull { it.scheduledAt == moment }
        if (intake != null) {
            assertEquals(null, world.reminders.alarmOf(intake.id), "a missed intake must not be armed")
        }
    }

    // --- Деградация без разрешений и отметка с часов -------------------------------------------

    @Допустим("^разрешение на уведомления отозвано$")
    fun notificationsAreRevoked() {
        world.system.deny(ReminderPermission.NOTIFICATIONS)
    }

    @И("^право на точные будильники отозвано$")
    fun exactAlarmsAreRevoked() {
        world.system.deny(ReminderPermission.EXACT_ALARMS)
    }

    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек отмечает приём «([^»]+)» принятым вручную$")
    fun thePersonMarksTheIntakeManually(date: String, time: String, name: String) {
        medication = name
        world.rememberState()
        val day = world.dateOf(date)
        // Отметка относится к запланированному приёму, а не к моменту нажатия: время
        // в строке — когда человек отметил, а не когда приём назначен (НАП-31).
        val intake = requireNotNull(
            world.intakes.of(name).firstOrNull { it.scheduledAt.toLocalDate() == day && it.isPending },
        ) { "the scenario must have a pending intake of $name on $day" }
        val moment = world.localMoment(date, time)
        world.clock.moveTo(moment)
        stockBefore = world.medications.byId(intake.medicationId)?.stockLeft
        world.mark(intake.id, MarkResult.TAKEN, IntakeSource.PHONE, world.clock.momentOf(moment))
        world.intakes.focusOn(intake)
    }

    @И("^остаток упаковки списывается$")
    fun theStockIsDecremented() {
        val before = requireNotNull(stockBefore) { "the scenario must know the stock before the mark" }
        val intake = currentIntake()
        val dose = requireNotNull(world.medications.byId(intake.medicationId)).doseAmount
        val after = requireNotNull(world.medications.byId(intake.medicationId)?.stockLeft)
        assertEquals(0, before.subtract(dose).compareTo(after), "the stock must be decremented by the dose")
    }

    @Допустим("^напоминание о приёме (.+?), (\\d{2}:\\d{2}) показано на телефоне$")
    fun theReminderIsShownOnThePhone(date: String, time: String) {
        val intake = deliverReminder(date, time)
        assertTrue(world.reminders.isNotificationShown(intake.id), "the card must be in the shade")
    }

    @Когда("^в (\\d{2}:\\d{2}) человек отмечает этот приём принятым на часах$")
    fun thePersonMarksTheIntakeOnTheWatch(time: String) {
        world.rememberState()
        val intake = currentIntake()
        val at = world.clock.today().atTime(LocalTime.parse(time))
        world.markWithSideEffects(intake.id, MarkResult.TAKEN, IntakeSource.WATCH, world.clock.momentOf(at))
    }

    @Тогда("^напоминание на телефоне закрывается само$")
    fun thePhoneReminderClosesItself() {
        assertFalse(world.reminders.isNotificationShown(currentIntake().id), "the mark must dismiss the card")
    }

    // --- Диагностика, настройки системы и приватность ------------------------------------------

    @Тогда("^плановым временем приёма остаётся (.+?), (\\d{2}:\\d{2})$")
    fun theScheduledMomentStays(date: String, time: String) {
        assertEquals(world.localMoment(date, time), currentIntake().scheduledAt, "the planned time is never rewritten")
    }

    @Допустим("^право на точные будильники не выдано$")
    fun exactAlarmsAreNotGranted() {
        world.system.deny(ReminderPermission.EXACT_ALARMS)
    }

    @Допустим("^доступ к политике «Не беспокоить» не выдан$")
    fun dndPolicyAccessIsNotGranted() {
        world.system.deny(ReminderPermission.DND_POLICY_ACCESS)
    }

    /**
     * Правка расписания — сохранение формы, а не запись в модель.
     *
     * Время сдвигается на час: сценарий называет повод («изменил расписание»), а не новое
     * значение, и второй раз он его не проверяет.
     */
    @Когда("^(.+?) в (\\d{2}:\\d{2}) человек изменил расписание «([^»]+)»$")
    fun thePersonChangedTheSchedule(date: String, time: String, name: String) {
        medication = name
        world.rememberState()
        world.clock.moveTo(world.localMoment(date, time))
        val device = world.medications.get(name)
        val result = world.save(
            MedicationDraft.of(device).copy(times = device.times.map { it.plusHours(HOURS) }),
        )
        assertTrue(result is SaveResult.Saved, "the schedule edit must be saved: $result")
    }

    /**
     * Журнал не покидает устройство — утверждение о сборке, а не о настроении.
     *
     * Сеть в продукте есть, но только аналитическим контуром (ПЛ-3, НФТ-15, ПМ-19,
     * SKL-82): `INTERNET` объявлен единственным сетевым разрешением, до согласия SDK
     * не активируется, а журнал наружу не уходит ни при каких условиях (НФТ-19).
     * Та же проверка, что у настроек (`SettingsContextSteps.theAppNeverReachesTheNetwork`)
     * и истории: запрещены только разрешения состояния сети. Копия здесь потому,
     * что строка сценария другая, а правило сборки одно.
     */
    @Тогда("^ни одна запись журнала не передаётся за пределы устройства$")
    fun theJournalNeverLeavesTheDevice() {
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
    }

    @Но("^повторного списания остатка не происходит$")
    fun noSecondStockDecrement() {
        val intake = currentIntake()
        val applied = world.intakes.outcomes.count { it is MarkOutcome.Applied && it.intake.id == intake.id }
        assertEquals(1, applied, "the intake must be written exactly once")
        val device = requireNotNull(world.medications.byId(intake.medicationId))
        val expected = BigDecimal(STOCK).subtract(device.doseAmount)
        assertEquals(0, expected.compareTo(requireNotNull(device.stockLeft)), "the stock must be decremented once")
    }

    // --- Обвязка -------------------------------------------------------------------------------

    /** Показ напоминания в его время: приём, будильник и доставка боевым путём. */
    private fun deliverReminder(date: String, time: String, pressNothing: Boolean = false): Intake {
        val day = world.dateOf(date)
        val intake = ensureIntake(medication, day, time)
        reschedule()
        val at = day.atTime(LocalTime.parse(time))
        world.clock.moveTo(at)
        world.reminders.deliverThrough(at)
        if (pressNothing) {
            assertTrue(world.reminders.shownCards.isNotEmpty(), "the reminder must actually show")
            assertEquals(IntakeStatus.PENDING, world.intakes.get(intake.id)?.status, "nothing may mark the intake")
        }
        return intake
    }

    private fun reschedule() {
        world.reminders.reschedule(RescheduleReason.MANUAL)
    }

    private fun advanceTo(time: LocalTime) {
        val at = world.clock.today().atTime(time)
        world.clock.moveTo(at)
        world.reminders.deliverThrough(at)
    }

    private fun ensureIntake(name: String, day: LocalDate, time: String): Intake {
        medication = name
        val intake = world.intakes.pending(name, day.atTime(LocalTime.parse(time)))
        world.intakes.focusOn(intake)
        return intake
    }

    private fun restore(name: String) {
        val id = world.medications.get(name).id
        val useCase = RestoreMedicationUseCase(
            medications = world.repositories.medicationStore,
            planner = world.planner(),
            transactor = ScenarioTransactor(world.intakes, world.medications),
            time = world.clock.time,
            log = world.log,
        )
        val outcome = runBlocking { useCase(id) }
        assertTrue(outcome is RestoreOutcome.Restored, "the medication must be restored: $outcome")
    }

    private fun alarmOf(name: String, date: LocalDate, time: String): Instant? {
        val moment = date.atTime(LocalTime.parse(time))
        val intake = world.intakes.of(name).firstOrNull { it.scheduledAt == moment } ?: return null
        return world.reminders.alarmOf(intake.id)
    }

    private fun cardsOf(name: String): List<ReminderCard> {
        val id = world.medications.get(name).id
        return world.reminders.shownCards.filter { card -> card.items.any { it.medicationId == id } }
    }

    private fun currentIntake(): Intake = world.intakes.current()

    private fun monthNumber(month: String): Int =
        RussianDates.month("$month ${world.clock.today().year}", world.clock.today()).monthValue

    private companion object {
        const val DEFAULT_MEDICATION = "Магний B6"
        const val MILLIGRAM_WORDS = "мг"
        const val MILLIGRAM_CODE = "mg"
        const val DAILY_TIME = "14:00"
        const val NIGHT_HOUR = 3
        const val HOURS = 1L
        const val ON_TIME_SECONDS = 120L
        const val PERSISTENT_ON = "включена"
        const val STOCK = 30

        const val MANIFEST = "app/src/main/AndroidManifest.xml"
        const val USES_PERMISSION = "<uses-permission[^>]*>"
        const val REMOVE_DIRECTIVE = "tools:node=\"remove\""
        const val ANALYTICS_PERMISSION = "android.permission.INTERNET"

        val FORBIDDEN_NETWORK_PERMISSIONS = listOf(
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
        )
    }
}
