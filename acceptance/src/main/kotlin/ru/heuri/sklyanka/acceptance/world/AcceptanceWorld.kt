package ru.heuri.sklyanka.acceptance.world

import kotlinx.coroutines.runBlocking
import ru.heuri.sklyanka.domain.intake.CreatePrnIntakeUseCase
import ru.heuri.sklyanka.domain.intake.MarkIntakeUseCase
import ru.heuri.sklyanka.domain.intake.MarkOutcome
import ru.heuri.sklyanka.domain.intake.MarkRequest
import ru.heuri.sklyanka.domain.intake.MarkResult
import ru.heuri.sklyanka.domain.intake.PrnOutcome
import ru.heuri.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.heuri.sklyanka.domain.intake.SweepReason
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.schedule.IntakeGenerationPolicy
import ru.heuri.sklyanka.domain.schedule.PlanIntakesUseCase
import ru.heuri.sklyanka.domain.schedule.PlanReason
import ru.heuri.sklyanka.domain.schedule.PlanScope
import ru.heuri.sklyanka.domain.today.GroupMarkOutcome
import ru.heuri.sklyanka.domain.today.MarkIntakeGroupUseCase
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime

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

    val log = ScenarioLog()

    /** Репозитории поверх состояния: под ними гоняются настоящие доменные сценарии. */
    val repositories = ScenarioRepositories(profiles, medications, intakes)

    /** Активный профиль как состояние приложения: переключение и перезапуск (ПРФ-17, ПРФ-18). */
    val profileSession = ScenarioProfileSession(profiles, repositories.profileStore)

    /** Подсистема напоминаний: будильники, карточки и журнал (SKL-13, ПРФ-6). */
    val reminders = ScenarioReminders(clock, profiles, medications, intakes, repositories, log)

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

    // --- Действия: планирование --------------------------------------------------------------

    fun plan(scope: PlanScope = PlanScope.AllProfiles, reason: PlanReason = PlanReason.MANUAL_CHECK) {
        val useCase = PlanIntakesUseCase(
            profiles = repositories.profileStore,
            medications = repositories.medicationStore,
            intakes = repositories.intakeStore,
            time = clock.time,
            log = log,
            policy = IntakeGenerationPolicy(windowDepthDays = depthDays),
        )
        runBlocking { useCase(scope, reason) }
    }

    fun planMedication(name: String, reason: PlanReason) {
        plan(PlanScope.Medication(medications.get(name).id), reason)
    }

    // --- Действия: отметка приёма --------------------------------------------------------------

    /**
     * Отметка приёма через единственную точку входа домена.
     *
     * Отказ домена не роняет шаг: проигравшая отметка — штатный ход событий, и сценарий
     * проверяет именно то, что человеку при этом ничего не показали.
     */
    fun mark(intakeId: String, result: MarkResult, source: IntakeSource, actedAt: Instant): MarkOutcome {
        val useCase = MarkIntakeUseCase(
            repositories.intakeStore,
            repositories.medicationStore,
            repositories.profileStore,
            clock.time,
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
