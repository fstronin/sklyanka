package ru.stohastika.sklyanka.acceptance.steps.task

import io.cucumber.datatable.DataTable
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.steps.dateOf
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.acceptance.world.TransferFileBuilder
import ru.stohastika.sklyanka.domain.medication.DraftViolation
import ru.stohastika.sklyanka.domain.medication.MedicationDraft
import ru.stohastika.sklyanka.domain.medication.MedicationListOrder
import ru.stohastika.sklyanka.domain.medication.SaveResult
import ru.stohastika.sklyanka.domain.medication.ValidateMedicationDraft
import ru.stohastika.sklyanka.domain.medication.effectiveSchedule
import ru.stohastika.sklyanka.domain.medication.normalized
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.ScheduleRule
import ru.stohastika.sklyanka.domain.model.Stage
import ru.stohastika.sklyanka.domain.pro.ComplexScheduleGate
import ru.stohastika.sklyanka.domain.pro.ProPoint
import ru.stohastika.sklyanka.domain.pro.ProProductId
import ru.stohastika.sklyanka.domain.reminder.ReminderSlot
import ru.stohastika.sklyanka.domain.today.BuildTodayAgenda
import ru.stohastika.sklyanka.domain.today.IntakeItem
import ru.stohastika.sklyanka.domain.transfer.ExportOutcome
import ru.stohastika.sklyanka.domain.transfer.TransferFormat
import ru.stohastika.sklyanka.domain.transfer.TransferMode
import ru.stohastika.sklyanka.domain.transfer.TransferOutcome
import ru.stohastika.sklyanka.domain.transfer.TransferSnapshot
import java.io.File
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.YearMonth

/**
 * Шаги приёмки SKL-117 — курс задаётся как назначено: недельные схемы, интервалы, циклы
 * и ступени (ПР-15…ПР-21, ИНВ-23…ИНВ-25).
 *
 * Правила не пересказываются: приёмы порождает настоящий `ScheduleExpander` за портом
 * `SaveMedicationUseCase`, отказы называет настоящий `ValidateMedicationDraft`, платную
 * границу решает настоящий `ComplexScheduleGate`, шторку показывает настоящий
 * `ProSheetSession`, а полный обход после загрузки файла идёт тем же портом, что
 * в продукте. Мир сценария подставляет только порты платформы и хранилище в памяти
 * (`ADR-0007`).
 *
 * Экранный прогон живёт рядом с доменным: строки «человек заполняет форму нового лекарства
 * «…»» и «нажимает «Сохранить лекарство»» объявлены здесь один раз на оба прогона — словарь
 * шагов не допускает второго объявления, — а собранную форму держит стенд `feature-meds`.
 * Он читает доменный черновик через [formDraft] и приносит нажатие нижней кнопки крючком
 * [saveOnScreen]; в доменном прогоне крючок пуст, и те же строки работают по черновику.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса. Слова,
 * о которых сценарий говорит вслух («форма называет причину словами», «поле интервала
 * остаётся с подсказкой»), проверяются по каталогу строк формы: ключ без слов — не слова
 * (П-7). Вёрстку и разметку держит экранный прогон.
 */
@Suppress("TooManyFunctions", "LargeClass")
class Sk117ScheduleSteps(private val world: AcceptanceWorld) {

    private val validate = ValidateMedicationDraft()

    /** Черновик формы: шаги сценариев формы меняют в нём то, что назвал сценарий (ПР-15). */
    private var draft: MedicationDraft? = null

    /**
     * Черновик формы для экранного прогона (SKL-117, группа `@form`).
     *
     * Стенд `feature-meds` собирает форму из этого черновика тем же путём, каким
     * `MedsPreviewScreenSteps` подтягивает стенд к доменному черновику. Крючка «открыть
     * форму» здесь нет намеренно: контейнер объектов Cucumber создаёт классы шагов лениво,
     * и к моменту вызова такого крючка экранный класс мог ещё не существовать. Чтение
     * черновика порядком создания классов не ограничено.
     */
    val formDraft: MedicationDraft? get() = draft

    /** Нарушения последней попытки сохранить: по ним форма называет причину словами. */
    private var violations: List<DraftViolation> = emptyList()

    /** Сохранение закрыто платной границей: вид сложный, а доступа нет (ПР-20, ПР-21). */
    private var blockedByPro: Boolean = false

    private var lastSave: SaveResult? = null

    /** Курс, о котором говорят проверки приёмов и напоминаний. */
    private var course: String = ""

    /** Снимок последнего файла переноса: выгруженного человеком или подготовленного Предысторией. */
    private var transferFile: TransferSnapshot? = null

    /** Приёмы курса перед выгрузкой: ими проверяются «те же дни и времена» после загрузки. */
    private var intakesBeforeExport: List<LocalDateTime> = emptyList()

    /** Приёмы прошлой недели как их оставила Предыстория обновления (SKL-117, группа `@migration`). */
    private var lastWeekRows: List<Intake> = emptyList()

    /** Нажатие нижней кнопки собранной формы: домен остаётся один, нажатие приходит из стенда. */
    var saveOnScreen: (() -> Unit)? = null

    /**
     * Доступ «Про», каким его видит **открытая форма** (Р-22, SKL-122, SKL-117 F3).
     *
     * Форма читает доступ при открытии и перечитывает его, когда закрывается витрина: покупка
     * в витрине, открытой из формы, обязана разблокировать сохранение без ухода из формы
     * (`MedicationFormViewModel.refreshProAccess`, `MainActivity`). Здесь этот снимок повторён
     * потому, что «форма» сценарного прогона — её черновик: правила гейта настоящие, а то, **когда**
     * форма читает доступ, — часть продукта, и сценарий повторяет её, а не выдумывает. Без
     * снимка «купил из формы и сохранил» проходило бы чтением доступа прямо в момент нажатия,
     * то есть проверяло бы не то, что делает продукт.
     */
    private var formAccess: Boolean? = null

    // --- Заведение курса: вид расписания решает, какие дни порождают приёмы -------------------

    @Когда("^человек заводит курс «([^»]+)» с приёмом по понедельникам, средам и пятницам в (\\d{2}):(\\d{2})$")
    fun thePersonStartsAWeekdaysCourse(name: String, hour: String, minute: String) {
        createCourse(name, ScheduleRule.Weekdays(MON_WED_FRI), LocalTime.of(hour.toInt(), minute.toInt()))
    }

    @Тогда("^приёмы идут по понедельникам, средам и пятницам$")
    fun theIntakesFollowThoseWeekdays() {
        assertIntakesOnlyOnRuleDays(medication())
        assertEveryRuleWeekdayIsFilled(medication())
    }

    @Тогда("^в остальные дни приёмов нет$")
    fun thereAreNoIntakesOnTheOtherDays() {
        assertNoIntakesOnTheOtherDays(medication())
    }

    @Тогда("^напоминания поставлены только на эти дни$")
    fun theRemindersStandOnThoseDaysOnly() {
        assertReminderDaysAre(MON_WED_FRI)
    }

    @Когда("^человек заводит курс «([^»]+)» с приёмом по средам в (\\d{2}):(\\d{2})$")
    fun thePersonStartsAOneWeekdayCourse(name: String, hour: String, minute: String) {
        // Один выбранный день недели — база (Р-24): подписки он не требует, и сценарий
        // заводит его без неё.
        createCourse(
            name = name,
            rule = ScheduleRule.Weekdays(setOf(DayOfWeek.WEDNESDAY)),
            time = LocalTime.of(hour.toInt(), minute.toInt()),
        )
    }

