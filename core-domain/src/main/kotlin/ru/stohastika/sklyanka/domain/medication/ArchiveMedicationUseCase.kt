package ru.stohastika.sklyanka.domain.medication

import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.repository.MedicationRepository
import ru.stohastika.sklyanka.domain.schedule.MedicationPlanner
import ru.stohastika.sklyanka.domain.schedule.PlanReason

/** Чем закончилась отправка курса в архив. */
sealed interface ArchiveOutcome {

    /**
     * Курс в архиве.
     *
     * [revokedIntakes] — сколько будущих ожидающихся приёмов снято вместе с ним: число
     * нужно журналу и отчёту разбора «куда делся вечерний приём».
     */
    data class Archived(val reason: ArchiveReason, val revokedIntakes: Int) : ArchiveOutcome

    /** Курс уже был в архиве: повторная архивация — не ошибка, но и не событие. */
    data object AlreadyArchived : ArchiveOutcome

    /** Лекарства с таким идентификатором нет: карточку открыли, пока курс уносил перенос данных. */
    data object UnknownMedication : ArchiveOutcome
}

/**
 * Отправка курса в архив: действие «В архив» карточки и подтверждённое предложение
 * «Курс завершён» (ОВ-42).
 *
 * Момент и причина архивации проставляются вместе одним условным обновлением
 * (ИНВ-9, [MedicationRepository.archive]): частично заполненного состояния не бывает
 * даже на время.
 *
 * **Судьба приёмов «ожидается» (ОВ-14).** Реализован вариант «снять будущие
 * ожидающиеся». Он и получается сам: архивированный курс ничего не планирует (ПР-11),
 * поэтому перепланирование этого лекарства видит пустой план и снимает ровно
 * неотмеченные приёмы будущего времени. Уже наступившие ожидающиеся не трогаются —
 * их разберёт правило перевода в «пропущено без отметки» (ПР-1): человек этот приём
 * действительно не отметил, и переписывать прожитое время задним числом нельзя.
 * Отмеченные приёмы не трогаются ни в одном варианте: история архивного курса
 * сохраняется целиком (ПР-11).
 *
 * Порядок жёсткий: сначала запись полей архивации, потом перепланирование. Обратный
 * порядок увидел бы курс ещё активным и породил бы приёмы заново.
 */
class ArchiveMedicationUseCase(private val medications: MedicationRepository, private val planner: MedicationPlanner) {

    suspend operator fun invoke(medicationId: String, reason: ArchiveReason): ArchiveOutcome = when {
        medications.get(medicationId) == null -> ArchiveOutcome.UnknownMedication
        // Архивация условна по пустому моменту архивации: повторный вызов ничего не пишет
        // и не порождает события (ИНВ-9).
        !medications.archive(medicationId, reason) -> ArchiveOutcome.AlreadyArchived
        else -> ArchiveOutcome.Archived(reason = reason, revokedIntakes = revokePlannedIntakes(medicationId))
    }

    /**
     * Снятие будущих ожидающихся приёмов.
     *
     * Отмена будильников (ПР-11) выполняется вне транзакции и принадлежит SKL-13:
     * планировщик платформенный, держать на нём транзакцию базы нельзя. Будильники стоят
     * на приёмах, а снятые приёмы отменять уже нечему.
     */
    private suspend fun revokePlannedIntakes(medicationId: String): Int =
        planner.plan(medicationId, PlanReason.MEDICATION_ARCHIVED).deleted
}
