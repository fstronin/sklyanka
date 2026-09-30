package ru.stohastika.sklyanka.feature.today

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestCoroutineScheduler
import ru.stohastika.sklyanka.domain.Transactor
import ru.stohastika.sklyanka.domain.intake.CreatePrnIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.EndOfDayIntakeWindow
import ru.stohastika.sklyanka.domain.intake.IntakeTransition
import ru.stohastika.sklyanka.domain.intake.MarkCommand
import ru.stohastika.sklyanka.domain.intake.MarkDecision
import ru.stohastika.sklyanka.domain.intake.MarkIntakeUseCase
import ru.stohastika.sklyanka.domain.intake.MarkOutcome
import ru.stohastika.sklyanka.domain.intake.PrnCommand
import ru.stohastika.sklyanka.domain.intake.PrnDailyLimit
import ru.stohastika.sklyanka.domain.intake.PrnOutcome
import ru.stohastika.sklyanka.domain.intake.StockEffect
import ru.stohastika.sklyanka.domain.intake.SweepMissedIntakesUseCase
import ru.stohastika.sklyanka.domain.intake.UndoCommand
import ru.stohastika.sklyanka.domain.intake.UndoDecision
import ru.stohastika.sklyanka.domain.intake.UndoMarkUseCase
import ru.stohastika.sklyanka.domain.intake.UndoOutcome
import ru.stohastika.sklyanka.domain.intake.UndoableMarkStore
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OpLogRecord
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.reminder.ReadReminderReliabilityUseCase
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.PermissionStateReader
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.domain.schedule.IntakePlanDiff
import ru.stohastika.sklyanka.domain.schedule.PlanApplyResult
import ru.stohastika.sklyanka.domain.stock.StockWarningForProfileUseCase
import ru.stohastika.sklyanka.domain.time.TimeSource
import ru.stohastika.sklyanka.domain.today.BuildTodayAgenda
import ru.stohastika.sklyanka.domain.today.DenseLayoutRule
import ru.stohastika.sklyanka.domain.today.MarkIntakeGroupUseCase
import ru.stohastika.sklyanka.domain.today.ObserveTodayAgendaUseCase
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Обвязка тестов экрана «Сегодня»: хранилище в памяти и часы виртуального времени.
 *
 * Сценарии отметки гоняются настоящие — [MarkIntakeUseCase], [MarkIntakeGroupUseCase],
 * [CreatePrnIntakeUseCase]: подменять их заглушками означало бы проверить, что
 * `ViewModel` вызывает то, что ей велено, и не проверить, что от этого меняется на
 * экране. Хранилище под ними — в памяти: неразрывность транзакции проверяется
 * инструментальным тестом `core-data`, эмулятора для которого в среде задачи нет.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TodayTestWorld(scheduler: TestCoroutineScheduler, startAt: LocalDateTime = TodayTest.at(7, 30)) {

    val clock = VirtualTimeSource(scheduler, TodayTest.moment(startAt), TodayTest.ZONE)
    val profiles = InMemoryProfileRepository()
    val medications = InMemoryMedicationRepository()
    val intakes = InMemoryIntakeRepository(clock, medications)
    val activeProfile = InMemoryActiveProfileRepository(TodayTest.PROFILE)
    val log = RecordingLog()

    /**
     * Разрешения «системы» теста: по умолчанию всё выдано.
     *
     * Сценарий, которому нужна ненадёжность, меняет карту до или между сборками
     * состояния — `ReadReminderReliabilityUseCase` читает её при каждом вызове,
     * а не запоминает флаг (НФТ-52).
     */
    private val permissions = mutableMapOf<ReminderPermission, PermissionState>()

    private val permissionReader = object : PermissionStateReader {
        override fun state(permission: ReminderPermission): PermissionState =
            permissions[permission] ?: PermissionState.GRANTED
    }

    fun denyPermissions(vararg denied: ReminderPermission) {
        denied.forEach { permissions[it] = PermissionState.DENIED }
    }

    /** Выдача разрешения мимо приложения: человек вернулся из системных настроек (SKL-48). */
    fun grantPermissions(vararg granted: ReminderPermission) {
        granted.forEach { permissions[it] = PermissionState.GRANTED }
    }

    val markIntake = MarkIntakeUseCase(intakes, medications, profiles, clock)
    val undoMark = UndoMarkUseCase(intakes, medications, profiles)
    val undoMarks = UndoableMarkStore(clock)

    init {
        profiles.put(
            Profile(id = TodayTest.PROFILE, name = "self", color = 0, isSelf = true, mirrorReminders = false),
        )
    }

    fun viewModel(denseThreshold: Int = DenseLayoutRule.DEFAULT_THRESHOLD) = TodayViewModel(
        observeAgenda = ObserveTodayAgendaUseCase(
            activeProfile = activeProfile,
            intakes = intakes,
            medications = medications,
            sweepMissed = SweepMissedIntakesUseCase(intakes, EndOfDayIntakeWindow(), clock, log),
            stockWarnings = StockWarningForProfileUseCase(medications, clock, log),
            time = clock,
            build = BuildTodayAgenda(DenseLayoutRule(denseThreshold)),
        ),
        markIntake = markIntake,
        markGroup = MarkIntakeGroupUseCase(markIntake, DirectTransactor, clock, log),
        createPrn = CreatePrnIntakeUseCase(intakes, medications, profiles, clock),
        undoMark = undoMark,
        undoMarks = undoMarks,
        time = clock,
        readReliability = ReadReminderReliabilityUseCase(permissionReader),
    )
}

