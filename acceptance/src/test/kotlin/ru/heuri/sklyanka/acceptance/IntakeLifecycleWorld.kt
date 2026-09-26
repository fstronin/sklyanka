package ru.heuri.sklyanka.acceptance

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import ru.heuri.sklyanka.domain.intake.CreatePrnIntakeUseCase
import ru.heuri.sklyanka.domain.intake.EndOfDayIntakeWindow
import ru.heuri.sklyanka.domain.intake.IntakeTransition
import ru.heuri.sklyanka.domain.intake.IntakeWindow
import ru.heuri.sklyanka.domain.intake.MarkCommand
import ru.heuri.sklyanka.domain.intake.MarkDecision
import ru.heuri.sklyanka.domain.intake.MarkIntakeUseCase
import ru.heuri.sklyanka.domain.intake.MarkOutcome
import ru.heuri.sklyanka.domain.intake.MarkRequest
import ru.heuri.sklyanka.domain.intake.MarkResult
import ru.heuri.sklyanka.domain.intake.PrnCommand
import ru.heuri.sklyanka.domain.intake.PrnDailyLimit
import ru.heuri.sklyanka.domain.intake.PrnOutcome
import ru.heuri.sklyanka.domain.intake.StockEffect
import ru.heuri.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.heuri.sklyanka.domain.intake.SweepReason
import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.log.OpLogRecord
import ru.heuri.sklyanka.domain.log.OperationLog
import ru.heuri.sklyanka.domain.model.ArchiveReason
import ru.heuri.sklyanka.domain.model.FoodRelation
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.model.MedicationForm
import ru.heuri.sklyanka.domain.model.Profile
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.repository.IntakeRepository
import ru.heuri.sklyanka.domain.repository.MedicationRepository
import ru.heuri.sklyanka.domain.repository.ProfileRepository
import ru.heuri.sklyanka.domain.schedule.IntakePlanDiff
import ru.heuri.sklyanka.domain.schedule.PlanApplyResult
import ru.heuri.sklyanka.domain.stock.StockLedger
import ru.heuri.sklyanka.domain.time.FixedTimeSource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Состояние сценария приёмки SKL-07 — жизненный цикл приёма.
 *
 * Хранилище — в памяти, а не Room: сценарии задачи говорят о состоянии приёма и о правилах
 * перехода, то есть о домене. Неразрывность транзакции проверяется инструментальным тестом
 * `core-data`, эмулятора для него в сборочном контейнере нет, и тащить базу сюда значило бы
 * не проверить ни того, ни другого.
 *
 * Прогоняются настоящие доменные сценарии — [MarkIntakeUseCase], [CreatePrnIntakeUseCase],
 * [SweepMissedIntakesUseCase]; здесь только хранилище под ними.
 */
@Suppress("TooManyFunctions")
internal class IntakeLifecycleWorld {

    val zone: ZoneId = ZoneId.of("Europe/Moscow")
    val clock = FixedTimeSource(DEFAULT_MOMENT.atZone(ZoneId.of("Europe/Moscow")).toInstant(), zone)

    /** Граница окна приёма: значение приходит из Предыстории сценария, а не из кода (ОВ-4). */
    var window: IntakeWindow = EndOfDayIntakeWindow()

    private val profilesByName = linkedMapOf<String, Profile>()
    private val medicationsByName = linkedMapOf<String, Medication>()
    private val intakesById = linkedMapOf<String, Intake>()
    private val pressesOnWatch = mutableListOf<WatchPress>()

    private var sequence = 0
    private var remembered = false

    /** Профиль, открытый на телефоне. Отметка с часов его не меняет (ПРФ-22). */
    var activeProfile: String = SELF_PROFILE
        private set

    /** Лекарство, о котором шла речь последним: шаги без имени говорят о нём. */
    var lastMedication: String = ""
        private set

    /** Приём, о котором шла речь последней: шаги «приём считается…» говорят о нём. */
    var currentIntakeId: String? = null
        private set

    /**
     * Был ли в сценарии шаг «Когда».
     *
     * Строка «остаток упаковки «Л» — 30 таблеток» в Предыстории задаёт остаток, а та же
     * строка после действия его проверяет. Ключевое слово Gherkin до шага не доходит,
     * поэтому различает их порядок: до действия — задание, после — проверка.
     */
    var acted: Boolean = false
        private set

    /** Всё, что домен вернул наружу как повод что-то показать человеку. */
    val messagesToHuman: MutableList<String> = mutableListOf()