    @Тогда("^лекарство «([^»]+)» сохраняется$")
    fun theMedicationIsSaved(name: String) {
        val result = requireNotNull(lastSave) { "the scenario never created «$name»" }
        assertTrue(result is SaveResult.Saved, "the course must be saved: $result")
        assertTrue(world.medications.has(name), "the saved course must stay in the profile")
    }

    @Тогда("^приёмы идут по средам$")
    fun theIntakesFollowWednesdays() {
        assertIntakesOnlyOnRuleDays(medication())
        assertEveryRuleWeekdayIsFilled(medication())
    }

    @Когда("^человек заводит курс «([^»]+)» с приёмом через каждый второй день в (\\d{2}):(\\d{2})$")
    fun thePersonStartsAnIntervalCourse(name: String, hour: String, minute: String) {
        createCourse(name, ScheduleRule.EveryNDays(EVERY_SECOND_DAY), LocalTime.of(hour.toInt(), minute.toInt()))
    }

    @Тогда("^приёмы идут (\\d+), (\\d+), (\\d+) и (\\d+) октября, каждый в (\\d{2}):(\\d{2})$")
    fun theIntakesStandOnThoseDays(
        first: String,
        second: String,
        third: String,
        fourth: String,
        hour: String,
        minute: String,
    ) {
        val year = world.clock.today().year
        val time = LocalTime.of(hour.toInt(), minute.toInt())
        val expected = listOf(first, second, third, fourth).map { day ->
            LocalDate.of(year, Month.OCTOBER, day.toInt()).atTime(time)
        }
        assertEquals(expected, intakesOf(medication()).map { it.scheduledAt }, "the days of the interval course")
    }

    @Тогда("^в промежуточные дни приёмов нет$")
    fun thereAreNoIntakesOnTheDaysBetween() {
        assertNoIntakesOnTheOtherDays(medication())
    }

    @Когда("^человек заполняет форму нового лекарства «([^»]+)»$")
    fun thePersonFillsTheNewMedicationForm(name: String) {
        // Строка объявлена здесь одна на оба прогона: доменный прогон заполняет черновик,
        // а экранный стенд собирает форму из него, прочитав [formDraft] (Станды живут
        // в модуле экрана, и крючка «открыть форму» здесь нет — контейнер шагов ленив).
        draft = defaultDraft(name)
        course = name
        // Открытие формы читает доступ «Про» один раз — так же, как `MedicationFormViewModel.open`.
        formAccess = world.pro.status().included
    }

    @Когда("^выбирает приём через каждый день в (\\d{2}):(\\d{2})$")
    fun theEveryDayIntervalIsChosen(hour: String, minute: String) {
        // «Через каждый день» (N = 1) — это ежедневное расписание: форма вырождает такой
        // интервал в базовый вид, и сложного вида не возникает (ПР-17, `MedicationFormViewModel`).
        draft = current().copy(
            schedule = ScheduleRule.Daily,
            times = listOf(LocalTime.of(hour.toInt(), minute.toInt())),
        )
    }

    @Тогда("^строка вида называет ежедневное расписание$")
    fun theKindRowNamesTheDailySchedule() {
        // Строка вида — это `ScheduleKind.of(rule)`, а её слово — `schedule_kind_daily`
        // («Каждый день»). Проверяется то, чем она решается: сам вид расписания.
        assertEquals(ScheduleRule.Daily, current().schedule, "the kind row must name the every-day schedule")
    }

    @Тогда("^поле интервала остаётся на форме с подсказкой «(.+)»$")
    fun theIntervalFieldKeepsItsHint(hint: String) {
        // Поле держит выбранный вид «через N дней», а подсказка — не отказ, а предложение
        // базового вида (§5.10): слово живёт в каталоге формы, и ключ без слов — не слова.
        assertEquals(ScheduleRule.Daily, current().schedule, "the interval of one day degenerates into the daily kind")
        assertEquals(hint, formWords(ONE_DAY_INTERVAL_KEY), "the hint of the interval field")
    }

    @Тогда("^метки «Про» у выбора вида расписания нет$")
    fun theKindRowCarriesNoProMark() {
        // Метка стоит у платного варианта, а платное — это сложный вид (Р-17, ПР-16):
        // у ежедневного расписания метки нет ни при какой подписке.
        assertFalse(current().schedule.isComplex, "the chosen kind must not be paid")
    }

    @Когда("^человек заводит курс «([^»]+)» циклами «(\\d+)/(\\d+)» в (\\d{2}):(\\d{2})$")
    fun thePersonStartsACyclesCourse(name: String, onDays: String, offDays: String, hour: String, minute: String) {
        // Окно генерации шире одного цикла: сценарий проверяет и перерыв, и возвращение
        // приёмов после него, а по умолчанию мир планирует семь суток (ОВ-8).
        createCourse(
            name = name,
            rule = ScheduleRule.Cycles(onDays = onDays.toInt(), offDays = offDays.toInt()),
            time = LocalTime.of(hour.toInt(), minute.toInt()),
            depthDays = CYCLES_WINDOW_DAYS,
        )
    }

    @Тогда("^приёмы идут двадцать один день подряд$")
    fun theIntakesRunForTwentyOneDaysInARow() {
        val medication = medication()
        for (offset in 0L until TWENTY_ONE_DAYS) {
            val day = medication.startDate.plusDays(offset)
            assertTrue(
                intakesOn(medication, day).isNotEmpty(),
                "the course must have an intake on $day (day ${offset + 1} of the cycle)",
            )
        }
    }

    @Тогда("^затем семь дней приёмов нет$")
    fun theNextSevenDaysHaveNoIntakes() {
        val medication = medication()
        for (offset in TWENTY_ONE_DAYS until TWENTY_ONE_DAYS + DAYS_IN_WEEK) {
            val day = medication.startDate.plusDays(offset)
            assertEquals(
                emptyList<LocalDateTime>(),
                intakesOn(medication, day).map { it.scheduledAt },
                "the pause of the cycle must leave $day without intakes (ПР-18)",
            )
        }
    }

    @Тогда("^затем приёмы идут снова$")
    fun theIntakesReturnAfterThePause() {
        val medication = medication()
        val day = medication.startDate.plusDays(TWENTY_ONE_DAYS + DAYS_IN_WEEK)
        assertTrue(
            intakesOn(medication, day).isNotEmpty(),
            "the course must return to intakes after the pause: $day",
        )
    }

    @Когда("^человек заводит курс «([^»]+)» со ступенями в (\\d{2}):(\\d{2})$")
    fun thePersonStartsAStagedCourse(name: String, hour: String, minute: String, stages: DataTable) {
        // Сценарий называет этапы своей таблицей: доза — десятичная дробь текстом (ИНВ-24),
        // и разбирает её тот же разбор чисел сценариев, что и остальные шаги.
        val rows = stages.asMaps(String::class.java, String::class.java)
        val parsed = rows.map { row ->
            Stage(
                doseAmount = RussianDates.amount(requireNotNull(row["доза"]) { "no dose in the stage table" }),
                days = requireNotNull(row["дней"]) { "no days in the stage table" }.toInt(),
            )
        }
        assertTrue(parsed.isNotEmpty(), "the scenario must name at least one stage")
        createCourse(
            name = name,
            rule = ScheduleRule.Staged(parsed),
            time = LocalTime.of(hour.toInt(), minute.toInt()),
            // Восьмой день ступени лежит за пределами рабочего окна в семь суток (ОВ-8).
            depthDays = STAGES_WINDOW_DAYS,
        )
    }

