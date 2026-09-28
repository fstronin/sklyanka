package ru.heuri.sklyanka.acceptance.world

import kotlinx.coroutines.runBlocking
import ru.heuri.sklyanka.domain.history.AdherenceCalculator
import ru.heuri.sklyanka.domain.history.AdherencePeriod
import ru.heuri.sklyanka.domain.history.HistoryWindow
import ru.heuri.sklyanka.domain.intake.CreatePrnIntakeUseCase
import ru.heuri.sklyanka.domain.intake.MarkIntakeUseCase
import ru.heuri.sklyanka.domain.intake.MarkOutcome
import ru.heuri.sklyanka.domain.intake.MarkRequest
import ru.heuri.sklyanka.domain.intake.MarkResult
import ru.heuri.sklyanka.domain.intake.MarkSideEffects
import ru.heuri.sklyanka.domain.intake.PrnOutcome
import ru.heuri.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.heuri.sklyanka.domain.intake.SweepReason
import ru.heuri.sklyanka.domain.medication.MedicationDraft
import ru.heuri.sklyanka.domain.medication.MedicationListRules
import ru.heuri.sklyanka.domain.medication.SaveMedicationUseCase
import ru.heuri.sklyanka.domain.medication.SaveResult
import ru.heuri.sklyanka.domain.medication.ValidateMedicationDraft
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.reminder.RescheduleReason
import ru.heuri.sklyanka.domain.schedule.IntakeGenerationPolicy
import ru.heuri.sklyanka.domain.schedule.MedicationPlanner
import ru.heuri.sklyanka.domain.schedule.PlanIntakesUseCase
import ru.heuri.sklyanka.domain.schedule.PlanReason
import ru.heuri.sklyanka.domain.schedule.PlanResult
import ru.heuri.sklyanka.domain.schedule.PlanScope
import ru.heuri.sklyanka.domain.today.GroupMarkOutcome
import ru.heuri.sklyanka.domain.today.MarkIntakeGroupUseCase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/**
 * Состояние одного сценария приёмки: создаётся на сценарий и живёт ровно его.
 *
 * Мир один на все классы шагов сценария — и общие, и принадлежащие задаче. До него у
 * сценария было по состоянию на каждый класс шагов: строка «сейчас …» ставила часы одному
 * состоянию, а проверка читала другое, и шаг, доставшийся от соседней задачи, работал
 * в чужом состоянии. Экземпляр раздаёт фабрика объектов Cucumber
 * (`cucumber-picocontainer`: контейнер создаётся на сценарий и отдаёт один мир всем
 * классам шагов этого сценария), поэтому класс публичный — `internal`-тип нельзя принять
 * параметром конструктора публичного glue-класса.
 *
 * Хранилище — в памяти, а не Room: сценарии говорят о доменных правилах, а эмулятора
 * в сборочном контейнере нет. Транзакционность проверяют инструментальные тесты `core-data`.
 */
@Suppress("TooManyFunctions")
class AcceptanceWorld {

    /** Часы и пояс сценария — единственный источник времени (НФТ-48). */
    val clock = ScenarioClock()

    val profiles = ScenarioProfiles()

    val medications = ScenarioMedications(clock)

    val intakes = ScenarioIntakes(clock, medications)

    val log = ScenarioLog(clock)

    /** Репозитории поверх состояния: под ними гоняются настоящие доменные сценарии. */
    val repositories = ScenarioRepositories(profiles, medications, intakes)

    /** Активный профиль как состояние приложения: переключение и перезапуск (ПРФ-17, ПРФ-18). */
    val profileSession = ScenarioProfileSession(profiles, repositories.profileStore)

    /** История приёмов: срез периода и отчёт врачу настоящими сценариями домена (SKL-18). */
    val history = ScenarioHistory(
        medications = medications,
        intakes = intakes,
        profiles = repositories.profileStore,
        activeProfile = profileSession.activeStore,
        clock = clock,
        log = log,
    )

    /** Подсистема напоминаний: будильники, карточки и журнал (SKL-13, ПРФ-6). */
    val reminders = ScenarioReminders(clock, profiles, medications, intakes, repositories, log)