    val outcomes: MutableList<MarkOutcome> = mutableListOf()

    /**
     * Приём в том виде, в каком его записала отметка.
     *
     * Нужен проверке «момент отметки остаётся тем же моментом»: шаг перелёта принадлежит
     * приёмке SKL-06 и снимка состояния в этом мире не делает, поэтому сверяться нужно
     * с тем, что записала сама отметка.
     */
    val appliedMarks: MutableMap<String, Intake> = linkedMapOf()
    var lastPrnOutcome: PrnOutcome? = null
        private set

    val log = CollectingIntakeLog()

    /**
     * Лекарства сценария как репозиторий.
     *
     * Остаток и порог предупреждения — поля лекарства, и сценарии SKL-12 читают их
     * отсюда же, где их ставят шаги SKL-07. Второе хранилище лекарств означало бы, что
     * приёмка проверяет два разных состояния и ни одно из них не совпадает с боевым.
     */
    val medicationStore: MedicationRepository = Medications()

    /** Состояние приёмов до первого действия сценария: с ним сверяются «остаётся», «не изменяется». */
    var before: Map<String, Intake> = emptyMap()
        private set

    init {
        // Предыстория каждого сценария SKL-07 начинается со строки «активен профиль «Я»»,
        // и этот шаг принадлежит приёмке SKL-06 — переопределить его нельзя, иначе
        // определения шагов станут неоднозначными. Владелец телефона заводится здесь.
        profile(SELF_PROFILE)
    }

    // --- Время -----------------------------------------------------------------------------

    fun today(): LocalDate = clock.today()

    fun moveTo(moment: LocalDateTime) {
        clock.advanceTo(moment.atZone(clock.zone()).toInstant())
    }

    fun momentOf(moment: LocalDateTime): Instant = moment.atZone(clock.zone()).toInstant()

    // --- Профили и лекарства ------------------------------------------------------------------

    /**
     * Профиль по имени; неизвестный заводится на месте.
     *
     * Владелец телефона — профиль с именем из Предыстории; остальные профили чужие,
     * и отметка в них подписывается владельцем (ПР-12).
     */
    fun profile(name: String): Profile = profilesByName.getOrPut(name) {
        Profile(
            id = "profile-${profilesByName.size + 1}",
            name = name,
            color = 0,
            isSelf = name == SELF_PROFILE,
            mirrorReminders = name != SELF_PROFILE,
        )
    }

    /**
     * Лекарство по имени; неизвестное заводится с безобидными значениями.
     *
     * Часть строк «у профиля … есть лекарство …» принадлежит приёмке SKL-06 и в этот мир
     * не попадает, поэтому лекарство обязано появляться по первому упоминанию в приёме.
     * Учёт остатка по умолчанию выключен: сценарий, которому остаток важен, задаёт его сам.
     */
    fun medication(name: String): Medication = medicationsByName.getOrPut(name) {
        buildMedication(profileName = activeProfile, times = emptyList())
    }

    fun hasMedication(name: String): Boolean = medicationsByName.containsKey(name)

    fun addMedication(
        profileName: String,
        name: String,
        times: List<LocalTime> = emptyList(),
        mode: ScheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay: Int? = null,
    ) {
        medicationsByName[name] = buildMedication(profileName, times, mode, maxPerDay)
        lastMedication = name
    }

    fun update(name: String, change: (Medication) -> Medication) {
        medicationsByName[name] = change(medication(name))
        lastMedication = name
    }

    fun stockOf(name: String): BigDecimal? = medication(name).stockLeft

    // --- Приёмы -------------------------------------------------------------------------------

    fun intakesOf(name: String): List<Intake> {
        val medicationId = medication(name).id
        return intakesById.values.filter { it.medicationId == medicationId }.sortedBy { it.scheduledAt }
    }

    fun intakesOfProfile(profileName: String): List<Intake> {
        val profileId = profile(profileName).id
        val owned = medicationsByName.values.filter { it.profileId == profileId }.map { it.id }.toSet()
        return intakesById.values.filter { it.medicationId in owned }.sortedBy { it.scheduledAt }
    }

    fun current(): Intake {
        val id = requireNotNull(currentIntakeId) { "the scenario never mentioned an intake" }
        return requireNotNull(intakesById[id]) { "intake $id disappeared" }
    }

    fun intakeAt(name: String, at: LocalDateTime): Intake =
        requireNotNull(intakesOf(name).firstOrNull { it.scheduledAt == at }) { "no intake of $name at $at" }

