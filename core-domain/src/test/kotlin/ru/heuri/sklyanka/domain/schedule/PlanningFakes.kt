package ru.heuri.sklyanka.domain.schedule

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.log.OpLogRecord
import ru.heuri.sklyanka.domain.log.OperationLog
import ru.heuri.sklyanka.domain.model.ArchiveReason
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.model.Profile
import ru.heuri.sklyanka.domain.repository.IntakeRepository
import ru.heuri.sklyanka.domain.repository.MedicationRepository
import ru.heuri.sklyanka.domain.repository.ProfileRepository
import ru.heuri.sklyanka.domain.time.TimeSource
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
internal class FakeIntakeRepository(private val time: TimeSource) : IntakeRepository {

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
}

/** Лекарства в памяти. */
internal class FakeMedicationRepository(private val time: TimeSource) : MedicationRepository {

    val stored: MutableMap<String, Medication> = linkedMapOf()

    fun put(medication: Medication) {
        stored[medication.id] = medication
    }

    override fun observeActive(profileId: String): Flow<List<Medication>> =
        flowOf(stored.values.filter { it.profileId == profileId && !it.isArchived })

    override fun observeArchived(profileId: String): Flow<List<Medication>> =
        flowOf(stored.values.filter { it.profileId == profileId && it.isArchived })

    override suspend fun get(medicationId: String): Medication? = stored[medicationId]

    override suspend fun save(medication: Medication) = put(medication)

    override suspend fun archive(medicationId: String, reason: ArchiveReason): Boolean {
        val medication = stored[medicationId]
        if (medication == null || medication.isArchived) return false
        put(medication.copy(archivedAt = time.now(), archiveReason = reason))
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