    /**
     * Чужое состояние системы: разрешения и связь с часами (SKL-20, НФТ-52).
     *
     * Читается настоящими портами экранов, а меняется строками Предыстории сценария.
     */
    val system = ScenarioSystemState()

    /**
     * Онбординг разрешений (SKL-14): что сохранено, что подсказано и что записано.
     *
     * Сохранённых фактов ровно два — «видел экран» и «подтвердил автозапуск»; состояния
     * разрешений читаются из [system] при каждом показе (НФТ-52).
     */
    val onboarding = ScenarioOnboarding()

    /**
     * Перенос данных: порты файловой системы и хранилища в памяти (SKL-19).
     *
     * Перепланирование после загрузки идёт тем же полным обходом, что в продукте:
     * все профили, затем каждый архивированный курс, и следом пересборка очереди
     * будильников настоящим `RescheduleRemindersUseCase` (ПЕР-18, НАП-11).
     */
    val transfer = ScenarioTransfer(
        profiles = profiles,
        medications = medications,
        intakes = intakes,
        activeProfile = profileSession.activeStore,
        log = log,
        clock = clock,
        fullReplan = {
            // Порядок полного обхода НАП-34: сначала просроченные, потом генерация,
            // потом будильники — как в `IntakePlanningTrigger` продукта.
            sweep(SweepReason.REPLAN)
            plan(PlanScope.AllProfiles, PlanReason.DATA_IMPORTED)
            reminders.reschedule(RescheduleReason.DATA_IMPORTED)
        },
        medicationReplan = { medicationId ->
            sweep(SweepReason.REPLAN)
            plan(PlanScope.Medication(medicationId), PlanReason.DATA_IMPORTED)
            reminders.reschedule(RescheduleReason.DATA_IMPORTED)
        },
    )

    /** Глубина окна генерации приёмов: значение приходит из Предыстории, а не из кода. */
    var depthDays: Int = DEFAULT_DEPTH_DAYS

    // --- Снимок состояния до действия -------------------------------------------------------

    /**
     * Было ли в сценарии «Когда».
     *
     * Строка «остаток упаковки «Л» — 30 таблеток» в Предыстории задаёт остаток, а та же
     * строка после действия его проверяет. Ключевое слово Gherkin до шага не доходит,
     * поэтому различает их порядок: до действия — задание, после — проверка.
     */
    var acted: Boolean = false
        private set

    val before: Map<String, Intake> get() = intakes.before

    /**
     * Запомнить состояние перед первым действием сценария.
     *
     * Снимок берётся один раз: шаги «Когда» бывают парой — правка расписания и следом
     * запуск планировщика, — и сверяться нужно с состоянием до правки.
     */
    fun rememberState() {
        acted = true
        intakes.remember()
        medications.rememberStock()
    }

    /**
     * Приверженность на момент до действия: ею проверяется «пробное не меняет историю» (НАП-22).
     *
     * Снимок берётся не из кольца приёмов, а настоящим доменным расчётом (SKL-18): второй
     * копии правила «что считается приверженностью» в приёмке нет.
     */
    var adherenceBefore: AdherencePeriod? = null
        private set

    fun rememberAdherence() {
        adherenceBefore = adherenceOn(clock.today())
    }

    /** Приверженность за месяц названной даты — тем же `AdherenceCalculator`, что в истории. */
    fun adherenceOn(date: LocalDate): AdherencePeriod {
        val active = profiles.active
        require(active.isNotEmpty()) { "the scenario names no profile" }
        val window = HistoryWindow.of(YearMonth.from(date))
        val profileId = profiles.get(active).id
        return AdherenceCalculator().of(history.slice(profileId, window), window, date, clock.zone)
    }

    // --- Действия: планирование --------------------------------------------------------------

    fun plan(scope: PlanScope = PlanScope.AllProfiles, reason: PlanReason = PlanReason.MANUAL_CHECK): PlanResult {
        val useCase = PlanIntakesUseCase(
            profiles = repositories.profileStore,
            medications = repositories.medicationStore,
            intakes = repositories.intakeStore,
            time = clock.time,
            log = log,
            policy = IntakeGenerationPolicy(windowDepthDays = depthDays),
        )
        return runBlocking { useCase(scope, reason) }
    }