    /** Ожидающийся приём: существующий или созданный на месте — так его рождает SKL-06. */
    fun pending(name: String, at: LocalDateTime): Intake {
        val existing = intakesOf(name).firstOrNull { it.scheduledAt == at }
        val intake = existing ?: Intake(
            id = "intake-${sequence++}",
            medicationId = medication(name).id,
            scheduledAt = at,
            status = IntakeStatus.PENDING,
            actedAt = null,
            source = null,
            actorProfileId = null,
        ).also { intakesById[it.id] = it }
        lastMedication = name
        currentIntakeId = intake.id
        return intake
    }

    /**
     * Приём, о котором говорит шаг, не назвавший ни лекарства, ни времени.
     *
     * Если сценарий его ещё не создавал, он создаётся по ближайшему времени расписания
     * текущего лекарства: сценарий говорит о приёме, который у человека действительно есть.
     */
    fun currentOrScheduled(): Intake {
        currentIntakeId?.let { return current() }
        val name = lastMedication.ifEmpty { medicationsByName.keys.first() }
        val at = medication(name).times.minOrNull() ?: clock.nowLocal().toLocalTime()
        return pending(name, today().atTime(at))
    }

    fun focusOn(intake: Intake) {
        currentIntakeId = intake.id
    }

    // --- Действия --------------------------------------------------------------------------------

    /**
     * Отметка приёма через единственную точку входа домена.
     *
     * Отказ домена не роняет шаг: проигравшая отметка — штатный ход событий, и сценарий
     * проверяет именно то, что человеку при этом ничего не показали.
     */
    fun mark(intakeId: String, result: MarkResult, source: IntakeSource, actedAt: Instant): MarkOutcome {
        val useCase = MarkIntakeUseCase(Intakes(), Medications(), Profiles(), clock)
        val outcome = runCatching {
            runBlocking { useCase(MarkRequest(intakeId, result, source, actedAt)) }
        }.getOrElse { failure ->
            messagesToHuman += failure.message.orEmpty()
            MarkOutcome.NotFound
        }
        outcomes += outcome
        when (outcome) {
            is MarkOutcome.Applied -> appliedMarks[outcome.intake.id] = outcome.intake
            is MarkOutcome.Superseded -> appliedMarks[outcome.intake.id] = outcome.intake
            else -> Unit
        }
        return outcome
    }

    fun markCurrent(result: MarkResult, source: IntakeSource, at: LocalDateTime): MarkOutcome {
        val intake = currentOrScheduled()
        moveTo(at)
        return mark(intake.id, result, source, momentOf(at))
    }

    /** Нажатие на часах: действие произошло сейчас, а дойдёт до телефона когда дойдёт (ЧАС-23). */
    fun pressOnWatch(result: MarkResult, at: LocalDateTime) {
        val intake = currentOrScheduled()
        pressesOnWatch += WatchPress(intake.id, result, momentOf(at), momentOf(at))
    }

    fun delayLastPress(to: LocalDateTime) {
        pressesOnWatch.lastOrNull()?.deliverAt = momentOf(to)
    }

    /** Доставка нажатий, дошедших до телефона к указанному моменту, в порядке доставки. */
    fun deliverPressesUntil(moment: Instant) {
        val due = pressesOnWatch.filter { !it.deliverAt.isAfter(moment) }.sortedBy { it.deliverAt }
        pressesOnWatch.removeAll(due)
        due.forEach { press -> mark(press.intakeId, press.result, IntakeSource.WATCH, press.actedAt) }
    }

    /** Остальные нажатия доходят позже действия на телефоне — и решают гонку по времени действия. */
    fun deliverRemainingPresses() {
        val rest = pressesOnWatch.sortedBy { it.deliverAt }
        pressesOnWatch.clear()
        rest.forEach { press -> mark(press.intakeId, press.result, IntakeSource.WATCH, press.actedAt) }
    }

    fun pressTakeOnDemand(name: String): PrnOutcome {
        val useCase = CreatePrnIntakeUseCase(Intakes(), Medications(), Profiles(), clock)
        lastMedication = name
        val outcome = runCatching {
            runBlocking { useCase(medication(name).id, IntakeSource.PHONE) }
        }.getOrElse { failure ->
            messagesToHuman += failure.message.orEmpty()
            PrnOutcome.UnknownMedication
        }
        lastPrnOutcome = outcome
        if (outcome is PrnOutcome.Created) currentIntakeId = outcome.intake.id
        return outcome
    }