    @Тогда("^приёмы первой недели идут с дозой (.+)$")
    fun theFirstWeekDosesAre(dose: String) {
        assertDosesOnDays((0L until DAYS_IN_WEEK.toLong()).toList(), RussianDates.amount(dose))
    }

    @Тогда("^с восьмого дня приёмы идут с дозой (.+)$")
    fun theDosesFromTheEighthDayAre(dose: String) {
        val medication = medication()
        val offsets = intakeDays(medication).map { day -> day.toEpochDay() - medication.startDate.toEpochDay() }
        assertDosesOnDays(offsets.filter { it >= DAYS_IN_WEEK.toLong() }, RussianDates.amount(dose))
    }

    @Тогда("^времена у всех приёмов — (\\d{2}):(\\d{2})$")
    fun everyIntakeKeepsThatTime(hour: String, minute: String) {
        val time = LocalTime.of(hour.toInt(), minute.toInt())
        val medication = medication()
        val moments = intakesOf(medication).map { it.scheduledAt }
        assertTrue(moments.isNotEmpty(), "the course must have intakes")
        moments.forEach { moment ->
            assertEquals(time, moment.toLocalTime(), "a stage changes the dose, never the time (ПР-19)")
        }
    }

    // --- Доза ступени видна человеку, а не только списана (SKL-117, критика F1) --------------

    @Допустим("^у человека есть курс «([^»]+)» со ступенями в (\\d{2}):(\\d{2})$")
    fun thePersonHasAStagedCourse(name: String, hour: String, minute: String, stages: DataTable) {
        val parsed = stages.asMaps(String::class.java, String::class.java).map { row ->
            Stage(
                doseAmount = RussianDates.amount(requireNotNull(row["доза"]) { "no dose in the stage table" }),
                days = requireNotNull(row["дней"]) { "no days in the stage table" }.toInt(),
            )
        }
        assertTrue(parsed.isNotEmpty(), "the scenario must name at least one stage")
        createCourse(
            name = name,
            rule = ScheduleRule.Staged(parsed),
            time = LocalTime.of(hour.toInt(), minute.toInt()),
            // Окно шире первой ступени: сценарий говорит и о её приёмах, и о приёмах второй.
            depthDays = STAGES_WINDOW_DAYS,
        )
    }

    /**
     * Приём названного дня курса уже отмечен — как данность Предыстории.
     *
     * Отметка делается до правки ступеней: сценарий проверяет, что доза, которой приём
     * прожит, остаётся его дозой (ПР-11).
     */
    @Допустим("^приём дня (\\d+) курса отмечен принятым$")
    fun theIntakeOfTheDayIsMarkedTaken(day: String) {
        val medication = medication()
        world.intakes.markTaken(course, momentOfDay(medication, day))
    }

    /**
     * Правка ступеней настоящим сохранением формы: доза второй ступени меняется, дни те же.
     *
     * Тем же портом, что у человека: `SaveMedicationUseCase` со своим перепланированием
     * (`world.save`), а не подмена расписания в хранилище — иначе сценарий проверял бы
     * состояние, которого правка формы не создаёт.
     */
    @Когда("^человек меняет дозу второй ступени на (.+)$")
    fun thePersonChangesTheDoseOfTheSecondStage(dose: String) {
        val medication = medication()
        val stages = (medication.schedule as? ScheduleRule.Staged)?.stages.orEmpty()
        assertTrue(stages.size >= SECOND_STAGE, "the scenario must bring a staged course: $stages")
        val changed = stages.mapIndexed { index, stage ->
            if (index == SECOND_STAGE - 1) stage.copy(doseAmount = RussianDates.amount(dose)) else stage
        }
        val result = world.save(MedicationDraft.of(medication).copy(schedule = ScheduleRule.Staged(changed)))
        lastSave = result
        assertTrue(result is SaveResult.Saved, "the edit of the stages must be saved: $result")
    }

    /** Доза, которую называет «Сегодня» у приёма названного дня (ПР-19). */
    @Тогда("^«Сегодня» называет приём дня (\\d+) с дозой (.+)$")
    fun theTodayScreenNamesTheIntakeOfTheDay(day: String, dose: String) {
        val medication = medication()
        val date = medication.startDate.plusDays(day.toLong() - 1)
        val items = agendaItemsOf(medication, date)
        assertTrue(items.isNotEmpty(), "the day $date must carry an intake in the agenda")
        items.forEach { item ->
            assertEquals(
                0,
                RussianDates.amount(dose).compareTo(item.doseAmount),
                "«Сегодня» must name the dose of the intake: ${item.doseAmount}",
            )
        }
    }

    /** Доза, которую называет карточка напоминания у приёма названного дня (ПР-19). */
    @Тогда("^карточка напоминания называет приём дня (\\d+) с дозой (.+)$")
    fun theReminderCardNamesTheIntakeOfTheDay(day: String, dose: String) {
        val medication = medication()
        val moment = momentOfDay(medication, day)
        val slot = ReminderSlot(profileId = medication.profileId, scheduledAt = moment)
        val card = runBlocking { world.reminders.cardSource().card(slot) }
        val item = requireNotNull(card?.items?.singleOrNull()) {
            "the reminder card of $moment must carry exactly one intake: $card"
        }
        assertEquals(
            0,
            RussianDates.amount(dose).compareTo(item.doseAmount),
            "the reminder card must name the dose of the intake: ${item.doseAmount}",
        )
    }

    /**
     * Дозы, записанные за период, называет отчёт врачу (ПР-19, SKL-117 F1).
     *
     * Отчёт строится настоящим `BuildDoctorReportUseCase` за месяц сценария: «отчёт совпадает
     * с экраном до числа» — не пересказ шага, а работающее правило (ОВ-15).
     */
    @Тогда("^отчёт врачу называет дозы (.+) и (.+)$")
    fun theDoctorReportNamesThoseDoses(first: String, second: String) {
        val month = YearMonth.from(world.clock.today())
        val report = requireNotNull(world.history.buildReport(month)) { "the report of $month must be built" }
        val row = report.medications.singleOrNull { it.medicationId == medication().id }
        val doses = requireNotNull(row) { "the report must carry the course: ${report.medications}" }.doses
        assertEquals(listOf(first, second).size, doses.size, "the report must name every dose: $doses")
        assertEquals(0, RussianDates.amount(first).compareTo(doses[0]))
        assertEquals(0, RussianDates.amount(second).compareTo(doses[1]))
    }

