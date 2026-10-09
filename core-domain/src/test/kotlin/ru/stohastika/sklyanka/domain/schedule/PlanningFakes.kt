package ru.stohastika.sklyanka.domain.schedule

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.stohastika.sklyanka.domain.intake.IntakeTransition
import ru.stohastika.sklyanka.domain.intake.MarkCommand
import ru.stohastika.sklyanka.domain.intake.MarkDecision
import ru.stohastika.sklyanka.domain.intake.MarkOutcome
import ru.stohastika.sklyanka.domain.intake.PrnCommand
import ru.stohastika.sklyanka.domain.intake.PrnDailyLimit
import ru.stohastika.sklyanka.domain.intake.PrnOutcome
import ru.stohastika.sklyanka.domain.intake.RevokeDecision
import ru.stohastika.sklyanka.domain.intake.StockEffect
import ru.stohastika.sklyanka.domain.intake.UndoCommand
import ru.stohastika.sklyanka.domain.intake.UndoDecision
import ru.stohastika.sklyanka.domain.intake.UndoOutcome
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
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Хранилище в памяти вместо Room.
 *
 * Генерация — доменная операция, и проверять её на базе значит проверять Room. Здесь
 * проверяется правило; транзакционность и уникальность проверяются отдельно,
 * инструментальным тестом `core-data` на реальной базе.
 */