    /** Проход по просроченным приёмам: и по таймеру, и при открытии приложения (НАП-34). */
    fun sweep(reason: SweepReason = SweepReason.TIMER) {
        val useCase = SweepMissedIntakesUseCase(Intakes(), window, clock, log)
        runBlocking { useCase(reason) }
    }

    /**
     * Снимок состояния перед первым действием сценария.
     *
     * Берётся один раз: шаги «Когда» бывают парой, и сверяться нужно с состоянием
     * до первого из них.
     */
    fun rememberState() {
        acted = true
        if (remembered) return
        before = intakesById.toMap()
        remembered = true
    }

    private fun buildMedication(
        profileName: String,
        times: List<LocalTime>,
        mode: ScheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay: Int? = null,
    ): Medication {
        val owner = profile(profileName)
        return Medication(
            id = "medication-${medicationsByName.size + 1}",
            profileId = owner.id,
            // Названий лекарств в модели сценария нет: в журнал и в отчёты попадают
            // только идентификаторы (НФТ-19), а имя из Gherkin — ключ сценария.
            name = "med-${medicationsByName.size + 1}",
            form = MedicationForm.TABLET,
            shape = null,
            doseAmount = BigDecimal.ONE,
            doseUnit = "unit",
            strength = null,
            strengthUnit = null,
            foodRelation = FoodRelation.ANY,
            scheduleMode = mode,
            maxPerDay = maxPerDay,
            times = times,
            startDate = START_DATE,
            endDate = null,
            stockLeft = null,
            stockThresholdDays = null,
            archivedAt = null,
            archiveReason = null,
        )
    }

    // --- Репозитории поверх состояния сценария -----------------------------------------------------

    private inner class Profiles : ProfileRepository {
        override fun observeAll(): Flow<List<Profile>> = flowOf(profilesByName.values.toList())
        override suspend fun get(profileId: String): Profile? = profilesByName.values.firstOrNull { it.id == profileId }
        override suspend fun getSelf(): Profile? = profilesByName.values.firstOrNull { it.isSelf }
        override suspend fun save(profile: Profile) = Unit
    }

    private inner class Medications : MedicationRepository {
        override fun observeActive(profileId: String): Flow<List<Medication>> =
            flowOf(medicationsByName.values.filter { it.profileId == profileId && !it.isArchived })

        override fun observeArchived(profileId: String): Flow<List<Medication>> = flowOf(emptyList())

        override suspend fun get(medicationId: String): Medication? =
            medicationsByName.values.firstOrNull { it.id == medicationId }

        override suspend fun save(medication: Medication) = Unit
        override suspend fun archive(medicationId: String, reason: ArchiveReason): Boolean = false

        override suspend fun setStock(medicationId: String, stockLeft: BigDecimal?) {
            val name = medicationsByName.entries.firstOrNull { it.value.id == medicationId }?.key ?: return
            medicationsByName[name] = medicationsByName.getValue(name).copy(stockLeft = stockLeft)
        }
    }