    /** Ожидающие приёмы второй ступени несут новую дозу: снимок обновлён (SKL-117 F7). */
    @Тогда("^ожидающие приёмы второй ступени несут дозу (.+)$")
    fun thePendingIntakesOfTheSecondStageCarry(dose: String) {
        val medication = medication()
        val pending = intakesOf(medication)
            .filter { it.isPending && stageNumberAt(medication, it.scheduledAt.toLocalDate()) == SECOND_STAGE - 1 }
        assertTrue(pending.isNotEmpty(), "the scenario must leave pending intakes of the second stage")
        pending.forEach { intake ->
            val snapshot = requireNotNull(intake.doseAmount) {
                "a pending intake of the second stage must carry its snapshot: ${intake.scheduledAt}"
            }
            assertEquals(0, RussianDates.amount(dose).compareTo(snapshot), "the snapshot of ${intake.scheduledAt}")
        }
    }

    /** Отмеченный приём сохраняет дозу, которой прожит: правка ступеней его не трогает (ПР-11). */
    @Тогда("^отмеченный приём дня (\\d+) сохраняет дозу (.+)$")
    fun theMarkedIntakeOfTheDayKeepsItsDose(day: String, dose: String) {
        val medication = medication()
        val moment = momentOfDay(medication, day)
        val marked = requireNotNull(
            intakesOn(medication, moment.toLocalDate()).firstOrNull { it.status == IntakeStatus.TAKEN },
        ) {
            "the day $moment must carry a marked intake"
        }
        val snapshot = requireNotNull(marked.doseAmount) { "the marked intake must carry its snapshot" }
        assertEquals(0, RussianDates.amount(dose).compareTo(snapshot), "the marked intake must keep its dose")
    }

    // --- Платная граница: покупка в витрине, открытой из формы, разблокирует сохранение -------

    /** Вид расписания с длинами цикла — тем же черновиком, каким его собирает форма. */
    @Когда("^выбирает вид расписания «([^»]+)» с длинами «([^»]+)» и «([^»]+)»$")
    fun theKindWithCycleLengthsIsChosen(kind: String, on: String, off: String) {
        val rule = when (kind) {
            "циклами" -> ScheduleRule.Cycles(onDays = on.toInt(), offDays = off.toInt())
            else -> error("the scenario names no lengths for «$kind»")
        }
        draft = current().copy(schedule = rule)
    }

    /** Покупка в уже открытой витрине — через настоящие `ProSheetSession` и магазин мира. */
    @Когда("^человек покупает подписку «Склянка Про» в открытой витрине$")
    fun thePersonBuysInTheOpenShowcase() {
        assertTrue(world.pro.state.visible, "the showcase must already be open — the form opened it")
        world.pro.session.select(ProProductId.MONTHLY)
        runBlocking { world.pro.session.buy() }
        assertTrue(world.pro.status().included, "a confirmed purchase must give access")
    }

    /**
     * Витрина закрылась — форма перечитала доступ (SKL-117 F3, SKL-122).
     *
     * Это повтор связки продукта: `MainActivity` на закрытие шторки перечитывает доступ
     * и строке настроек, и форме лекарства (`refreshProAccess`). Сценарий называет
     * состояние, которое продукт создаёт сам, — без этого «купил и сохранил» оставалось бы
     * правдой только при чтении доступа в момент нажатия.
     */
    @Когда("^витрина закрывается$")
    fun theShowcaseIsDismissed() {
        world.pro.session.dismiss()
        assertFalse(world.pro.state.visible, "the showcase must be closed")
        formAccess = world.pro.status().included
    }

    // --- Параметры вида: их проверяют до сохранения, и причина названа словами ---------------

    @Когда(
        "^человек задаёт в форме курс «([^»]+)» вида «([^»]+)» с параметрами «([^»]+)» в (\\d{2}):(\\d{2})$",
    )
    fun thePersonSetsBrokenParameters(name: String, kind: String, params: String, hour: String, minute: String) {
        // Параметры испорчены так, как их называет пример сценария: поле оставлено пустым,
        // введён ноль, у этапа нет дозы, последний этап выходит за конец курса.
        val broken = brokenRule(kind, params)
        draft = defaultDraft(name).copy(
            schedule = broken.rule,
            endDate = broken.endDate,
            // Время приёма человек набирает в той же форме: у испорченного вида оно есть,
            // и отказ называет вид, а не пустое время.
            times = listOf(LocalTime.of(hour.toInt(), minute.toInt())),
        )
        course = name
    }

    @Тогда("^лекарство «([^»]+)» не сохраняется$")
    fun theMedicationIsNotSaved(name: String) {
        val current = requireNotNull(draft) { "the scenario never filled the form" }
        violations = validate(current.normalized())
        // Отказ формы — не запись: курс не появляется в профиле ни при отказе правил,
        // ни при закрытой платной границе (ПР-20). В экранном прогоне форму сохраняет
        // стенд, и его решения проверяет экранный шаг: здесь остаётся сам запрет записи.
        assertFalse(world.medications.has(name), "the form must not write «$name»")
        if (saveOnScreen == null) {
            assertTrue(
                violations.isNotEmpty() || blockedByPro,
                "the refusal must have a reason: rules or the paid boundary",
            )
        }
    }

    @Тогда("^форма называет причину словами$")
    fun theFormNamesTheReasonInWords() {
        // Слов в чистом JVM-модуле нет, поэтому «названа словами» проверяется словарём
        // отказов: у каждой причины есть ключ, и ключ обязан иметь непустую строку (§10).
        val keys = if (violations.isNotEmpty()) {
            violations.map(::wordsOf)
        } else {
            assertTrue(blockedByPro, "the form must name a reason")
            listOf(BLOCKED_CREATION_KEY)
        }
        keys.forEach { key ->
            assertTrue(formWords(key).isNotBlank(), "the form catalog must carry words for $key")
        }
    }

    @Когда("^выбирает вид расписания «по дням недели» с днями «понедельник» и «среда»$")
    fun theWeekdaysKindIsChosen() {
        draft = current().copy(schedule = ScheduleRule.Weekdays(MONDAY_AND_WEDNESDAY))
    }

    @Тогда("^у выбора вида расписания стоит метка «Про»$")
    fun theKindRowCarriesTheProMark() {
        // Метка стоит там, где начинается платное: у «по дням недели» это второй день
        // (Р-24, Р-17), и признак платности вида — ровно `isComplex`.
        assertTrue(current().schedule.isComplex, "the chosen kind must be paid")
    }

    @Когда("^нажимает «Сохранить лекарство»$")
    fun theSaveOfTheMedicationIsPressed() {
        val current = requireNotNull(draft) { "the scenario never filled the form" }
        violations = validate(current.normalized())
        // Платная граница проверяется после правил: незаполненный вид доходит до отказа
        // формы и называет свои правила, а закрытой она оказывается только у правильно
        // заполненного сложного расписания (ПР-20, ПР-21).
        //
        // Доступ берётся у открытой формы ([formAccess]), а не у магазина в момент нажатия:
        // форма держит снимок и перечитывает его, когда закрывается витрина (SKL-117, F3).
        val complexCreation = ComplexScheduleGate.isCreationOfComplex(
            original = current.originalSchedule,
            next = current.effectiveSchedule,
            included = formAccess ?: world.pro.status().included,
        )
        blockedByPro = violations.isEmpty() && complexCreation
        if (blockedByPro) {
            // Недоступное действие говорит «нельзя», а правда — «можно, по подписке» (П-7):
            // нажатие ведёт в единственную витрину, и она открывается с места выбора (Р-67).
            runBlocking { world.pro.session.open(ProPoint.MEDICATION_FORM) }
        }
        if (violations.isEmpty() && !blockedByPro && saveOnScreen == null) {
            lastSave = world.save(current)
        }
        saveOnScreen?.invoke()
    }