internal class FakeIntakeRepository(
    private val time: TimeSource,
    /**
     * Лекарства нужны отметке: доза и остаток живут у них (ПР-5), суточный лимит — тоже
     * (ИНВ-17). Планированию они не нужны, поэтому связь необязательная.
     */
    private val medications: FakeMedicationRepository? = null,
    /**
     * Журнал отмены: в Room след пишет хранилище внутри транзакции (SKL-30), и эта
     * обвязка повторяет ту же последовательность. Необязателен — большинству тестов
     * журнал не нужен.
     */
    private val log: OperationLog? = null,
) : IntakeRepository {

    val stored: MutableMap<String, Intake> = linkedMapOf()

    /** Число обращений к транзакции применения плана: пустой план не открывает её вовсе. */
    var applyCalls: Int = 0
        private set

    private var sequence = 0

    fun put(intake: Intake) {
        stored[intake.id] = intake
    }

    fun of(medicationId: String): List<Intake> =
        stored.values.filter { it.medicationId == medicationId }.sortedBy { it.scheduledAt }

    override fun observeDay(profileId: String, date: LocalDate): Flow<List<Intake>> = flowOf(emptyList())

    override fun observePeriod(profileId: String, from: LocalDate, to: LocalDate): Flow<List<Intake>> =
        flowOf(emptyList())

    override fun observeByMedication(
        profileId: String,
        medicationId: String,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<Intake>> = flowOf(of(medicationId))

    override suspend fun hasTaken(profileId: String, medicationId: String): Boolean =
        stored.values.any { it.medicationId == medicationId && it.status == IntakeStatus.TAKEN }

    override fun observeHasTaken(profileId: String, medicationId: String): Flow<Boolean> =
        flowOf(stored.values.any { it.medicationId == medicationId && it.status == IntakeStatus.TAKEN })

    override suspend fun get(intakeId: String): Intake? = stored[intakeId]

    override suspend fun insertMissing(intakes: List<Intake>): Int {
        val missing = intakes.filterNot { candidate ->
            stored.values.any { it.medicationId == candidate.medicationId && it.scheduledAt == candidate.scheduledAt }
        }
        missing.forEach { put(it) }
        return missing.size
    }

    override suspend fun scheduledBetween(
        profileId: String,
        medicationId: String,
        from: LocalDateTime,
        toExclusive: LocalDateTime,
    ): List<Intake> = of(medicationId).filter { it.scheduledAt >= from && it.scheduledAt < toExclusive }

    override suspend fun applyPlan(profileId: String, medicationId: String, diff: IntakePlanDiff): PlanApplyResult {
        applyCalls++
        // Удаление только ожидающихся — то же условие, что стоит внутри DELETE в SQL:
        // между чтением окна и записью помещается отметка с часов.
        val deleted = diff.toDelete.count { id -> stored[id]?.isPending == true && stored.remove(id) != null }
        val created = diff.toCreate.count { moment ->
            val clash = stored.values.any { it.medicationId == medicationId && it.scheduledAt == moment }
            if (!clash) put(Planning.pending(moment, "generated-${sequence++}", medicationId))
            !clash
        }
        return PlanApplyResult(created = created, deleted = deleted)
    }

    override suspend fun markIfPending(
        intakeId: String,
        status: IntakeStatus,
        source: IntakeSource,
        actorProfileId: String?,
    ): Boolean {
        val intake = stored[intakeId]
        if (intake == null || !intake.isPending) return false
        stored[intakeId] = intake.copy(
            status = status,
            actedAt = time.now(),
            source = source,
            actorProfileId = actorProfileId,
        )
        return true
    }

    override suspend fun expiredPending(before: LocalDateTime): List<Intake> =
        stored.values.filter { it.isPending && it.scheduledAt < before }

    /**
     * Та же последовательность, что и в транзакции Room: прочитать, решить правилом
     * домена, записать, привести остаток. Здесь проверяется правило; неразрывность
     * шагов проверяется инструментальным тестом `core-data` на настоящей базе.
     */
    override suspend fun markAtomically(command: MarkCommand): MarkOutcome {
        val current = stored[command.intakeId] ?: return MarkOutcome.NotFound
        return when (val decision = IntakeTransition.decide(current, command)) {
            is MarkDecision.Keep -> MarkOutcome.Ignored(decision.intake, decision.reason)
            is MarkDecision.Write -> {
                stored[command.intakeId] = decision.intake
                applyStock(decision.intake.medicationId, decision.stock)
                if (decision.superseded) {
                    MarkOutcome.Superseded(decision.intake, current)
                } else {
                    MarkOutcome.Applied(decision.intake)
                }
            }
        }
    }

    /**
     * Та же последовательность, что и в транзакции Room (SKL-30, SKL-127): прочитать
     * действие целиком, проверить режим и профили, применить правило своего режима,
     * вернуть остаток.
     */
    override suspend fun undoAtomically(command: UndoCommand): UndoOutcome {
        val current = command.intakeIds.map { intakeId -> stored[intakeId] ?: return UndoOutcome.NotFound }
        val medicationsById = current.map { it.medicationId }.distinct().associateWith { medicationId ->
            requireNotNull(medications?.stored?.get(medicationId)) {
                "Undo refers to a medication that does not exist: $medicationId"
            }
        }
        val profiles = medicationsById.values.map { it.profileId }.distinct()
        require(profiles.size == 1) { "Undo command spans several profiles: $profiles" }
        val modes = medicationsById.values.map { it.scheduleMode }.distinct()
        require(modes.size == 1) { "Undo command spans several schedule modes: $modes" }
        return when (modes.single()) {
            ScheduleMode.SCHEDULED -> undoMarks(current, command)
            ScheduleMode.PRN -> revokeRecords(current, command, time.now())
        }
    }

    private suspend fun undoMarks(current: List<Intake>, command: UndoCommand): UndoOutcome {
        var undone = 0
        current.forEach { intake ->
            when (val decision = IntakeTransition.undo(intake)) {
                is UndoDecision.Keep -> Unit
                is UndoDecision.Revert -> {
                    stored[intake.id] = decision.intake
                    applyStock(intake.medicationId, decision.stock)
                    log?.record(
                        event = OpEvent.INTAKE_MARK_UNDONE,
                        profileId = command.actorProfileId,
                        medicationId = intake.medicationId,
                        intakeId = intake.id,
                        before = intake.status.name,
                        after = IntakeStatus.PENDING.name,
                        source = command.source.name,
                    )
                    undone++
                }
            }
        }
        return UndoOutcome.Applied(undone)
    }

    private suspend fun revokeRecords(current: List<Intake>, command: UndoCommand, at: Instant): UndoOutcome {
        var revoked = 0
        current.forEach { intake ->
            when (val decision = IntakeTransition.revoke(intake, at)) {
                is RevokeDecision.Keep -> Unit
                is RevokeDecision.Revoked -> {
                    stored[intake.id] = decision.intake
                    applyStock(intake.medicationId, decision.stock)
                    log?.record(
                        event = OpEvent.INTAKE_REVOKED,
                        profileId = command.actorProfileId,
                        medicationId = intake.medicationId,
                        intakeId = intake.id,
                        before = intake.status.name,
                        after = intake.status.name,
                        source = command.source.name,
                    )
                    revoked++
                }
            }
        }
        return UndoOutcome.Applied(revoked)
    }

    override suspend fun createPrnAtomically(command: PrnCommand): PrnOutcome {
        val medication = medications?.stored?.get(command.medicationId) ?: return PrnOutcome.UnknownMedication
        val day = command.scheduledAt.toLocalDate()
        val countToday = of(command.medicationId).count { it.scheduledAt.toLocalDate() == day }
        val limit = medication.maxPerDay
        return if (PrnDailyLimit.reached(limit, countToday)) {
            PrnOutcome.RejectedDailyLimit(limit = requireNotNull(limit), countToday = countToday)
        } else {
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
            applyStock(command.medicationId, StockEffect.DECREMENT)
            PrnOutcome.Created(intake)
        }
    }

    override suspend fun markMissed(ids: List<String>, at: Instant): Int = ids.count { id ->
        val intake = stored[id]
        if (intake == null || !intake.isPending) {
            false
        } else {
            stored[id] = IntakeTransition.expire(intake, at)
            true
        }
    }

    /** Остаток ниже нуля не уходит (INV-8); лекарство без учёта остатка правилу не подчиняется (ПР-5). */
    private fun applyStock(medicationId: String, effect: StockEffect) {
        val medication = medications?.stored?.get(medicationId)
        val stock = medication?.stockLeft
        if (effect == StockEffect.NONE || medication == null || stock == null) return
        val next = when (effect) {
            StockEffect.DECREMENT -> (stock - medication.doseAmount).max(BigDecimal.ZERO)
            StockEffect.RESTORE -> stock + medication.doseAmount
            StockEffect.NONE -> stock
        }
        medications?.put(medication.copy(stockLeft = next))
    }
}

/** Лекарства в памяти. */
internal class FakeMedicationRepository(private val time: TimeSource) : MedicationRepository {

    val stored: MutableMap<String, Medication> = linkedMapOf()

    fun put(medication: Medication) {
        stored[medication.id] = medication
    }

    override fun observeActive(profileId: String): Flow<List<Medication>> =
        flowOf(stored.values.filter { it.profileId == profileId && !it.isArchived && !it.isDeleted })

    override fun observeArchived(profileId: String): Flow<List<Medication>> =
        flowOf(stored.values.filter { it.profileId == profileId && it.isArchived && !it.isDeleted })

    override suspend fun get(medicationId: String): Medication? = stored[medicationId]

    override suspend fun save(medication: Medication) = put(medication)

    override suspend fun archive(medicationId: String, reason: ArchiveReason): Boolean {
        val medication = stored[medicationId]
        if (medication == null || medication.isArchived || medication.isDeleted) return false
        put(medication.copy(archivedAt = time.now(), archiveReason = reason))
        return true
    }

    override suspend fun delete(medicationId: String): Boolean {
        val medication = stored[medicationId]
        if (medication == null || medication.isDeleted) return false
        put(medication.copy(deletedAt = time.now(), archivedAt = null, archiveReason = null))
        return true
    }

    override suspend fun undelete(medicationId: String): Boolean {
        val medication = stored[medicationId]
        if (medication == null || !medication.isDeleted) return false
        put(medication.copy(deletedAt = null))
        return true
    }

    override suspend fun setStock(medicationId: String, stockLeft: BigDecimal?) {
        stored[medicationId]?.let { put(it.copy(stockLeft = stockLeft)) }
    }
}

/** Профили в памяти. */
internal class FakeProfileRepository : ProfileRepository {

    val stored: MutableMap<String, Profile> = linkedMapOf()

    fun put(profile: Profile) {
        stored[profile.id] = profile
    }

    override fun observeAll(): Flow<List<Profile>> = flowOf(stored.values.toList())

    override suspend fun get(profileId: String): Profile? = stored[profileId]

    override suspend fun getSelf(): Profile? = stored.values.firstOrNull { it.isSelf }

    override suspend fun save(profile: Profile) = put(profile)
}

/** Журнал в памяти: по нему проверяется и след операции, и отсутствие в нём медицинских данных. */
internal class RecordingOperationLog : OperationLog {

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
