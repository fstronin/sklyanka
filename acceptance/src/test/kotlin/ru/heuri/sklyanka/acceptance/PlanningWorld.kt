package ru.heuri.sklyanka.acceptance

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
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
import ru.heuri.sklyanka.domain.schedule.IntakeGenerationPolicy
import ru.heuri.sklyanka.domain.schedule.IntakePlanDiff
import ru.heuri.sklyanka.domain.schedule.PlanApplyResult
import ru.heuri.sklyanka.domain.schedule.PlanIntakesUseCase
import ru.heuri.sklyanka.domain.schedule.PlanReason
import ru.heuri.sklyanka.domain.schedule.PlanScope
import ru.heuri.sklyanka.domain.time.FixedTimeSource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Состояние сценария приёмки SKL-06.
 *
 * Хранилище — в памяти, а не Room: сценарии задачи говорят о составе списка приёмов, то есть
 * о доменном правиле. Room проверяется инструментальными тестами `core-data`, эмулятора
 * для них в сборочном контейнере нет, и тащить их сюда значило бы не проверить ничего.
 */
internal class PlanningWorld {

    val zone: ZoneId = ZoneId.of("Europe/Moscow")
    val clock = FixedTimeSource(DEFAULT_MOMENT.atZone(ZoneId.of("Europe/Moscow")).toInstant(), zone)

    /** Глубина окна генерации: значение приходит из Предыстории сценария, а не из кода. */
    var depthDays: Int = DEFAULT_DEPTH_DAYS

    private val profilesByName = linkedMapOf<String, Profile>()
    private val medicationsByName = linkedMapOf<String, Medication>()
    private val intakesById = linkedMapOf<String, Intake>()

    private var sequence = 0
    private var remembered = false

    /** Состояние приёмов до первого действия сценария: с ним сверяются «остаётся», «не изменяется». */
    var before: Map<String, Intake> = emptyMap()
        private set

    var activeProfile: String = ""
        private set

    /** Лекарство, о котором шла речь последним: шаги без имени говорят о нём. */
    var lastMedication: String = ""
        private set

    val log = CollectingLog()

    // --- Время -------------------------------------------------------------------------

    fun today(): LocalDate = clock.today()

    fun moveTo(moment: LocalDateTime) {
        clock.advanceTo(moment.atZone(clock.zone()).toInstant())
    }

    fun moveToDate(date: LocalDate) {
        moveTo(date.atTime(clock.nowLocal().toLocalTime()))
    }

    /** Перелёт: момент остаётся прежним, местные часы сдвигаются вместе с поясом. */
    fun shiftZone(hours: Long) {
        clock.moveTo(ZoneId.ofOffset("", ZoneOffset.ofHours(MOSCOW_OFFSET_HOURS + hours.toInt())))
    }

    // --- Профили и лекарства -------------------------------------------------------------

    fun useProfile(name: String) {
        profile(name)
        activeProfile = name
    }

    fun profile(name: String): Profile = profilesByName.getOrPut(name) {
        Profile(
            id = "profile-${profilesByName.size + 1}",
            name = name,
            color = 0,
            isSelf = profilesByName.isEmpty(),
            mirrorReminders = profilesByName.isNotEmpty(),
        )
    }

    fun hasMedication(name: String): Boolean = medicationsByName.containsKey(name)

    @Suppress("LongParameterList")
    fun addMedication(
        profileName: String,
        name: String,
        times: List<LocalTime>,
        startDate: LocalDate = today(),
        endDate: LocalDate? = null,
        mode: ScheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay: Int? = null,
    ) {
        val owner = profile(profileName)
        medicationsByName[name] = Medication(
            id = "medication-${medicationsByName.size + 1}",
            profileId = owner.id,
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
            startDate = startDate,
            endDate = endDate,
            stockLeft = null,
            stockThresholdDays = null,
            archivedAt = null,
            archiveReason = null,
        )
        lastMedication = name
    }

    fun medication(name: String): Medication = requireNotNull(medicationsByName[name]) { "unknown medication: $name" }

    fun update(name: String, change: (Medication) -> Medication) {
        medicationsByName[name] = change(medication(name))
        lastMedication = name
    }

    fun archive(name: String, at: Instant) {
        update(name) { it.copy(archivedAt = at, archiveReason = ArchiveReason.MANUAL) }
    }

    fun unarchive(name: String) {
        update(name) { it.copy(archivedAt = null, archiveReason = null) }
    }

    // --- Приёмы ----------------------------------------------------------------------------

    fun intakesOf(name: String): List<Intake> {
        val medicationId = medication(name).id
        return intakesById.values.filter { it.medicationId == medicationId }.sortedBy { it.scheduledAt }
    }

    fun pendingOf(name: String): List<Intake> = intakesOf(name).filter { it.isPending }