    fun planMedication(name: String, reason: PlanReason) {
        plan(PlanScope.Medication(medications.get(name).id), reason)
    }

    /**
     * Перепланирование одного лекарства в виде порта [MedicationPlanner] (`SKL-09`).
     *
     * Повторяет связку продукта (`MedicationCardModule.provideMedicationPlanner` над
     * `IntakePlanningTrigger.planMedication`): сначала проход по просроченным, потом
     * генерация окна приёмов, потом пересборка будильников — тем же настоящим
     * `RescheduleRemindersUseCase`. Второй реализации «что произойдёт после сохранения»
     * в приёмке нет.
     */
    fun planner(): MedicationPlanner = MedicationPlanner { medicationId, reason ->
        sweep(SweepReason.REPLAN)
        val result = plan(PlanScope.Medication(medicationId), reason)
        reminders.reschedule(reminderReason(reason))
        result
    }

    /**
     * Причина перепланирования по причине генерации — таблица НАП-11 как в `app`
     * (`ReminderPlanReasons.rescheduleReason`). Через порт проходят только причины одного
     * лекарства; прочие — ошибка программиста, и отказ здесь обязателен, как и в продукте.
     *
     * Соответствие повторено, а не переиспользовано: `ReminderPlanReasons` — внутренний
     * объект `app`, а модуль приёмки остаётся чистым JVM (`ADR-0007`) и `app` не видит.
     */
    private fun reminderReason(reason: PlanReason): RescheduleReason = when (reason) {
        PlanReason.MEDICATION_ADDED -> RescheduleReason.MEDICATION_ADDED
        PlanReason.MEDICATION_SAVED -> RescheduleReason.SCHEDULE_EDITED
        PlanReason.MEDICATION_ARCHIVED -> RescheduleReason.MEDICATION_ARCHIVED
        PlanReason.MEDICATION_RESTORED -> RescheduleReason.MEDICATION_UNARCHIVED
        else -> error("Generation for reason ${reason.code} does not change one medication")
    }

    // --- Счётчики списка лекарств ----------------------------------------------------------------

    /** Сколько у профиля активных по расписанию и сколько в архиве — правилом ЭКР-3 из домена. */
    fun medicationCounts(profileName: String = profiles.active): ListCounts {
        val profileId = profiles.get(profileName).id
        val owned = medications.all().filter { it.profileId == profileId }
        return ListCounts(
            active = MedicationListRules.activeCount(owned),
            archived = owned.count { it.isArchived },
        )
    }

    /**
     * Счётчики до последнего изменения состава — предмет проверок «уменьшился на единицу».
     *
     * Задание Предыстории и проверка следствия лежат в разных классах шагов, и передать
     * счётчики друг другу можно только через мир сценария. Заполняется действием, которое
     * состав меняет: «лекарство отправлено в архив» (SKL-09).
     */
    var medicationCountsBefore: ListCounts? = null

    /** Пара счётчиков списка лекарств: «Активные · N» и «Архив · N». */
    data class ListCounts(val active: Int, val archived: Int)

    // --- Действия: сохранение лекарства ----------------------------------------------------------

    /**
     * Сохранение лекарства настоящим `SaveMedicationUseCase` (`SKL-09`).
     *
     * Проверка, запись и порождение приёмов — тот же путь, что у формы в продукте:
     * приёмка проверяет следствие сохранения, а не повторяет его правила.
     */
    fun save(draft: MedicationDraft): SaveResult {
        val useCase = SaveMedicationUseCase(
            validate = ValidateMedicationDraft(),
            medications = repositories.medicationStore,
            planner = planner(),
            transactor = ScenarioTransactor(intakes, medications),
            log = log,
            newId = { "medication-saved-${++savedMedications}" },
        )
        return runBlocking { useCase(draft) }
    }

    /** Идентификаторы сохранённых сценарием курсов: детерминированные, но разные. */
    private var savedMedications: Int = 0

    // --- Действия: отметка приёма --------------------------------------------------------------

