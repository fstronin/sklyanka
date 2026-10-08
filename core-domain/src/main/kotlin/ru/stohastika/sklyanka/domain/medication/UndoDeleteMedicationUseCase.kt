package ru.stohastika.sklyanka.domain.medication

import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.schedule.MedicationPlanner
import ru.stohastika.sklyanka.domain.schedule.PlanReason

/** Чем закончился возврат удаления в коротком окне (ПМ-24 п. 5). */
sealed interface UndoDeleteOutcome {

    /** Лекарство снова активно; [createdIntakes] — сколько приёмов порождено заново. */
    data class Undone(val createdIntakes: Int) : UndoDeleteOutcome

    /** Запись не удалена: возвращать нечего. */
    data object NotDeleted : UndoDeleteOutcome

    data object UnknownMedication : UndoDeleteOutcome
}

/**
 * Возврат мягко удалённого лекарства в коротком окне отмены (ПМ-24 п. 5).
 *
 * Окно (4–6 секунд) — забота магазина «последнее действие», а не сценария: сценарий
 * времени не знает и не проверяет, как и отмена отметки (SKL-30). Он выполняет ровно
 * то, что обещает человеку: очищает `deleted_at`, возвращает приёмы ближайшего окна
 * и пересобирает будильники. История не пересоздаётся: приёмы порождаются от текущей
 * даты, уже записанные отметки не трогаются.
 */
class UndoDeleteMedicationUseCase(
    private val medications: MedicationRepository,
    private val planner: MedicationPlanner,
) {

    suspend operator fun invoke(medicationId: String): UndoDeleteOutcome =
        when (val medication = medications.get(medicationId)) {
            null -> UndoDeleteOutcome.UnknownMedication
            else -> undo(medication)
        }

    private suspend fun undo(medication: Medication): UndoDeleteOutcome = when {
        !medication.isDeleted -> UndoDeleteOutcome.NotDeleted
        // Условная очистка: между чтением и записью окно могло закрыться — и тогда
        // возврата не было вовсе, а не «частичного».
        !medications.undelete(medication.id) -> UndoDeleteOutcome.NotDeleted
        else -> applied(medication)
    }

    private suspend fun applied(medication: Medication): UndoDeleteOutcome {
        val plan = planner.plan(medication.id, PlanReason.MEDICATION_UNDELETED)
        return UndoDeleteOutcome.Undone(createdIntakes = plan.created)
    }
}
