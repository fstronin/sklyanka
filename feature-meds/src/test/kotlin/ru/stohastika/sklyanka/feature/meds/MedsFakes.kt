package ru.stohastika.sklyanka.feature.meds

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ru.stohastika.sklyanka.domain.Transactor
import ru.stohastika.sklyanka.domain.intake.MarkCommand
import ru.stohastika.sklyanka.domain.intake.MarkOutcome
import ru.stohastika.sklyanka.domain.intake.PrnCommand
import ru.stohastika.sklyanka.domain.intake.PrnOutcome
import ru.stohastika.sklyanka.domain.intake.UndoCommand
import ru.stohastika.sklyanka.domain.intake.UndoOutcome
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OpLogRecord
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.domain.schedule.IntakePlanDiff
import ru.stohastika.sklyanka.domain.schedule.PlanApplyResult
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Хранилища в памяти вместо Room.
 *
 * Состояние экрана — это арифметика по потокам, и проверять её на настоящей базе
 * значит проверять Room. Настоящих названий лекарств и имён людей здесь нет:
 * тестовые данные попадают в отчёты (НФТ-19).
 */
internal object MedsTestData {

    const val PROFILE_ID = "11111111-1111-4111-8111-111111111111"
    const val OTHER_PROFILE_ID = "22222222-2222-4222-8222-222222222222"

    val TODAY: LocalDate = LocalDate.of(2026, 9, 25)
    val ZONE: ZoneId = ZoneId.of("Europe/Moscow")
    val MORNING: LocalTime = LocalTime.of(8, 0)
    val EVENING: LocalTime = LocalTime.of(20, 0)

    fun clock(at: LocalDateTime = TODAY.atTime(10, 0)): FixedTimeSource =
        FixedTimeSource(at.atZone(ZONE).toInstant(), ZONE)

    @Suppress("LongParameterList")
    fun medication(
        id: String,
        profileId: String = PROFILE_ID,
        name: String = "med-$id",
        form: MedicationForm = MedicationForm.TABLET,
        times: List<LocalTime> = listOf(MORNING),
        mode: ScheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay: Int? = null,
        stockLeft: String? = null,
        packageSize: String? = null,
        thresholdDays: Int? = null,
        startDate: LocalDate = LocalDate.of(2026, 3, 12),
        endDate: LocalDate? = null,
        archivedAt: Instant? = null,
        archiveReason: ArchiveReason? = archivedAt?.let { ArchiveReason.MANUAL },
        shape: TabletShape? = null,
        foodRelation: FoodRelation = FoodRelation.ANY,
    ) = Medication(
        id = id,
        profileId = profileId,
        name = name,
        form = form,
        shape = shape,
        doseAmount = BigDecimal("2"),
        doseUnit = "tablet",
        strength = BigDecimal("50"),
        strengthUnit = "mg",
        foodRelation = foodRelation,
        scheduleMode = mode,
        maxPerDay = maxPerDay,
        times = if (mode == ScheduleMode.SCHEDULED) times else emptyList(),
        startDate = startDate,
        endDate = endDate,
        stockLeft = stockLeft?.let { BigDecimal(it) },
        packageSize = packageSize?.let { BigDecimal(it) },
        stockThresholdDays = thresholdDays,
        archivedAt = archivedAt,
        archiveReason = archiveReason,
    )

    fun intake(medicationId: String, at: LocalDateTime, status: IntakeStatus) = Intake(
        id = "intake-$medicationId-$at-$status",
        medicationId = medicationId,
        scheduledAt = at,
        status = status,
        actedAt = if (status == IntakeStatus.PENDING) null else at.atZone(ZONE).toInstant(),
        source = if (status == IntakeStatus.PENDING) null else IntakeSource.PHONE,
        actorProfileId = null,
    )

    fun taken(medicationId: String, at: LocalDateTime) = Intake(
        id = "intake-$medicationId-$at",
        medicationId = medicationId,
        scheduledAt = at,
        status = IntakeStatus.TAKEN,
        actedAt = at.atZone(ZONE).toInstant(),
        source = IntakeSource.PHONE,
        actorProfileId = null,
    )
}

/** Активный профиль: меняется на ходу — экран обязан пересобраться сам (ПР-10). */
internal class FakeActiveProfile(initial: String = MedsTestData.PROFILE_ID) : ActiveProfileRepository {

    private val id = MutableStateFlow(initial)

    override val activeProfileId: Flow<String> get() = id

    override suspend fun current(): String = id.value

    override suspend fun switchTo(profileId: String) {
        id.value = profileId
    }
}

/** Лекарства в памяти; поток пересобирается на каждое изменение. */
internal class FakeMedications : MedicationRepository {

    private val stored = MutableStateFlow<List<Medication>>(emptyList())

    fun put(vararg medications: Medication) {
        stored.value = stored.value.filterNot { existing -> medications.any { it.id == existing.id } } + medications
    }

    fun snapshot(): List<Medication> = stored.value

    override fun observeActive(profileId: String): Flow<List<Medication>> =
        stored.map { all -> all.filter { it.profileId == profileId && !it.isArchived && !it.isDeleted } }

    override fun observeArchived(profileId: String): Flow<List<Medication>> =
        stored.map { all -> all.filter { it.profileId == profileId && it.isArchived && !it.isDeleted } }

    override suspend fun get(medicationId: String): Medication? = stored.value.firstOrNull { it.id == medicationId }

