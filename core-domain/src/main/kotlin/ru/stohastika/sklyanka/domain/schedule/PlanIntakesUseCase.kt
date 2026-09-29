package ru.stohastika.sklyanka.domain.schedule

import kotlinx.coroutines.flow.first
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.repository.ProfileRepository
import ru.stohastika.sklyanka.domain.time.TimeSource

/**
 * Генерация приёмов из расписаний на окно вперёд.
 *
 * Порядок относительно SKL-13 жёсткий: сначала окно приёмов, потом будильники на первые
 * 48 часов. Обратный порядок даёт будильник на `intake_id`, которого ещё нет.
 */
class PlanIntakesUseCase(
    private val profiles: ProfileRepository,
    private val medications: MedicationRepository,
    private val intakes: IntakeRepository,
    private val time: TimeSource,
    private val log: OperationLog,
    private val policy: IntakeGenerationPolicy,
) {

    private val expander = ScheduleExpander(time, policy)
    private val differ = IntakePlanDiffer(time)

    suspend operator fun invoke(
        scope: PlanScope = PlanScope.AllProfiles,
        reason: PlanReason = PlanReason.MANUAL_CHECK,
    ): PlanResult {
        val window = GenerationWindow.of(time.today(), policy.windowDepthDays)
        val plans = targets(scope).map { medication -> plan(medication, window, reason) }
        return PlanResult(window = window, reason = reason, medications = plans)
    }

    /**
     * Лекарства, которые обходит запуск.
     *
     * Полный обход берёт только активные курсы: архивированный курс пуст по плану, и
     * снимать с него будущие ожидающиеся приёмы нужно один раз — в момент архивации,
     * узкой областью [PlanScope.Medication], а не при каждом старте приложения (`TDD.md §7`).
     */
    private suspend fun targets(scope: PlanScope): List<Medication> = when (scope) {
        PlanScope.AllProfiles -> profiles.observeAll().first()
            .flatMap { profile -> medications.observeActive(profile.id).first() }

        is PlanScope.Profile -> medications.observeActive(scope.profileId).first()

        is PlanScope.Medication -> listOfNotNull(medications.get(scope.medicationId))
    }

    private suspend fun plan(medication: Medication, window: GenerationWindow, reason: PlanReason): MedicationPlan {
        val planned = expander.expand(medication, window)
        val existing = intakes.scheduledBetween(
            profileId = medication.profileId,
            medicationId = medication.id,
            from = window.start,
            toExclusive = window.endExclusive,
        )
        val diff = differ.diff(planned, existing)
        // Пустая разница не открывает транзакцию вовсе: самый частый запуск — повторный,
        // и он обязан быть дешёвым и бесследным.
        val applied =
            if (diff.isEmpty) PlanApplyResult.NOTHING else intakes.applyPlan(medication.profileId, medication.id, diff)
        record(medication, applied, reason)
        return MedicationPlan(
            profileId = medication.profileId,
            medicationId = medication.id,
            created = applied.created,
            deleted = applied.deleted,
            unchanged = diff.unchanged,
        )
    }

    /**
     * След в журнале.
     *
     * В журнал идут только идентификаторы и числа: названия лекарств, доз и имён профилей
     * там нет (НФТ-19). Запуск, ничего не изменивший, не пишет ничего — иначе журнал
     * заполняется ежечасным «изменений нет», и в нём тонет единственная важная запись.
     */
    private suspend fun record(medication: Medication, applied: PlanApplyResult, reason: PlanReason) {
        if (applied.created > 0) {
            log.record(
                event = OpEvent.INTAKES_GENERATED,
                profileId = medication.profileId,
                medicationId = medication.id,
                after = applied.created.toString(),
                detail = reason.code,
            )
        }
        if (applied.deleted > 0) {
            log.record(
                event = OpEvent.INTAKES_REVOKED,
                profileId = medication.profileId,
                medicationId = medication.id,
                before = applied.deleted.toString(),
                detail = reason.code,
            )
        }
    }
}