    /**
     * Отметка приёма через единственную точку входа домена.
     *
     * Отказ домена не роняет шаг: проигравшая отметка — штатный ход событий, и сценарий
     * проверяет именно то, что человеку при этом ничего не показали.
     */
    fun mark(intakeId: String, result: MarkResult, source: IntakeSource, actedAt: Instant): MarkOutcome =
        applyMark(intakeId, result, source, actedAt, MarkSideEffects.None)

    /**
     * Отметка с последствиями подсистемы напоминаний (SKL-13, НАП-30).
     *
     * Отличается от [mark] ровно точкой композиции: снятие уведомления и отзыв карточки
     * часов приходят теми же последствиями, что у продукта (`ReminderModule`), а не
     * повторяются шагом. Нужна сценарию «отметка с часов закрывает напоминание».
     */
    fun markWithSideEffects(intakeId: String, result: MarkResult, source: IntakeSource, actedAt: Instant): MarkOutcome =
        applyMark(intakeId, result, source, actedAt, reminders.markSideEffects())

    private fun applyMark(
        intakeId: String,
        result: MarkResult,
        source: IntakeSource,
        actedAt: Instant,
        sideEffects: MarkSideEffects,
    ): MarkOutcome {
        val useCase = MarkIntakeUseCase(
            repositories.intakeStore,
            repositories.medicationStore,
            repositories.profileStore,
            clock.time,
            sideEffects,
        )
        val outcome = runCatching {
            runBlocking { useCase(MarkRequest(intakeId, result, source, actedAt)) }
        }.getOrElse { failure ->
            intakes.messagesToHuman += failure.message.orEmpty()
            MarkOutcome.NotFound
        }
        intakes.outcomes += outcome
        when (outcome) {
            is MarkOutcome.Applied -> intakes.appliedMarks[outcome.intake.id] = outcome.intake
            is MarkOutcome.Superseded -> intakes.appliedMarks[outcome.intake.id] = outcome.intake
            else -> Unit
        }
        return outcome
    }

    fun markCurrent(result: MarkResult, source: IntakeSource, at: LocalDateTime): MarkOutcome {
        val intake = intakes.currentOrScheduled()
        clock.moveTo(at)
        return mark(intake.id, result, source, clock.momentOf(at))
    }

    /**
     * Отметка с телефона.
     *
     * Перед действием доходят нажатия с часов, чей срок доставки уже наступил, после —
     * все остальные: сообщение с часов приходит позже нажатия, и именно на этом стоит
     * правило первенства по времени действия (ПР-3).
     */
    fun markFromPhone(at: LocalDateTime, result: MarkResult) {
        rememberState()
        deliverPressesUntil(clock.momentOf(at))
        markCurrent(result, IntakeSource.PHONE, at)
        deliverRemainingPresses()
    }

    /**
     * Отметка, пришедшая с экрана: намерение кнопки называет приём, а не «текущий».
     *
     * От [markFromPhone] отличается только адресатом — два прохода по нажатиям часов
     * и правило первенства по времени действия те же (ПР-3). Копии этого правила
     * у экранных шагов нет: кнопка говорит о своём приёме, а решает домен.
     */
    fun markFromScreen(intakeId: String, at: LocalDateTime, result: MarkResult) {
        rememberState()
        val moment = clock.momentOf(at)
        deliverPressesUntil(moment)
        mark(intakeId, result, IntakeSource.PHONE, moment)
        deliverRemainingPresses()
        intakes.focusOn(requireNotNull(intakes.get(intakeId)) { "unknown intake: $intakeId" })
    }