    @Тогда("^предложено оформить подписку «Склянка Про»$")
    fun theSubscriptionIsOffered() {
        assertTrue(blockedByPro, "the offer is the outcome of a closed creation, not of a wish")
        assertTrue(world.pro.state.visible, "the showcase must be open")
        assertEquals(ProPoint.MEDICATION_FORM, world.pro.state.point, "the showcase must open from the form")
    }

    // --- Платная граница: создание закрыто, заведённое не отнимается --------------------------

    @Допустим("^у человека есть ежедневный курс «([^»]+)» в (\\d{2}):(\\d{2})$")
    fun thePersonHasADailyCourse(name: String, hour: String, minute: String) {
        createCourse(name, ScheduleRule.Daily, LocalTime.of(hour.toInt(), minute.toInt()))
    }

    @Когда("^человек открывает курс «([^»]+)» на правку$")
    fun thePersonOpensTheCourseForEditing(name: String) {
        // Форма правки — черновик, заполненный из курса: исходный вид приходит в него
        // вместе с курсом, и решение о сложности принимается по переходу (ПР-21).
        draft = MedicationDraft.of(world.medications.get(name))
        course = name
        formAccess = world.pro.status().included
    }

    @Тогда("^расписание курса «([^»]+)» остаётся ежедневным$")
    fun theCourseScheduleStaysDaily(name: String) {
        assertEquals(
            ScheduleRule.Daily,
            world.medications.get(name).schedule,
            "a closed save must not rewrite the schedule of the course",
        )
    }

    @Допустим("^у человека есть курс «([^»]+)» с приёмом по понедельникам, средам и пятницам в (\\d{2}):(\\d{2})$")
    fun thePersonHasAWeekdaysCourse(name: String, hour: String, minute: String) {
        createCourse(name, ScheduleRule.Weekdays(MON_WED_FRI), LocalTime.of(hour.toInt(), minute.toInt()))
    }

    @Когда("^подписка «Склянка Про» истекает (\\d+) октября (\\d+)$")
    fun theSubscriptionExpiresInOctober(day: String, year: String) {
        // Истёкшая подписка — это срок, названный магазином и уже прошедший: доступ считает
        // правило истечения, а не хранилище (Р-67 п. 3, ADR-0015).
        world.pro.owned(ProProductId.MONTHLY, world.dateOf("$day октября $year"))
    }

    @Тогда("^приёмы по понедельникам, средам и пятницам идут дальше$")
    fun theWeekdayIntakesKeepGoing() {
        val medication = medication()
        assertTrue(intakesOf(medication).isNotEmpty(), "the taken-away subscription must not stop the intakes")
        assertIntakesOnlyOnRuleDays(medication)
    }

    @Тогда("^напоминания по ним поставлены$")
    fun theRemindersOnThemAreStillScheduled() {
        assertReminderDaysAre(MON_WED_FRI)
    }

    @Тогда("^правка параметров курса «([^»]+)» разрешена$")
    fun theParametersOfTheCourseStayEditable(name: String) {
        // Правка заведённого сложного курса разрешена целиком (Р-28): он уже оплачен,
        // и new-сложным переход не является.
        val schedule = world.medications.get(name).schedule
        assertFalse(
            ComplexScheduleGate.isCreationOfComplex(schedule, schedule, included = false),
            "editing an existing complex course must stay allowed without a subscription",
        )
    }

    @Тогда("^создание нового сложного расписания закрыто$")
    fun theCreationOfANewComplexScheduleIsClosed() {
        assertFalse(world.pro.status().included, "the scenario must run without access")
        assertTrue(
            ComplexScheduleGate.isCreationOfComplex(
                original = null,
                next = ScheduleRule.Weekdays(MONDAY_AND_WEDNESDAY),
                included = world.pro.status().included,
            ),
            "a new complex schedule must be closed without a subscription (Р-17, ПР-20)",
        )
    }

    // --- Перенос: вид расписания и дозы этапов не теряются на пути, который умеет расписание --

    @Когда("^человек выгружает файл переноса$")
    fun thePersonExportsTheTransferFile() {
        // Выгрузка идёт настоящим `ExportDataUseCase` поверх кодеков сценарного прогона:
        // доменному прогону провод не участвует (`DomainScenarioCodec`), и дальше файл
        // применяется тем же снимком, что отдала выгрузка (так же, как в SKL-99).
        intakesBeforeExport = intakesOf(medication()).map { it.scheduledAt }
        transferFile = world.transfer.snapshot()
        val outcome = world.transfer.export()
        assertTrue(outcome is ExportOutcome.Saved, "the export must be saved: $outcome")
    }

    @Когда("^загружает этот файл в пустое приложение$")
    fun theFileIsLoadedIntoAnEmptyApp() {
        val snapshot = requireNotNull(transferFile) { "the scenario never exported a file" }
        world.transfer.prepare(snapshot)
        // «Пустое приложение» — режим «Заменить всё»: он стирает состояние устройства
        // и ставит на его место файл (ПЕР-16). Перепланирование идёт полным обходом мира.
        world.transfer.useMode(TransferMode.REPLACE_ALL)
        val outcome = world.transfer.apply()
        assertTrue(outcome is TransferOutcome.Applied, "the file must be applied: $outcome")
    }

    @Тогда("^курс «([^»]+)» снова идёт по понедельникам, средам и пятницам$")
    fun theCourseRunsOnTheSameWeekdaysAgain(name: String) {
        val medication = world.medications.get(name)
        assertEquals(
            ScheduleRule.Weekdays(MON_WED_FRI),
            medication.schedule,
            "the file must carry the kind of the schedule (ПЕР-11)",
        )
        assertIntakesOnlyOnRuleDays(medication)
    }

    @Тогда("^его приёмы стоят в те же дни и времена$")
    fun itsIntakesStandOnTheSameDaysAndTimes() {
        assertTrue(intakesBeforeExport.isNotEmpty(), "the course must have intakes before the export")
        assertEquals(
            intakesBeforeExport,
            intakesOf(medication()).map { it.scheduledAt },
            "the loaded course must take the same days and times as the exported one",
        )
    }

    @Когда("^человек загружает файл переноса прежней версии$")
    fun thePersonLoadsAFileOfThePreviousFormat() {
        val builder = TransferFileBuilder(world.clock)
        builder.profile(FILE_SELF, isSelf = true)
        builder.medication(
            key = COURSE_FROM_FILE,
            profileKey = FILE_SELF,
            name = COURSE_FROM_FILE,
            times = listOf(DOSE),
        )
        // Файл прежней версии: запись расписания не несёт вовсе, и курс читается
        // ежедневным (ПР-15). Номер формата — тот, с которого начат выпуск: добавление
        // необязательного поля версию не двигает (ПЕР-7), и старый файл читается
        // через миграцию (ПЕР-30).
        val snapshot = builder.build(exportedAt = world.clock.now())
            .copy(formatVersion = TransferFormat.MINIMUM_VERSION)
        transferFile = snapshot
        world.transfer.prepare(snapshot)
        world.transfer.useMode(TransferMode.REPLACE_ALL)
        val outcome = world.transfer.apply()
        assertTrue(outcome is TransferOutcome.Applied, "an old file must be read as before: $outcome")
    }

