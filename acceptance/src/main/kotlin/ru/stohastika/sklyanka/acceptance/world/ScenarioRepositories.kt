package ru.stohastika.sklyanka.acceptance.world

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import ru.stohastika.sklyanka.domain.intake.IntakeTransition
import ru.stohastika.sklyanka.domain.intake.MarkCommand
import ru.stohastika.sklyanka.domain.intake.MarkDecision
import ru.stohastika.sklyanka.domain.intake.MarkOutcome
import ru.stohastika.sklyanka.domain.intake.PrnCommand
import ru.stohastika.sklyanka.domain.intake.PrnDailyLimit
import ru.stohastika.sklyanka.domain.intake.PrnOutcome
import ru.stohastika.sklyanka.domain.intake.StockEffect
import ru.stohastika.sklyanka.domain.intake.UndoCommand
import ru.stohastika.sklyanka.domain.intake.UndoDecision
import ru.stohastika.sklyanka.domain.intake.UndoOutcome
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Invariants
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.domain.schedule.IntakePlanDiff
import ru.stohastika.sklyanka.domain.schedule.PlanApplyResult
import ru.stohastika.sklyanka.domain.stock.StockLedger
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Три репозитория поверх состояния сценария — по одной реализации на весь прогон.
 *
 * Раньше каждое состояние сценария реализовывало `IntakeRepository` по-своему: то, что
 * нужно его задаче, — по-настоящему, остальное — заглушкой. Пока состояний было несколько,
 * это означало, что один и тот же вызов домена давал разный результат в зависимости
 * от того, чьи шаги оказались в сценарии. Здесь настоящее всё.
 *
 * Правила не копируются: переход считает [IntakeTransition], списание — [StockLedger],
 * суточный лимит — [PrnDailyLimit]. Повторяется только последовательность транзакции Room:
 * прочитать, решить правилом домена, записать, привести остаток.
 *
 * Репозиториев три объекта, а не один: все три интерфейса объявляют `get(id)` с разным
 * типом результата, и одним классом их не реализовать — разделение здесь требование
 * языка, а не выбор устройства.
 */