    fun intakesOfProfile(profileName: String): List<Intake> {
        val profileId = profile(profileName).id
        val owned = medicationsByName.values.filter { it.profileId == profileId }.map { it.id }.toSet()
        return intakesById.values.filter { it.medicationId in owned }.sortedBy { it.scheduledAt }
    }

    fun pendingOfProfile(profileName: String): List<Intake> = intakesOfProfile(profileName).filter { it.isPending }

    fun markTaken(name: String, at: LocalDateTime) {
        val medication = medication(name)
        val existing = intakesById.values.firstOrNull { it.medicationId == medication.id && it.scheduledAt == at }
        val id = existing?.id ?: "intake-${sequence++}"
        intakesById[id] = Intake(
            id = id,
            medicationId = medication.id,
            scheduledAt = at,
            status = IntakeStatus.TAKEN,
            actedAt = at.atZone(zone).toInstant(),
            source = IntakeSource.PHONE,
            actorProfileId = null,
        )
    }

    // --- Запуск планировщика ------------------------------------------------------------------

    fun plan(scope: PlanScope = PlanScope.AllProfiles, reason: PlanReason = PlanReason.MANUAL_CHECK) {
        val useCase = PlanIntakesUseCase(
            profiles = Profiles(),
            medications = Medications(),
            intakes = Intakes(),
            time = clock,
            log = log,
            policy = IntakeGenerationPolicy(windowDepthDays = depthDays),
        )
        runBlocking { useCase(scope, reason) }
    }

    fun planMedication(name: String, reason: PlanReason) {
        plan(PlanScope.Medication(medication(name).id), reason)
    }

    /**
     * Запомнить состояние перед первым действием сценария.
     *
     * Снимок берётся один раз: шаги «Когда» бывают парой — правка расписания и следом
     * запуск планировщика, — и сверяться нужно с состоянием до правки.
     */
    fun rememberState() {
        if (remembered) return
        before = intakesById.toMap()
        remembered = true
    }

    // --- Репозитории поверх состояния сценария ---------------------------------------------------

    private inner class Profiles : ProfileRepository {
        override fun observeAll(): Flow<List<Profile>> = flowOf(profilesByName.values.toList())
        override suspend fun get(profileId: String): Profile? = profilesByName.values.firstOrNull { it.id == profileId }
        override suspend fun getSelf(): Profile? = profilesByName.values.firstOrNull { it.isSelf }
        override suspend fun save(profile: Profile) = Unit
    }

    private inner class Medications : MedicationRepository {
        override fun observeActive(profileId: String): Flow<List<Medication>> =
            flowOf(medicationsByName.values.filter { it.profileId == profileId && !it.isArchived })

        override fun observeArchived(profileId: String): Flow<List<Medication>> =
            flowOf(medicationsByName.values.filter { it.profileId == profileId && it.isArchived })

        override suspend fun get(medicationId: String): Medication? =
            medicationsByName.values.firstOrNull { it.id == medicationId }

        override suspend fun save(medication: Medication) = Unit
        override suspend fun archive(medicationId: String, reason: ArchiveReason): Boolean = false
        override suspend fun setStock(medicationId: String, stockLeft: BigDecimal?) = Unit
    }

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
        ): List<Intake> = intakesById.values
            .filter { it.medicationId == medicationId && it.scheduledAt >= from && it.scheduledAt < toExclusive }
            .sortedBy { it.scheduledAt }

        override suspend fun applyPlan(
            profileId: String,
            medicationId: String,
            diff: IntakePlanDiff,
        ): PlanApplyResult {
            val deleted = diff.toDelete.count { id ->
                intakesById[id]?.isPending == true && intakesById.remove(id) != null
            }
            val created = diff.toCreate.count { moment ->
                val taken = intakesById.values.any { it.medicationId == medicationId && it.scheduledAt == moment }
                if (!taken) {
                    val id = "intake-${sequence++}"
                    intakesById[id] = Intake(
                        id = id,
                        medicationId = medicationId,
                        scheduledAt = moment,
                        status = IntakeStatus.PENDING,
                        actedAt = null,
                        source = null,
                        actorProfileId = null,
                    )
                }
                !taken
            }
            return PlanApplyResult(created = created, deleted = deleted)
        }

        override suspend fun markIfPending(
            intakeId: String,
            status: IntakeStatus,
            source: IntakeSource,
            actorProfileId: String?,
        ): Boolean = false

        override suspend fun expiredPending(before: LocalDateTime): List<Intake> = emptyList()
    }

    private companion object {
        val DEFAULT_MOMENT: LocalDateTime = LocalDateTime.of(2026, 9, 25, 7, 0)
        const val DEFAULT_DEPTH_DAYS = 7
        const val MOSCOW_OFFSET_HOURS = 3
    }
}

/** Журнал сценария: сюда смотрит проверка «медицинских данных в журнале нет». */
internal class CollectingLog : OperationLog {

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