    /**
     * Хранилище приёмов в памяти.
     *
     * Повторяет ту же последовательность, что транзакция Room: прочитать, решить правилом
     * домена, записать, привести остаток. Правило здесь одно и то же с боевым кодом —
     * [IntakeTransition]; расходиться им нельзя, иначе приёмка проверяет копию правила.
     */
    private inner class Intakes : IntakeRepository {
        override fun observeDay(profileId: String, date: LocalDate): Flow<List<Intake>> = flowOf(emptyList())

        override fun observePeriod(profileId: String, from: LocalDate, to: LocalDate): Flow<List<Intake>> =
            flowOf(emptyList())

        override fun observeByMedication(
            profileId: String,
            medicationId: String,
            from: LocalDate,
            to: LocalDate,
        ): Flow<List<Intake>> = flowOf(emptyList())

        override suspend fun get(intakeId: String): Intake? = intakesById[intakeId]

        override suspend fun insertMissing(intakes: List<Intake>): Int = 0

        override suspend fun scheduledBetween(
            profileId: String,
            medicationId: String,
            from: LocalDateTime,
            toExclusive: LocalDateTime,
        ): List<Intake> = emptyList()

        override suspend fun applyPlan(
            profileId: String,
            medicationId: String,
            diff: IntakePlanDiff,
        ): PlanApplyResult = PlanApplyResult.NOTHING

        override suspend fun markIfPending(
            intakeId: String,
            status: IntakeStatus,
            source: IntakeSource,
            actorProfileId: String?,
        ): Boolean = false

        override suspend fun expiredPending(before: LocalDateTime): List<Intake> =
            intakesById.values.filter { it.isPending && it.scheduledAt < before }

        override suspend fun markAtomically(command: MarkCommand): MarkOutcome {
            val current = intakesById[command.intakeId] ?: return MarkOutcome.NotFound
            return when (val decision = IntakeTransition.decide(current, command)) {
                is MarkDecision.Keep -> MarkOutcome.Ignored(decision.intake, decision.reason)
                is MarkDecision.Write -> {
                    intakesById[command.intakeId] = decision.intake
                    applyStock(decision.intake.medicationId, decision.stock)
                    if (decision.superseded) {
                        MarkOutcome.Superseded(decision.intake, current)
                    } else {
                        MarkOutcome.Applied(decision.intake)
                    }
                }
            }
        }

        override suspend fun createPrnAtomically(command: PrnCommand): PrnOutcome {
            val medication = medicationById(command.medicationId) ?: return PrnOutcome.UnknownMedication
            val day = command.scheduledAt.toLocalDate()
            val countToday = intakesById.values
                .count { it.medicationId == medication.id && it.scheduledAt.toLocalDate() == day }
            val limit = medication.maxPerDay
            return if (PrnDailyLimit.reached(limit, countToday)) {
                PrnOutcome.RejectedDailyLimit(limit = requireNotNull(limit), countToday = countToday)
            } else {
                val intake = Intake(
                    id = "intake-${sequence++}",
                    medicationId = medication.id,
                    scheduledAt = command.scheduledAt,
                    status = IntakeStatus.TAKEN,
                    actedAt = command.actedAt,
                    source = command.source,
                    actorProfileId = command.actorProfileId,
                )
                intakesById[intake.id] = intake
                applyStock(medication.id, StockEffect.DECREMENT)
                PrnOutcome.Created(intake)
            }
        }

        override suspend fun markMissed(ids: List<String>, at: Instant): Int = ids.count { id ->
            val intake = intakesById[id]
            if (intake == null || !intake.isPending) {
                false
            } else {
                intakesById[id] = IntakeTransition.expire(intake, at)
                true
            }
        }

        private fun medicationById(medicationId: String): Medication? =
            medicationsByName.values.firstOrNull { it.id == medicationId }

        private fun applyStock(medicationId: String, effect: StockEffect) {
            val medication = medicationById(medicationId) ?: return
            // Правило списания — доменное и одно на всё приложение (SKL-12): копия
            // арифметики здесь означала бы, что приёмка проверяет копию правила.
            val change = StockLedger.apply(medication.stockLeft, medication.doseAmount, effect) ?: return
            val name = medicationsByName.entries.first { it.value.id == medicationId }.key
            medicationsByName[name] = medication.copy(stockLeft = change.after)
        }
    }

    private companion object {
        /**
         * Момент из Предыстории сценария: 25 сентября 2026, 07:55.
         *
         * Продублирован здесь потому, что шаг «сейчас …» принадлежит приёмке SKL-06 и
         * второй раз объявлен быть не может. Расхождение с Предысторией не проходит молча:
         * сценарии с относительным временем («в 08:03») тогда падают.
         */
        val DEFAULT_MOMENT: LocalDateTime = LocalDateTime.of(2026, 9, 25, 7, 55)
        val START_DATE: LocalDate = LocalDate.of(2026, 9, 1)
        const val SELF_PROFILE = "Я"
    }
}

/** Нажатие на часах: момент действия и момент доставки — разные вещи (ПР-3, ЧАС-23). */
internal data class WatchPress(
    val intakeId: String,
    val result: MarkResult,
    val actedAt: Instant,
    var deliverAt: Instant,
)

/** Журнал сценария: по нему проверяется, что автоматический пропуск ничего не показывает человеку. */
internal class CollectingIntakeLog : OperationLog {

    val events: MutableList<OpEvent> = mutableListOf()

    @Suppress("LongParameterList")
    override suspend fun record(
        event: OpEvent,
        profileId: String?,
        medicationId: String?,
        intakeId: String?,
        before: String?,
        after: String?,
        source: String?,
        detail: String?,
    ) {
        events += event
    }

    override suspend fun recent(limit: Int): List<OpLogRecord> = emptyList()

    override suspend fun prune(keepDays: Long, keepRecords: Int) = Unit
}