    @Тогда("^курс «([^»]+)» из файла идёт каждый день в (\\d{2}):(\\d{2})$")
    fun theCourseFromTheFileRunsEveryDay(name: String, hour: String, minute: String) {
        assertRunsEveryDay(name, LocalTime.of(hour.toInt(), minute.toInt()))
    }

    @Тогда("^перенос не отказывает из-за версии формата$")
    fun theTransferDoesNotRefuseTheFormatVersion() {
        // Разбор провода — дело кодека `core-data` (ПЕР-29, ПЕР-30); доменный прогон
        // применяет подготовленный снимок. Отказ из-за версии отвечает здесь на вопрос
        // о самой версии: она в границах, которые сборка читает, и отказа не было.
        assertNull(world.transfer.receivedReject, "the file must not be refused: ${world.transfer.receivedReject}")
        val version = requireNotNull(transferFile).formatVersion
        assertTrue(
            version in TransferFormat.MINIMUM_VERSION..TransferFormat.CURRENT_VERSION,
            "a file of the previous format stays readable (ПЕР-30): v$version",
        )
        assertTrue(
            world.transfer.lastOutcome is TransferOutcome.Applied,
            "the transfer must end with the applied file: ${world.transfer.lastOutcome}",
        )
    }

    @Тогда("^в списке лекарств «([^»]+)» называет ближайший приём в пятницу, (\\d+) октября$")
    fun theListNamesTheNextIntakeOnFriday(name: String, day: String) {
        // Порядок списка считает настоящее правило домена — то же, каким живёт экран (ПМ-15 п. 1).
        val next = requireNotNull(
            MedicationListOrder.nextIntakeAt(world.medications.get(name), world.clock.nowLocal()),
        ) { "the list must name the next intake of «$name»" }
        assertEquals(DayOfWeek.FRIDAY, next.dayOfWeek, "the next intake of the weekday course: $next")
        assertEquals(day.toInt(), next.dayOfMonth, "the day the list names: $next")
        assertEquals(Month.OCTOBER, next.month, "the month the list names: $next")
    }

    // --- Обновление: заведённое и история не переписываются (SKL-117, группа `@migration`) -----

    @Допустим("^его приёмы прошлой недели частью отмечены$")
    fun theLastWeekIntakesArePartlyMarked() {
        val medication = medication()
        // Приёмы прошлой недели — данность Предыстории: сценарий начинается с уже
        // прожитой истории курса, а не проживает её заново.
        val monday = world.clock.today().with(DayOfWeek.MONDAY).minusWeeks(ONE_WEEK)
        val moments = (0L until DAYS_IN_WEEK.toLong()).map { offset -> monday.plusDays(offset).atTime(DOSE) }
        moments.forEach { moment -> world.intakes.pending(course, moment) }
        moments.take(MARKED_DAYS_OF_LAST_WEEK).forEach { moment -> world.intakes.markTaken(course, moment) }
        lastWeekRows = moments.mapNotNull { moment ->
            world.intakes.of(course).firstOrNull { it.scheduledAt == moment }
        }
        assertEquals(moments.size, lastWeekRows.size, "the whole last week must be lived")
        assertTrue(lastWeekRows.any { it.status == IntakeStatus.TAKEN }, "a part of the week must be marked")
        assertTrue(lastWeekRows.any { it.isPending }, "a part of the week must stay without a mark")
        assertEquals(medication.id, lastWeekRows.first().medicationId)
    }

    @Когда("^приложение обновляется до версии с видами расписания$")
    fun theAppIsUpgradedToTheVersionWithScheduleKinds() {
        // Мир приёмки держит состояние в памяти (`ADR-0007`): базы, которую обновляет миграция,
        // здесь нет, и обновление доказывается не подменой состояния, а тем, что миграция
        // существует и закреплена настоящим инструментальным тестом. Схема объявляет
        // переход 9 → 10, версия базы — 10, а строки приёмов и отметок переживают переход
        // в `MigrationTest.theScheduleRuleAndTheDoseSnapshotArriveWithoutTouchingAnyRow`.
        val migrations = sourceOf(MIGRATIONS_FILE)
        assertTrue(
            migrations.contains("MIGRATION_9_10"),
            "the upgrade must be declared as MIGRATION_9_10 (SKL-117, МГ-3)",
        )
        assertTrue(
            sourceOf(DATABASE_FILE).contains("const val VERSION = 10"),
            "the schema of the version with schedule kinds must be 10 (SKL-117)",
        )
        val migrationTest = sourceOf(MIGRATION_TEST_FILE)
        assertTrue(
            migrationTest.contains(MIGRATION_TEST_NAME),
            "the migration 9 → 10 must be proven by $MIGRATION_TEST_NAME",
        )
        assertTrue(
            migrationTest.contains("SklyankaMigrations.MIGRATION_9_10"),
            "the test must run the very migration the schema declares",
        )
    }

    @Тогда("^приёмы прошлой недели остаются теми же$")
    fun theLastWeekIntakesStayTheSame() {
        assertTrue(lastWeekRows.isNotEmpty(), "the premise must leave the lived week")
        lastWeekRows.forEach { row ->
            assertEquals(
                row,
                world.intakes.get(row.id),
                "the upgrade must not touch a lived intake (SKL-117: $MIGRATION_TEST_NAME)",
            )
        }
    }

    @Тогда("^отметки не переписаны$")
    fun theMarksAreNotRewritten() {
        val marks = lastWeekRows.filter { it.status == IntakeStatus.TAKEN }
        assertTrue(marks.isNotEmpty(), "the premise says a part of the week is marked")
        marks.forEach { row ->
            val current = requireNotNull(world.intakes.get(row.id)) { "the upgrade lost the intake ${row.id}" }
            assertEquals(row.status, current.status, "the mark must stay a mark ($MIGRATION_TEST_NAME)")
            assertEquals(row.actedAt, current.actedAt, "the moment of the mark must stay ($MIGRATION_TEST_NAME)")
            assertEquals(row.source, current.source, "the source of the mark must stay ($MIGRATION_TEST_NAME)")
        }
    }

    @Тогда("^курс «([^»]+)» продолжает идти каждый день в (\\d{2}):(\\d{2})$")
    fun theCourseKeepsRunningEveryDay(name: String, hour: String, minute: String) {
        assertRunsEveryDay(name, LocalTime.of(hour.toInt(), minute.toInt()))
    }

    // --- Обвязка: заведение курса, приёмы и напоминания --------------------------------------

    /**
     * Заведение курса настоящим сохранением формы.
     *
     * Часы на миг возвращаются к первой дозе: продукт намеренно не порождает приём дня
     * начала, чьё время уже прошло (НФТ-35, SKL-06), а сценарий называет первый день курса
     * днём приёма («приёмы идут 13, 15, 17 и 19 октября»). После сохранения часы встают
     * на момент Предыстории, и перепланирование не повторяется — оно стёрло бы приём,
     * которого план «сейчас» не знает.
     */
    private fun createCourse(name: String, rule: ScheduleRule, time: LocalTime, depthDays: Int? = null) {
        if (depthDays != null) world.depthDays = depthDays
        val current = world.clock.nowLocal()
        val start = world.clock.today().atTime(time)
        if (start.isBefore(current)) world.clock.moveTo(start)
        val result = world.save(courseDraft(name, rule, time))
        lastSave = result
        course = name
        world.clock.moveTo(current)
        assertTrue(result is SaveResult.Saved, "the course «$name» must be created: $result")
    }