/**
 * Часы, идущие вместе с виртуальным временем теста.
 *
 * Без них поток суток, ждущий местной полуночи, крутился бы вхолостую: виртуальное
 * время шло бы вперёд, а «сегодня» оставалось прежним (НФТ-48).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class VirtualTimeSource(
    private val scheduler: TestCoroutineScheduler,
    private val start: Instant,
    private val zone: ZoneId,
) : TimeSource {

    override fun now(): Instant = start.plusMillis(scheduler.currentTime)

    override fun zone(): ZoneId = zone
}

/** Приёмы в памяти: наблюдаемые сутки и те же правила перехода, что в Room. */
internal class InMemoryIntakeRepository(
    private val time: TimeSource,
    private val medications: InMemoryMedicationRepository,
) : IntakeRepository {

    val stored: MutableMap<String, Intake> = linkedMapOf()
    private val revision = MutableStateFlow(0)
    private var sequence = 0

    fun put(intake: Intake) {
        stored[intake.id] = intake
        revision.value++
    }

    override fun observeDay(profileId: String, date: LocalDate): Flow<List<Intake>> = revision.map {
        stored.values
            .filter { it.scheduledAt.toLocalDate() == date && medications.profileOf(it.medicationId) == profileId }
            .sortedBy { it.scheduledAt }
    }

    override fun observePeriod(profileId: String, from: LocalDate, to: LocalDate): Flow<List<Intake>> =
        flowOf(emptyList())

    override fun observeByMedication(
        profileId: String,
        medicationId: String,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<Intake>> = flowOf(emptyList())

    override suspend fun get(intakeId: String): Intake? = stored[intakeId]

    override suspend fun insertMissing(intakes: List<Intake>): Int = 0

    override suspend fun scheduledBetween(
        profileId: String,
        medicationId: String,
        from: LocalDateTime,
        toExclusive: LocalDateTime,
    ): List<Intake> = emptyList()

    override suspend fun applyPlan(profileId: String, medicationId: String, diff: IntakePlanDiff): PlanApplyResult =
        PlanApplyResult(created = 0, deleted = 0)

    override suspend fun markIfPending(
        intakeId: String,
        status: IntakeStatus,
        source: IntakeSource,
        actorProfileId: String?,
    ): Boolean {
        val intake = stored[intakeId]
        return if (intake == null || !intake.isPending) {
            false
        } else {
            put(intake.copy(status = status, actedAt = time.now(), source = source, actorProfileId = actorProfileId))
            true
        }
    }

    override suspend fun expiredPending(before: LocalDateTime): List<Intake> =
        stored.values.filter { it.isPending && it.scheduledAt < before }

    override suspend fun markAtomically(command: MarkCommand): MarkOutcome {
        val current = stored[command.intakeId] ?: return MarkOutcome.NotFound
        return when (val decision = IntakeTransition.decide(current, command)) {
            is MarkDecision.Keep -> MarkOutcome.Ignored(decision.intake, decision.reason)
            is MarkDecision.Write -> {
                put(decision.intake)
                medications.applyStock(decision.intake.medicationId, decision.stock)
                if (decision.superseded) {
                    MarkOutcome.Superseded(decision.intake, current)
                } else {
                    MarkOutcome.Applied(decision.intake)
                }
            }
        }
    }

    override suspend fun undoAtomically(command: UndoCommand): UndoOutcome {
        val current = command.intakeIds.map { intakeId -> stored[intakeId] ?: return UndoOutcome.NotFound }
        val medicationsById = current.map { it.medicationId }.distinct().associateWith { medicationId ->
            requireNotNull(medications.stored[medicationId]) {
                "Undo refers to a medication that does not exist: $medicationId"
            }
        }
        require(medicationsById.values.map { it.profileId }.distinct().size == 1) {
            "Undo command spans several profiles"
        }
        require(medicationsById.values.all { it.scheduleMode == ScheduleMode.SCHEDULED }) {
            "Undo addresses an on-demand intake"
        }
        var undone = 0
        current.forEach { intake ->
            when (val decision = IntakeTransition.undo(intake)) {
                is UndoDecision.Keep -> Unit
                is UndoDecision.Revert -> {
                    put(decision.intake)
                    medications.applyStock(intake.medicationId, decision.stock)
                    undone++
                }
            }
        }
        return UndoOutcome.Applied(undone)
    }

    override suspend fun createPrnAtomically(command: PrnCommand): PrnOutcome {
        val medication = medications.stored[command.medicationId]
        val day = command.scheduledAt.toLocalDate()
        val countToday = stored.values.count {
            it.medicationId == command.medicationId && it.scheduledAt.toLocalDate() == day
        }
        return when {
            medication == null -> PrnOutcome.UnknownMedication
            PrnDailyLimit.reached(medication.maxPerDay, countToday) ->
                PrnOutcome.RejectedDailyLimit(requireNotNull(medication.maxPerDay), countToday)

            else -> PrnOutcome.Created(createTaken(command))
        }
    }

    private fun createTaken(command: PrnCommand): Intake {
        val intake = Intake(
            id = "prn-${sequence++}",
            medicationId = command.medicationId,
            scheduledAt = command.scheduledAt,
            status = IntakeStatus.TAKEN,
            actedAt = command.actedAt,
            source = command.source,
            actorProfileId = command.actorProfileId,
        )
        put(intake)
        medications.applyStock(command.medicationId, StockEffect.DECREMENT)
        return intake
    }

    override suspend fun markMissed(ids: List<String>, at: Instant): Int = ids.count { id ->
        val intake = stored[id]
        if (intake == null || !intake.isPending) {
            false
        } else {
            put(IntakeTransition.expire(intake, at))
            true
        }
    }
}

/** Лекарства в памяти. */
internal class InMemoryMedicationRepository : MedicationRepository {

    val stored: MutableMap<String, Medication> = linkedMapOf()
    private val revision = MutableStateFlow(0)

    fun put(medication: Medication) {
        stored[medication.id] = medication
        revision.value++
    }

    fun profileOf(medicationId: String): String? = stored[medicationId]?.profileId

    fun applyStock(medicationId: String, effect: StockEffect) {
        val medication = stored[medicationId]
        val stock = medication?.stockLeft
        if (medication == null || stock == null || effect == StockEffect.NONE) return
        val next = when (effect) {
            StockEffect.DECREMENT -> (stock - medication.doseAmount).max(BigDecimal.ZERO)
            StockEffect.RESTORE -> stock + medication.doseAmount
            StockEffect.NONE -> stock
        }
        put(medication.copy(stockLeft = next))
    }

    override fun observeActive(profileId: String): Flow<List<Medication>> = revision.map {
        stored.values.filter { it.profileId == profileId && !it.isArchived }
    }

    override fun observeArchived(profileId: String): Flow<List<Medication>> = flowOf(emptyList())

    override suspend fun get(medicationId: String): Medication? = stored[medicationId]

    override suspend fun save(medication: Medication) = put(medication)

    override suspend fun archive(medicationId: String, reason: ArchiveReason): Boolean = false

    override suspend fun setStock(medicationId: String, stockLeft: BigDecimal?) {
        stored[medicationId]?.let { put(it.copy(stockLeft = stockLeft)) }
    }
}

/** Профили в памяти. */
internal class InMemoryProfileRepository : ProfileRepository {

    private val stored: MutableMap<String, Profile> = linkedMapOf()

    fun put(profile: Profile) {
        stored[profile.id] = profile
    }

    override fun observeAll(): Flow<List<Profile>> = flowOf(stored.values.toList())

    override suspend fun get(profileId: String): Profile? = stored[profileId]

    override suspend fun getSelf(): Profile? = stored.values.firstOrNull { it.isSelf }

    override suspend fun save(profile: Profile) = put(profile)
}

/** Активный профиль, который можно переключить прямо в тесте. */
internal class InMemoryActiveProfileRepository(initial: String) : ActiveProfileRepository {

    private val state = MutableStateFlow(initial)

    override val activeProfileId: Flow<String> = state

    override suspend fun current(): String = state.value

    override suspend fun switchTo(profileId: String) {
        state.value = profileId
    }
}

/** Транзакция без хранилища: проверяется состояние экрана, а не неразрывность записи. */
internal object DirectTransactor : Transactor {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
}

/** Журнал в памяти: по нему видно, что след операции остался. */
internal class RecordingLog : OperationLog {

    val records: MutableList<OpLogRecord> = mutableListOf()

    fun events(): List<OpEvent> = records.map { it.event }

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
        records += OpLogRecord(
            id = records.size.toLong(),
            at = Instant.EPOCH,
            event = event,
            profileId = profileId,
            medicationId = medicationId,
            intakeId = intakeId,
            before = before,
            after = after,
            source = source,
            detail = detail,
        )
    }

    override suspend fun recent(limit: Int): List<OpLogRecord> = records.takeLast(limit)

    override suspend fun prune(keepDays: Long, keepRecords: Int) = Unit
}