    override suspend fun save(medication: Medication) = put(medication)

    override suspend fun archive(medicationId: String, reason: ArchiveReason): Boolean {
        val medication = get(medicationId)?.takeIf { !it.isArchived && !it.isDeleted }
        medication?.let { put(it.copy(archivedAt = Instant.EPOCH, archiveReason = reason)) }
        return medication != null
    }

    override suspend fun delete(medicationId: String): Boolean {
        val medication = get(medicationId)?.takeIf { !it.isDeleted }
        medication?.let { put(it.copy(deletedAt = Instant.EPOCH, archivedAt = null, archiveReason = null)) }
        return medication != null
    }

    override suspend fun undelete(medicationId: String): Boolean {
        val medication = get(medicationId)?.takeIf { it.isDeleted }
        medication?.let { put(it.copy(deletedAt = null)) }
        return medication != null
    }

    override suspend fun setStock(medicationId: String, stockLeft: BigDecimal?) {
        get(medicationId)?.let { put(it.copy(stockLeft = stockLeft)) }
    }
}

/**
 * Приёмы в памяти.
 *
 * Отметка и создание приёма по потребности принадлежат SKL-07 и здесь только
 * объявлены: список лекарств их не вызывает — он читает историю и показывает её.
 */
internal class FakeIntakes : IntakeRepository {

    private val stored = MutableStateFlow<List<Intake>>(emptyList())

    fun put(vararg intakes: Intake) {
        stored.value = stored.value + intakes
    }

    fun snapshot(): List<Intake> = stored.value

    override fun observeDay(profileId: String, date: LocalDate): Flow<List<Intake>> =
        stored.map { all -> all.filter { it.scheduledAt.toLocalDate() == date } }

    override fun observePeriod(profileId: String, from: LocalDate, to: LocalDate): Flow<List<Intake>> =
        stored.map { all -> all.filter { it.scheduledAt.toLocalDate() in from..to } }

    override fun observeByMedication(
        profileId: String,
        medicationId: String,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<Intake>> = stored.map { all -> all.filter { it.medicationId == medicationId } }

    override suspend fun hasTaken(profileId: String, medicationId: String): Boolean =
        stored.value.any { it.medicationId == medicationId && it.status == IntakeStatus.TAKEN }

    override fun observeHasTaken(profileId: String, medicationId: String): Flow<Boolean> =
        stored.map { all -> all.any { it.medicationId == medicationId && it.status == IntakeStatus.TAKEN } }

    override suspend fun get(intakeId: String): Intake? = stored.value.firstOrNull { it.id == intakeId }

    override suspend fun insertMissing(intakes: List<Intake>): Int {
        val missing = intakes.filterNot { candidate ->
            stored.value.any { it.medicationId == candidate.medicationId && it.scheduledAt == candidate.scheduledAt }
        }
        put(*missing.toTypedArray())
        return missing.size
    }

    override suspend fun scheduledBetween(
        profileId: String,
        medicationId: String,
        from: LocalDateTime,
        toExclusive: LocalDateTime,
    ): List<Intake> = stored.value.filter {
        it.medicationId == medicationId && it.scheduledAt >= from && it.scheduledAt < toExclusive
    }

    override suspend fun applyPlan(profileId: String, medicationId: String, diff: IntakePlanDiff): PlanApplyResult {
        stored.value = stored.value.filterNot { it.id in diff.toDelete }
        val created = diff.toCreate.map { moment ->
            Intake(
                id = "planned-$medicationId-$moment",
                medicationId = medicationId,
                scheduledAt = moment,
                status = IntakeStatus.PENDING,
                actedAt = null,
                source = null,
                actorProfileId = null,
            )
        }
        put(*created.toTypedArray())
        return PlanApplyResult(created = created.size, deleted = diff.toDelete.size)
    }

    override suspend fun markIfPending(
        intakeId: String,
        status: IntakeStatus,
        source: IntakeSource,
        actorProfileId: String?,
    ): Boolean = false

    override suspend fun expiredPending(before: LocalDateTime): List<Intake> = emptyList()

    override suspend fun markAtomically(command: MarkCommand): MarkOutcome = MarkOutcome.NotFound

    /** Отмена — предмет SKL-30, и список лекарств её не вызывает: объявлена неиспользуемой. */
    override suspend fun undoAtomically(command: UndoCommand): UndoOutcome = UndoOutcome.NotFound

    override suspend fun createPrnAtomically(command: PrnCommand): PrnOutcome = PrnOutcome.UnknownMedication

    override suspend fun markMissed(ids: List<String>, at: Instant): Int = 0
}

/** Профили в памяти: планировщику нужен их список для полного обхода. */
internal class FakeProfiles : ProfileRepository {

    private val stored = MutableStateFlow<List<Profile>>(emptyList())

    fun put(profile: Profile) {
        stored.value = stored.value + profile
    }

    override fun observeAll(): Flow<List<Profile>> = stored

    override suspend fun get(profileId: String): Profile? = stored.value.firstOrNull { it.id == profileId }

    override suspend fun getSelf(): Profile? = stored.value.firstOrNull { it.isSelf }

    override suspend fun save(profile: Profile) = put(profile)
}

/** Транзакция в памяти. */
internal class DirectTransactor : Transactor {
    override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
}

/** Журнал в памяти. */
internal class RecordingLog : OperationLog {

    val records: MutableList<OpLogRecord> = mutableListOf()

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