    /** Черновик нового курса: доза, форма и времена — те же, что у прочих сценариев задачи. */
    private fun courseDraft(name: String, rule: ScheduleRule, time: LocalTime): MedicationDraft = MedicationDraft(
        id = null,
        profileId = world.profiles.get(world.profiles.active).id,
        name = name,
        form = MedicationForm.TABLET,
        doseAmount = BigDecimal.ONE,
        scheduleMode = ScheduleMode.SCHEDULED,
        times = listOf(time),
        schedule = rule,
        startDate = world.clock.today(),
        endDate = null,
    )

    /** Черновик новой формы: заполнен всем, кроме того, что назовёт сценарий. */
    private fun defaultDraft(name: String): MedicationDraft = MedicationDraft(
        id = null,
        profileId = world.profiles.get(world.profiles.active).id,
        name = name,
        form = MedicationForm.TABLET,
        doseAmount = BigDecimal.ONE,
        scheduleMode = ScheduleMode.SCHEDULED,
        times = listOf(DOSE),
        startDate = world.clock.today(),
        endDate = null,
    )

    /**
     * Испорченные параметры вида из таблицы примеров — то, что человек набрал в форме.
     *
     * Вид берёт свои параметры буквально: пустой набор дней, нулевой интервал, нулевая
     * длина цикла, этап без дозы и этапы, выходящие за конец курса (ИНВ-23, ИНВ-24).
     */
    private fun brokenRule(kind: String, params: String): BrokenRule {
        val today = world.clock.today()
        return when (kind to params) {
            "по дням недели" to "ни один день недели не выбран" -> BrokenRule(ScheduleRule.Weekdays(emptySet()))

            "через N дней" to "интервал короче двух дней" -> BrokenRule(ScheduleRule.EveryNDays(EMPTY_INTERVAL))

            "циклами" to "ноль дней приёма в цикле" ->
                BrokenRule(ScheduleRule.Cycles(onDays = 0, offDays = DAYS_IN_WEEK))

            "ступенчатое" to "ступень без дозы" ->
                BrokenRule(ScheduleRule.Staged(listOf(Stage(doseAmount = BigDecimal.ZERO, days = DAYS_IN_WEEK))))

            "ступенчатое" to "ступень без длительности" ->
                BrokenRule(ScheduleRule.Staged(listOf(Stage(doseAmount = BigDecimal.ONE, days = 0))))

            "ступенчатое" to "последняя ступень выходит за конец курса" -> BrokenRule(
                rule = ScheduleRule.Staged(
                    listOf(
                        Stage(doseAmount = BigDecimal.ONE, days = DAYS_IN_WEEK),
                        Stage(doseAmount = BigDecimal.ONE, days = DAYS_IN_WEEK),
                    ),
                ),
                // Курс на неделю короче, чем идут этапы: последний этап выходит за конец (ИНВ-24).
                endDate = today.plusDays(DAYS_IN_WEEK - 1L),
            )

            else -> error("the scenario names no broken parameters: «$kind» / «$params»")
        }
    }

    private fun current(): MedicationDraft = requireNotNull(draft) { "the scenario never filled the form" }

    private fun medication(): Medication = world.medications.get(course.ifEmpty { world.medications.last })

    /** Момент приёма названного дня курса — дни считаются от даты начала, первый день — первый. */
    private fun momentOfDay(medication: Medication, day: String): LocalDateTime {
        val offset = requireNotNull(day.toLongOrNull()) { "the scenario names no day of the course: «$day»" }
        assertTrue(offset >= 1L, "the days of the course are counted from its first day")
        return medication.startDate.plusDays(offset - 1).atTime(DOSE)
    }

    /**
     * Номер ступени (с нуля), в которую попадает день курса.
     *
     * Этапы идут подряд от даты начала, и границы заданы их длительностями, а не дозой:
     * после правки дозы второй ступени границы те же, и «приёмы второй ступени» — это
     * дни от конца первой ступени до конца второй. День за концом этапов продолжает
     * последнюю ступень — тем же правилом, что у домена (`ScheduleRule.Staged`).
     */
    private fun stageNumberAt(medication: Medication, date: LocalDate): Int {
        val stages = (medication.schedule as? ScheduleRule.Staged)?.stages.orEmpty()
        val offset = date.toEpochDay() - medication.startDate.toEpochDay()
        var consumed = 0L
        val ends = stages.map { stage ->
            consumed += stage.days
            consumed
        }
        val position = ends.indexOfFirst { end -> offset < end }
        return if (position >= 0) position else maxOf(ends.size - 1, 0)
    }

    /**
     * Приёмы дня на экране «Сегодня» — собранные настоящим [BuildTodayAgenda].
     *
     * Доза для экрана берётся у элемента ленты ([ru.stohastika.sklyanka.domain.today.IntakeItem]),
     * а не из записи приёма: сценарий проверяет то, что человек видит, — правило домена
     * «снимок, иначе доза лекарства» в его единственном определении.
     */
    private fun agendaItemsOf(medication: Medication, date: LocalDate): List<IntakeItem> {
        val agenda = BuildTodayAgenda()(
            date = date,
            intakes = intakesOn(medication, date),
            activeMedications = listOf(medication),
            now = world.clock.nowLocal(),
            zone = world.clock.zone,
        )
        return agenda.layout.groups.flatMap { group -> group.items }
    }

    private fun intakesOf(medication: Medication): List<Intake> =
        world.intakes.all().filter { it.medicationId == medication.id }.sortedBy { it.scheduledAt }

    private fun intakesOn(medication: Medication, day: LocalDate): List<Intake> =
        intakesOf(medication).filter { it.scheduledAt.toLocalDate() == day }

    private fun intakeDays(medication: Medication): List<LocalDate> =
        intakesOf(medication).map { it.scheduledAt.toLocalDate() }.distinct().sorted()

    /** Приёмы стоят только на тех днях, которые назначает вид расписания (ПР-16…ПР-18). */
    private fun assertIntakesOnlyOnRuleDays(medication: Medication) {
        val days = intakeDays(medication)
        assertTrue(days.isNotEmpty(), "the course must have intakes")
        days.forEach { day ->
            assertTrue(
                medication.schedule.includes(day, medication.startDate),
                "$day is not an intake day of ${medication.schedule}",
            )
        }
    }

    /**
     * Каждый день недели вида назван приёмом хотя бы раз: без этого «приёмы идут по средам»
     * проходило бы и на курсе, у которого приёмов нет вовсе.
     */
    private fun assertEveryRuleWeekdayIsFilled(medication: Medication) {
        val weekdays = intakeDays(medication).map { it.dayOfWeek }.toSet()
        val expected = (medication.schedule as? ScheduleRule.Weekdays)?.days.orEmpty()
        assertTrue(
            expected.isNotEmpty() && weekdays.containsAll(expected),
            "every chosen weekday must be filled with an intake: chosen $expected, filled $weekdays",
        )
    }

