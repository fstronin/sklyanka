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
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.Profile
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
) {

    val profileStore: ProfileRepository = Profiles()

    val medicationStore: MedicationRepository = Medications()

    val intakeStore: IntakeRepository = Intakes()

    private fun ofProfile(profileId: String): List<Intake> {
        val owned = medications.all().filter { it.profileId == profileId }.map { it.id }.toSet()
        return intakes.all().filter { it.medicationId in owned }.sortedBy { it.scheduledAt }
    }

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
            flowOf(medications.all().filter { it.profileId == profileId && !it.isArchived })

        override fun observeArchived(profileId: String): Flow<List<Medication>> =
            flowOf(medications.all().filter { it.profileId == profileId && it.isArchived })

        override suspend fun get(medicationId: String): Medication? = medications.byId(medicationId)

        override suspend fun save(medication: Medication) = medications.put(medication)

        override suspend fun archive(medicationId: String, reason: ArchiveReason): Boolean =
            medications.archiveById(medicationId, reason)

        override suspend fun setStock(medicationId: String, stockLeft: BigDecimal?) {
            medications.setStock(medicationId, stockLeft)
        }
    }

    // --- Приёмы --------------------------------------------------------------------------------

    @Suppress("TooManyFunctions")
    private inner class Intakes : IntakeRepository {

        override fun observeDay(profileId: String, date: LocalDate): Flow<List<Intake>> =
            flowOf(ofProfile(profileId).filter { it.scheduledAt.toLocalDate() == date })

        override fun observePeriod(profileId: String, from: LocalDate, to: LocalDate): Flow<List<Intake>> =
            flowOf(ofProfile(profileId).filter { it.scheduledAt.toLocalDate() in from..to })

        override fun observeByMedication(
            profileId: String,
            medicationId: String,
            from: LocalDate,
            to: LocalDate,
        ): Flow<List<Intake>> = flowOf(
            ofProfile(profileId)
                .filter { it.medicationId == medicationId && it.scheduledAt.toLocalDate() in from..to },
        )

        override suspend fun get(intakeId: String): Intake? = intakes.get(intakeId)

        override suspend fun insertMissing(intakes: List<Intake>): Int = 0

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
            val deleted = diff.toDelete.count { id -> intakes.removeIfPending(id) }
            val created = diff.toCreate.count { moment ->
                val taken = intakes.all().any { it.medicationId == medicationId && it.scheduledAt == moment }
                if (!taken) {
                    intakes.store(
                        Intake(
                            id = intakes.nextId(),
                            medicationId = medicationId,
                            scheduledAt = moment,
                            status = IntakeStatus.PENDING,
                            actedAt = null,
                            source = null,
                            actorProfileId = null,
                        ),
                    )
                }
                !taken
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

        override suspend fun createPrnAtomically(command: PrnCommand): PrnOutcome {
            val medication = medications.byId(command.medicationId) ?: return PrnOutcome.UnknownMedication
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