class ScenarioRepositories(
    private val profiles: ScenarioProfiles,
    private val medications: ScenarioMedications,
    private val intakes: ScenarioIntakes,
    private val clock: ScenarioClock,
    /**
     * Журнал сценария: след отмены пишет хранилище, как в `Room`-реализации (SKL-30),
     * а не шаг приёмки — иначе проверялась бы копия правила, а не правило.
     */
    private val log: ScenarioLog,
) {

    val profileStore: ProfileRepository = Profiles()

    val medicationStore: MedicationRepository = Medications()

    val intakeStore: IntakeRepository = Intakes()

    private fun ofProfile(profileId: String): List<Intake> {
        val owned = medications.all().filter { it.profileId == profileId }.map { it.id }.toSet()
        return intakes.all().filter { it.medicationId in owned }.sortedBy { it.scheduledAt }
    }

    /**
     * Приёмы профиля, видимые экранам: приёмы удалённого лекарства из выборок выпадают
     * (ПР-14), хотя строки в мире остаются — как и в базе.
     */
    private fun visibleOfProfile(profileId: String): List<Intake> =
        ofProfile(profileId).filter { medications.byId(it.medicationId)?.isDeleted != true }

    private fun applyStock(medicationId: String, effect: StockEffect) {
        val medication = medications.byId(medicationId) ?: return
        // Правило списания — доменное и одно на всё приложение (SKL-12): копия
        // арифметики здесь означала бы, что приёмка проверяет копию правила.
        val change = StockLedger.apply(medication.stockLeft, medication.doseAmount, effect) ?: return
        medications.setStock(medicationId, change.after)
    }

    // --- Профили -------------------------------------------------------------------------------

    private inner class Profiles : ProfileRepository {

        override fun observeAll(): Flow<List<Profile>> = flowOf(profiles.all())

        override suspend fun get(profileId: String): Profile? = profiles.byId(profileId)

        override suspend fun getSelf(): Profile? = profiles.self()

        override suspend fun save(profile: Profile) = profiles.replace(profile)
    }

    // --- Лекарства -----------------------------------------------------------------------------

    private inner class Medications : MedicationRepository {

        override fun observeActive(profileId: String): Flow<List<Medication>> =
            flowOf(medications.all().filter { it.profileId == profileId && !it.isArchived && !it.isDeleted })

        override fun observeArchived(profileId: String): Flow<List<Medication>> =
            flowOf(medications.all().filter { it.profileId == profileId && it.isArchived && !it.isDeleted })

        override suspend fun get(medicationId: String): Medication? = medications.byId(medicationId)

        override suspend fun save(medication: Medication) {
            Invariants.requireValid(Invariants.violations(medication))
            medications.byId(medication.id)?.let { stored ->
                Invariants.requireValid(Invariants.ownershipViolations(stored.profileId, medication))
            }
            medications.put(medication)
        }

        override suspend fun archive(medicationId: String, reason: ArchiveReason): Boolean =
            medications.archiveById(medicationId, reason)

        override suspend fun delete(medicationId: String): Boolean {
            val changed = medications.deleteById(
                medicationId = medicationId,
                hasTaken = intakes.all().any { it.medicationId == medicationId && it.status == IntakeStatus.TAKEN },
            )
            if (changed) {
                log.record(
                    event = OpEvent.MEDICATION_DELETED,
                    profileId = medications.byId(medicationId)?.profileId,
                    medicationId = medicationId,
                )
            }
            return changed
        }

        override suspend fun undelete(medicationId: String): Boolean {
            val changed = medications.undeleteById(medicationId)
            if (changed) {
                log.record(
                    event = OpEvent.MEDICATION_DELETED_UNDONE,
                    profileId = medications.byId(medicationId)?.profileId,
                    medicationId = medicationId,
                )
            }
            return changed
        }

        override suspend fun setStock(medicationId: String, stockLeft: BigDecimal?) {
            medications.setStock(medicationId, stockLeft)
        }
    }

    // --- Приёмы --------------------------------------------------------------------------------

    @Suppress("TooManyFunctions")
    private inner class Intakes : IntakeRepository {

        override fun observeDay(profileId: String, date: LocalDate): Flow<List<Intake>> =
            flowOf(visibleOfProfile(profileId).filter { it.scheduledAt.toLocalDate() == date })

        override fun observePeriod(profileId: String, from: LocalDate, to: LocalDate): Flow<List<Intake>> =
            flowOf(visibleOfProfile(profileId).filter { it.scheduledAt.toLocalDate() in from..to })

        override fun observeByMedication(
            profileId: String,
            medicationId: String,
            from: LocalDate,
            to: LocalDate,
        ): Flow<List<Intake>> = flowOf(
            visibleOfProfile(profileId)
                .filter { it.medicationId == medicationId && it.scheduledAt.toLocalDate() in from..to },
        )

        override suspend fun hasTaken(profileId: String, medicationId: String): Boolean =
            medications.byId(medicationId)?.profileId == profileId &&
                intakes.all().any { it.medicationId == medicationId && it.status == IntakeStatus.TAKEN }

        override fun observeHasTaken(profileId: String, medicationId: String): Flow<Boolean> = flowOf(
            medications.byId(medicationId)?.profileId == profileId &&
                intakes.all().any { it.medicationId == medicationId && it.status == IntakeStatus.TAKEN },
        )

        override suspend fun get(intakeId: String): Intake? = intakes.get(intakeId)

        override suspend fun insertMissing(intakes: List<Intake>): Int {
            // Граница хранилища та же, что у Room: существование лекарства, поля
            // отметки по статусу (ИНВ-10, ИНВ-11), «по потребности» не ожидается (ИНВ-14).
            intakes.forEach { intake ->
                val medication = requireNotNull(medications.byId(intake.medicationId)) {
                    "Intake refers to a medication that does not exist: ${intake.medicationId}"
                }
                Invariants.requireValid(Invariants.writeViolations(intake, medication.scheduleMode))
            }
            return intakes.count { candidate ->
                val absent = this@ScenarioRepositories.intakes.get(candidate.id) == null
                if (absent) this@ScenarioRepositories.intakes.store(candidate)
                absent
            }
        }

        override suspend fun scheduledBetween(
            profileId: String,
            medicationId: String,
            from: LocalDateTime,
            toExclusive: LocalDateTime,
        ): List<Intake> = intakes.all()
            .filter { it.medicationId == medicationId && it.scheduledAt >= from && it.scheduledAt < toExclusive }
            .sortedBy { it.scheduledAt }

        override suspend fun applyPlan(
            profileId: String,
            medicationId: String,
            diff: IntakePlanDiff,
        ): PlanApplyResult {
            val course = requireNotNull(medications.byId(medicationId)) {
                "Plan refers to a medication that does not exist: $medicationId"
            }
            require(course.profileId == profileId) {
                "Plan refers to a medication of another profile: $medicationId"
            }
            // Граница генерации та же, что у Room: рождённая строка проверяется полной
            // тройкой пары, включая курс (ИНВ-15, SKL-107).
            val rows = diff.toCreate.map { moment ->
                Intake(
                    id = intakes.nextId(),
                    medicationId = medicationId,
                    scheduledAt = moment,
                    status = IntakeStatus.PENDING,
                    actedAt = null,
                    source = null,
                    actorProfileId = null,
                ).also { intake -> Invariants.requireValid(Invariants.violations(intake, course, clock.zone)) }
            }
            val deleted = diff.toDelete.count { id -> intakes.removeIfPending(id) }
            val created = rows.count { intake ->
                val absent = intakes.all().none {
                    it.medicationId == medicationId && it.scheduledAt == intake.scheduledAt
                }
                if (absent) intakes.store(intake)
                absent
            }
            return PlanApplyResult(created = created, deleted = deleted)
        }

        /**
         * Ядро идемпотентности отдельным вызовом приёмке не нужно: сценарии отмечают приём
         * через [markAtomically], и только он решает, применять отметку или нет.
         */
        override suspend fun markIfPending(
            intakeId: String,
            status: IntakeStatus,
            source: IntakeSource,
            actorProfileId: String?,
        ): Boolean = false

        override suspend fun expiredPending(before: LocalDateTime): List<Intake> = intakes.expiredPending(before)

        override suspend fun markAtomically(command: MarkCommand): MarkOutcome {
            val current = intakes.get(command.intakeId) ?: return MarkOutcome.NotFound
            if (current.medicationId == intakes.failingMedicationId) {
                error("the write of the intake ${command.intakeId} cannot be performed")
            }
            return when (val decision = IntakeTransition.decide(current, command)) {
                is MarkDecision.Keep -> MarkOutcome.Ignored(decision.intake, decision.reason)
                is MarkDecision.Write -> {
                    val medication = requireNotNull(medications.byId(current.medicationId)) {
                        "An intake addresses a medication that does not exist: ${current.medicationId}"
                    }
                    Invariants.requireValid(Invariants.markingViolations(decision.intake, medication))
                    intakes.store(decision.intake)
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
         * Та же последовательность, что и в транзакции Room (SKL-30): прочитать действие
         * целиком, проверить режим и профили, отменить правилом домена, вернуть остаток
         * и записать след. Отсутствие любого приёма отменяет всё действие.
         */
        override suspend fun undoAtomically(command: UndoCommand): UndoOutcome {
            val current = command.intakeIds.map { intakeId -> intakes.get(intakeId) ?: return UndoOutcome.NotFound }
            val medicationsById = current.map { it.medicationId }.distinct().associateWith { medicationId ->
                requireNotNull(medications.byId(medicationId)) {
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
                        Invariants.requireValid(Invariants.violations(decision.intake))
                        intakes.store(decision.intake)
                        applyStock(intake.medicationId, decision.stock)
                        log.record(
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

        override suspend fun createPrnAtomically(command: PrnCommand): PrnOutcome {
            val medication = medications.byId(command.medicationId)
            return when {
                medication == null -> PrnOutcome.UnknownMedication
                medication.isDeleted -> PrnOutcome.Deleted
                else -> createPrn(command, medication)
            }
        }

        private fun createPrn(
            command: PrnCommand,
            medication: ru.stohastika.sklyanka.domain.model.Medication,
        ): PrnOutcome {
            val day = command.scheduledAt.toLocalDate()
            val countToday = intakes.all()
                .count { it.medicationId == medication.id && it.scheduledAt.toLocalDate() == day }
            val limit = medication.maxPerDay
            return if (PrnDailyLimit.reached(limit, countToday)) {
                PrnOutcome.RejectedDailyLimit(limit = requireNotNull(limit), countToday = countToday)
            } else {
                val intake = Intake(
                    id = intakes.nextId(),
                    medicationId = medication.id,
                    scheduledAt = command.scheduledAt,
                    status = IntakeStatus.TAKEN,
                    actedAt = command.actedAt,
                    source = command.source,
                    actorProfileId = command.actorProfileId,
                )
                Invariants.requireValid(Invariants.prnBirthViolations(intake, medication, clock.zone))
                intakes.store(intake)
                applyStock(medication.id, StockEffect.DECREMENT)
                PrnOutcome.Created(intake)
            }
        }

        override suspend fun markMissed(ids: List<String>, at: Instant): Int = ids.count { id ->
            val intake = intakes.get(id)
            if (intake == null || !intake.isPending) {
                false
            } else {
                intakes.store(IntakeTransition.expire(intake, at))
                true
            }
        }
    }
}
