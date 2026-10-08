package ru.stohastika.sklyanka.domain.medication

import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.schedule.MedicationPlanner
import ru.stohastika.sklyanka.domain.schedule.PlanReason

/** Чем закончилось мягкое удаление лекарства (ПМ-24, ПР-14). */
sealed interface DeleteOutcome {

    /**
     * Лекарство удалено мягко.
     *
     * [revokedIntakes] — сколько будущих ожидающихся приёмов снято вместе с ним: число
     * нужно разбору «куда делся вечерний приём» (его пишет планировщик своим событием).
     */
    data class Deleted(val revokedIntakes: Int) : DeleteOutcome

    /**
     * У лекарства есть отметка `TAKEN`: удаление скрыло бы факт приёма.
     *
     * Единственный путь для такого курса — архив (ПР-14, ИНВ-22).
     */
    data object HasTakenIntakes : DeleteOutcome

    /** Запись уже удалена: повтор — не ошибка, но и не событие. */
    data object AlreadyDeleted : DeleteOutcome

    /** Лекарства с таким идентификатором нет: карточку открыли, пока запись уносил перенос. */
    data object UnknownMedication : DeleteOutcome
}

/**
 * Мягкое удаление ошибочно заведённого лекарства (ПМ-24, ПР-14).
 *
 * Границу «ни одной отметки `TAKEN`» держат двое, и это не дублирование:
 * проверка здесь даёт человеку объяснимый исход, а условное обновление хранилища
 * (`MedicationRepository.delete`) — гарантию: между открытием карточки и подтверждением
 * отметку мог поставить другой путь, и удаление, скрывшее факт приёма, недопустимо.
 *
 * Порядок жёсткий, как у архивации: сначала запись состояния, потом перепланирование.
 * Обратный порядок увидел бы курс ещё активным и породил бы снятые приёмы заново.
 * Число снятых приёмов возвращается наружу для журнала.
 */
class DeleteMedicationUseCase(
    private val medications: MedicationRepository,
    private val intakes: IntakeRepository,
    private val planner: MedicationPlanner,
) {

    suspend operator fun invoke(medicationId: String): DeleteOutcome =
        when (val medication = medications.get(medicationId)) {
            null -> DeleteOutcome.UnknownMedication
            else -> delete(medication)
        }

    private suspend fun delete(medication: Medication): DeleteOutcome = when {
        medication.isDeleted -> DeleteOutcome.AlreadyDeleted
        intakes.hasTaken(medication.profileId, medication.id) -> DeleteOutcome.HasTakenIntakes
        // Условное обновление — единственная точка удаления (ИНВ-21, ИНВ-22): 0 строк
        // означает гонку с отметкой или чужое удаление, и исход берётся из нового чтения.
        !medications.delete(medication.id) -> raced(medication)
        else -> applied(medication)
    }

    private suspend fun applied(medication: Medication): DeleteOutcome {
        val revoked = planner.plan(medication.id, PlanReason.MEDICATION_DELETED).deleted
        return DeleteOutcome.Deleted(revokedIntakes = revoked)
    }

    /** Проигранная гонка: состояние перечитывается, чтобы исход называл настоящую причину. */
    private suspend fun raced(medication: Medication): DeleteOutcome = when {
        intakes.hasTaken(medication.profileId, medication.id) -> DeleteOutcome.HasTakenIntakes
        else -> DeleteOutcome.AlreadyDeleted
    }
}