    /**
     * Отметка целой группы одновременных приёмов — нажатие «Отметить все три» (ОВ-32).
     *
     * Идёт через настоящий [MarkIntakeGroupUseCase]: правило «вся группа или никак»
     * и единое время отметки проверяются, а не пересказываются. Отказ хранилища
     * возвращается наружу как [GroupMarkOutcome.Rejected], а не бросается: сценарий
     * проверяет последствия отказа, а не исключение.
     */
    fun markGroup(intakeIds: List<String>, at: LocalDateTime, result: MarkResult = MarkResult.TAKEN): GroupMarkOutcome {
        rememberState()
        val useCase = MarkIntakeGroupUseCase(
            markIntake = MarkIntakeUseCase(
                repositories.intakeStore,
                repositories.medicationStore,
                repositories.profileStore,
                clock.time,
            ),
            transactor = ScenarioTransactor(intakes, medications),
            time = clock.time,
            log = log,
        )
        val outcome = runBlocking { useCase(intakeIds, IntakeSource.PHONE, result, clock.momentOf(at)) }
        intakes.lastGroupOutcome = outcome
        intakes.lastGroupIds = intakeIds
        return outcome
    }

    /**
     * Сценарий «запись отметки «Омеза» не может быть выполнена»: хранилище отказывает
     * на записи названного лекарства.
     */
    fun failWriteFor(medication: String) {
        intakes.failingMedicationId = medications.get(medication).id
    }

    /**
     * Уже отмеченный приём как данность Предыстории.
     *
     * Отметка идёт через домен, а не записью в обход правил: сценарий, начинающийся
     * с отмеченного приёма, обязан начинаться с того же состояния, которое даёт отметка.
     */
    fun markExisting(medication: String, at: LocalDateTime, markedAt: LocalTime, result: MarkResult) {
        val intake = intakes.pending(medication, at)
        val moment = at.toLocalDate().atTime(markedAt)
        clock.moveTo(moment)
        mark(intake.id, result, IntakeSource.PHONE, clock.momentOf(moment))
        intakes.focusOn(intake)
    }

    // --- Действия: часы ------------------------------------------------------------------------

    /** Нажатие на часах: действие произошло сейчас, а дойдёт до телефона когда дойдёт (ЧАС-23). */
    fun pressOnWatch(result: MarkResult, at: LocalDateTime) {
        val intake = intakes.currentOrScheduled()
        intakes.presses += WatchPress(intake.id, result, clock.momentOf(at), clock.momentOf(at))
    }

    fun delayLastPress(to: LocalDateTime) {
        intakes.presses.lastOrNull()?.deliverAt = clock.momentOf(to)
    }

    /** Доставка нажатий, дошедших до телефона к указанному моменту, в порядке доставки. */
    fun deliverPressesUntil(moment: Instant) {
        val due = intakes.presses.filter { !it.deliverAt.isAfter(moment) }.sortedBy { it.deliverAt }
        intakes.presses.removeAll(due)
        due.forEach { press -> mark(press.intakeId, press.result, IntakeSource.WATCH, press.actedAt) }
    }

    /** Остальные нажатия доходят позже действия на телефоне — и решают гонку по времени действия. */
    fun deliverRemainingPresses() {
        val rest = intakes.presses.sortedBy { it.deliverAt }
        intakes.presses.clear()
        rest.forEach { press -> mark(press.intakeId, press.result, IntakeSource.WATCH, press.actedAt) }
    }

    // --- Действия: приём по потребности и просроченные приёмы ------------------------------------

    fun pressTakeOnDemand(name: String): PrnOutcome {
        val useCase = CreatePrnIntakeUseCase(
            repositories.intakeStore,
            repositories.medicationStore,
            repositories.profileStore,
            clock.time,
        )
        medications.focusOn(name)
        val outcome = runCatching {
            runBlocking { useCase(medications.get(name).id, IntakeSource.PHONE) }
        }.getOrElse { failure ->
            intakes.messagesToHuman += failure.message.orEmpty()
            PrnOutcome.UnknownMedication
        }
        intakes.lastPrnOutcome = outcome
        if (outcome is PrnOutcome.Created) intakes.focusOn(outcome.intake)
        return outcome
    }

    /** Проход по просроченным приёмам: и по таймеру, и при открытии приложения (НАП-34). */
    fun sweep(reason: SweepReason = SweepReason.TIMER) {
        val useCase = SweepMissedIntakesUseCase(repositories.intakeStore, intakes.window, clock.time, log)
        runBlocking { useCase(reason) }
    }

    private companion object {
        const val DEFAULT_DEPTH_DAYS = 7
    }
}