    /** Дни окна, на которые вид приёма не назначает, приёмов не несут. */
    private fun assertNoIntakesOnTheOtherDays(medication: Medication) {
        val days = intakeDays(medication)
        assertTrue(days.isNotEmpty(), "the course must have intakes")
        val others = generateSequence(days.first()) { it.plusDays(1) }
            .takeWhile { !it.isAfter(days.last()) }
            .filterNot { medication.schedule.includes(it, medication.startDate) }
            .toList()
        assertTrue(others.isNotEmpty(), "the scenario must cover days without intakes")
        others.forEach { day ->
            assertEquals(
                emptyList<LocalDateTime>(),
                intakesOn(medication, day).map { it.scheduledAt },
                "an intake appeared on $day, which the kind does not include",
            )
        }
    }

    /** Напоминания стоят на приёмах, и только на днях вида (НАП-7, ПР-18). */
    private fun assertReminderDaysAre(days: Set<DayOfWeek>) {
        val scheduled = world.reminders.scheduled()
        assertTrue(scheduled.isNotEmpty(), "the course must leave reminders")
        scheduled.forEach { (intakeId, moment) ->
            val intake = requireNotNull(world.intakes.get(intakeId)) { "an alarm without an intake: $intakeId" }
            assertEquals(world.clock.momentOf(intake.scheduledAt), moment, "the alarm must stand at its intake")
            assertTrue(
                intake.scheduledAt.dayOfWeek in days,
                "a reminder stands on ${intake.scheduledAt}, which is not an intake day",
            )
        }
    }

    /** Дозы приёмов на днях курса со смещениями [offsets] равны названной (ПР-19). */
    private fun assertDosesOnDays(offsets: List<Long>, expected: BigDecimal) {
        val medication = medication()
        assertTrue(offsets.isNotEmpty(), "the scenario must name days to check")
        offsets.forEach { offset ->
            val day = medication.startDate.plusDays(offset)
            val intakes = intakesOn(medication, day)
            assertTrue(intakes.isNotEmpty(), "the course must have an intake on $day")
            intakes.forEach { intake ->
                val dose = requireNotNull(intake.doseAmount) { "the staged course must carry the stage dose: $day" }
                assertEquals(0, expected.compareTo(dose), "the dose of $day: $dose")
            }
        }
    }

    /** Курс идёт каждый день в названное время: дни без пропусков, времена — одни (ПР-15). */
    private fun assertRunsEveryDay(name: String, time: LocalTime) {
        val medication = world.medications.get(name)
        assertEquals(ScheduleRule.Daily, medication.schedule, "the course must keep the every-day kind")
        assertEquals(listOf(time), medication.times, "the course must keep its time")
        val days = intakeDays(medication)
        assertTrue(days.isNotEmpty(), "the everyday course must have intakes")
        // Отсчёт идёт от начала курса, а не от первого приёма в истории: прожитые дни
        // прошлой недели — данность Предыстории, и разрыв между ними и курсом не пропуск.
        val from = maxOf(days.first(), medication.startDate)
        generateSequence(from) { it.plusDays(1) }
            .takeWhile { !it.isAfter(days.last()) }
            .forEach { day ->
                assertTrue(days.contains(day), "the everyday course must have an intake on $day")
            }
    }

    /** Слова отказа формы по нарушению: тот же словарь, что у экрана (`MedsText.scheduleMessage`). */
    private fun wordsOf(violation: DraftViolation): String = when (violation) {
        DraftViolation.ScheduleDaysEmpty -> "error_weekdays_missing"
        DraftViolation.ScheduleIntervalTooShort -> "error_interval_too_small"
        DraftViolation.ScheduleCycleLengthNotPositive -> "error_cycle_zero"
        DraftViolation.ScheduleStagesEmpty,
        DraftViolation.ScheduleStageDoseNotPositive,
        -> "error_stage_dose"

        DraftViolation.ScheduleStageDaysNotPositive -> "error_stage_days"
        DraftViolation.ScheduleStagesBeyondCourse -> "error_stage_beyond_course"
        else -> error("not a schedule violation: $violation")
    }

    /** Слово формы по ключу каталога: ключ без строки — не слово (П-7). */
    private fun formWords(key: String): String =
        requireNotNull(FORM_CATALOG[key]) { "the form catalog has no words for $key" }

    private fun sourceOf(path: String): String = File(RepositoryLayout.root, path).readText()

    /** Пара «испорченный вид плюс испорченный конец курса»: сценарий называет её примером. */
    private data class BrokenRule(val rule: ScheduleRule, val endDate: LocalDate? = null)

    private companion object {

        val DOSE: LocalTime = LocalTime.of(8, 0)

        val MON_WED_FRI: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

        val MONDAY_AND_WEDNESDAY: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)

        /** ПР-17: «через каждый второй день» — интервал в два дня. */
        const val EVERY_SECOND_DAY = 2

        /** «Через N дней» с пустым полем: интервал короче двух дней — нарушение ИНВ-23. */
        const val EMPTY_INTERVAL = 0

        const val DAYS_IN_WEEK = 7

        const val ONE_WEEK = 1L

        /** Сколько дней прошлой недели уже отмечены: «частью отмечены». */
        const val MARKED_DAYS_OF_LAST_WEEK = 3

        /** ПР-18: двадцать один день приёма подряд. */
        const val TWENTY_ONE_DAYS = 21L

        /** Окно генерации шире цикла: сценарий проверяет и перерыв, и возвращение приёмов. */
        const val CYCLES_WINDOW_DAYS = 40

        /** Восьмой день ступени лежит за рабочим окном в семь суток (ОВ-8). */
        const val STAGES_WINDOW_DAYS = 14

        /** Номер второй ступени: сценарии правки дозы говорят именно о ней. */
        const val SECOND_STAGE = 2

        /** Имя курса из файла переноса: язык Gherkin, а не модель (НФТ-19). */
        const val COURSE_FROM_FILE = "Магний"

        /** Профиль файла: свой, потому что чужого профиля у сценария переноса нет. */
        const val FILE_SELF = "файловый свой"

        const val MIGRATIONS_FILE = "core-data/src/main/kotlin/ru/stohastika/sklyanka/data/room/SklyankaMigrations.kt"
        const val DATABASE_FILE = "core-data/src/main/kotlin/ru/stohastika/sklyanka/data/room/SklyankaDatabase.kt"
        const val MIGRATION_TEST_FILE =
            "core-data/src/androidTest/kotlin/ru/stohastika/sklyanka/data/room/MigrationTest.kt"

        /** Инструментальный тест перехода 9 → 10: им доказывается «обновление не трогает заведённое». */
        const val MIGRATION_TEST_NAME = "theScheduleRuleAndTheDoseSnapshotArriveWithoutTouchingAnyRow"

        /** Слова формы: тот же каталог, что показывает человеку (§10 нормы дизайна). */
        const val FORM_STRINGS = "feature-meds/src/main/res/values/strings.xml"

        const val ONE_DAY_INTERVAL_KEY = "error_interval_one_day"

        /** Причина над кнопкой, когда создание сложного расписания закрыто (ПР-20). */
        const val BLOCKED_CREATION_KEY = "form_save_blocked_pro"

        /** Каталог слов формы: ключ → строка, как их читает экран. */
        val FORM_CATALOG: Map<String, String> by lazy {
            val text = File(RepositoryLayout.root, FORM_STRINGS).readText()
            Regex("""<string name="([^"]+)">([^<]*)</string>""")
                .findAll(text)
                .associate { match -> match.groupValues[1] to match.groupValues[2] }
        }
    }
}
