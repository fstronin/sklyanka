package ru.stohastika.sklyanka.domain.medication

import ru.stohastika.sklyanka.domain.Transactor
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.schedule.MedicationPlanner
import ru.stohastika.sklyanka.domain.schedule.PlanReason
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.LocalDate

/** Чем закончился возврат курса из архива. */
sealed interface RestoreOutcome {

    /** Курс снова активен; [createdIntakes] — сколько приёмов порождено на ближайшее окно. */
    data class Restored(val createdIntakes: Int) : RestoreOutcome

    /**
     * Дата окончания курса в прошлом: возврат как есть не породил бы ни одного приёма.
     *
     * Человек нажал «Вернуть» и не получил ничего — молчаливого возврата в никуда
     * не происходит (ОВ-14). Что делает с этим экран — объясняет или открывает правку
     * периода — решает экран; курс при этом остаётся в архиве.
     */
    data class NeedsPeriodUpdate(val endDate: LocalDate) : RestoreOutcome

    /** Курс не в архиве: возвращать нечего. */
    data object NotArchived : RestoreOutcome

    /** Лекарство мягко удалено: поля архива очищены, возврат из архива к нему неприменим (ПР-14). */
    data object Deleted : RestoreOutcome

    data object UnknownMedication : RestoreOutcome
}

/**
 * Возврат курса из архива — обратная операция к архивации.
 *
 * Момент и причина архивации очищаются **вместе**, одной записью строки (ИНВ-9):
 * частично очищенного состояния не бывает. Запись идёт в транзакции, потому что
 * инвариант проверяется перед ней, а не после.
 *
 * Перепланирование — **после** транзакции, не внутри: планировщик платформенный, и
 * держать на нём транзакцию базы нельзя (НФТ-6, НАП-11). Приёмы порождаются от текущей
 * даты, а не от начала курса: пересоздавать прошлое нельзя, история уже записана
 * (ПР-11, НАП-31).
 *
 * Бессрочный курс возвращается бессрочным: пустая дата окончания не заполняется ничем
 * (ПР-6).
 */
class RestoreMedicationUseCase(
    private val medications: MedicationRepository,
    private val planner: MedicationPlanner,
    private val transactor: Transactor,
    private val time: TimeSource,
    private val log: OperationLog,
) {

    suspend operator fun invoke(medicationId: String): RestoreOutcome {
        val medication = medications.get(medicationId)
        // Сравниваются календарные даты, а не моменты: курс, заканчивающийся сегодня, ещё
        // не в прошлом — последний день входит в курс целиком (ИНВ-15).
        val endInThePast = medication?.endDate?.takeIf { it < time.today() }
        return when {
            medication == null -> RestoreOutcome.UnknownMedication
            medication.isDeleted -> RestoreOutcome.Deleted
            !medication.isArchived -> RestoreOutcome.NotArchived
            endInThePast != null -> refuse(medication, endInThePast)
            else -> restore(medication)
        }
    }

    /**
     * Отказ в возврате: приёмов такой курс не породит, и человеку об этом сказано.
     *
     * Курс остаётся в архиве. Молчаливый возврат в никуда — худший вариант из разобранных
     * в `docs/tasks/SKL-11/task.md` (ОВ-14).
     */
    private suspend fun refuse(medication: Medication, endDate: LocalDate): RestoreOutcome {
        log.record(
            event = OpEvent.MEDICATION_UPDATED,
            profileId = medication.profileId,
            medicationId = medication.id,
            detail = DETAIL_PERIOD_IN_PAST,
        )
        return RestoreOutcome.NeedsPeriodUpdate(endDate)
    }

    private suspend fun restore(medication: Medication): RestoreOutcome {
        val reasonBefore: ArchiveReason? = medication.archiveReason
        transactor.inTransaction {
            medications.save(medication.copy(archivedAt = null, archiveReason = null))
        }
        val plan = planner.plan(medication.id, PlanReason.MEDICATION_RESTORED)
        log.record(
            event = OpEvent.MEDICATION_UPDATED,
            profileId = medication.profileId,
            medicationId = medication.id,
            before = reasonBefore?.name,
            after = plan.created.toString(),
            detail = DETAIL_RESTORED,
        )
        return RestoreOutcome.Restored(createdIntakes = plan.created)
    }

    private companion object {
        /** Служебные коды журнала: русского текста и названий лекарств в нём нет (НФТ-19, НФТ-42). */
        const val DETAIL_RESTORED = "restored_from_archive"
        const val DETAIL_PERIOD_IN_PAST = "restore_blocked_period_in_past"
    }
}
