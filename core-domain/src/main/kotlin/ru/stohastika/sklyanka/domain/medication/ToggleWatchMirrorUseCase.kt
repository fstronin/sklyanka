package ru.stohastika.sklyanka.domain.medication

import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.repository.MedicationRepository

/** Чем закончилось переключение дублирования напоминаний на часы. */
sealed interface WatchMirrorOutcome {

    data class Applied(val before: Boolean, val after: Boolean) : WatchMirrorOutcome

    /**
     * Лекарство «по потребности»: дублировать нечего (ЧАС-17).
     *
     * Отдельный исход, а не тихое «ничего не произошло»: строка тумблера у такого
     * лекарства не показывается вовсе, и попытка записать значение означает дефект
     * экрана, который обязан быть виден.
     */
    data object NotApplicable : WatchMirrorOutcome

    /** Курс в архиве: напоминаний у него нет, и дублировать их некуда (ПР-11). */
    data object Archived : WatchMirrorOutcome

    /** Лекарство мягко удалено: напоминаний нет и не будет (ПР-14). */
    data object Deleted : WatchMirrorOutcome

    data object UnknownMedication : WatchMirrorOutcome
}

/**
 * Тумблер «Напоминать на часах» (ЧАС-15).
 *
 * Значение пишется в лекарство и читается в момент показа напоминания. Недоступность
 * часов на запись не влияет и ошибкой сценария не является (ЧАС-19): состояние связи —
 * дело SKL-17, а тумблер выражает желание человека, а не факт подключения.
 *
 * Перепланирования здесь нет намеренно, хотя `TDD.md §5.5` его называет. Состав
 * напоминания меняется, но ни один приём от переключения тумблера не появляется и
 * не исчезает, поэтому генерация приёмов увидела бы пустую разницу и написала бы в
 * журнал шум. Точка, где тумблер действительно меняет поведение, — постановка
 * будильника и отправка карточки на часы (SKL-13, SKL-16, SKL-17); там значение и
 * читается.
 */
class ToggleWatchMirrorUseCase(private val medications: MedicationRepository, private val log: OperationLog) {

    suspend operator fun invoke(medicationId: String, enabled: Boolean): WatchMirrorOutcome {
        val medication = medications.get(medicationId)
        return when {
            medication == null -> WatchMirrorOutcome.UnknownMedication
            medication.scheduleMode != ScheduleMode.SCHEDULED -> WatchMirrorOutcome.NotApplicable
            medication.isDeleted -> WatchMirrorOutcome.Deleted
            medication.isArchived -> WatchMirrorOutcome.Archived
            // Значение, равное записанному, не пишется и не журналируется: повторное
            // нажатие тумблера в ту же сторону — не событие.
            medication.mirrorToWatch == enabled -> WatchMirrorOutcome.Applied(before = enabled, after = enabled)
            else -> write(medication, enabled)
        }
    }

    private suspend fun write(medication: Medication, enabled: Boolean): WatchMirrorOutcome {
        medications.save(medication.copy(mirrorToWatch = enabled))
        log.record(
            event = OpEvent.MEDICATION_UPDATED,
            profileId = medication.profileId,
            medicationId = medication.id,
            before = medication.mirrorToWatch.toString(),
            after = enabled.toString(),
            detail = DETAIL_WATCH_MIRROR,
        )
        return WatchMirrorOutcome.Applied(before = medication.mirrorToWatch, after = enabled)
    }

    private companion object {
        /** Служебный код журнала: имя изменившегося поля, без значений медицинских данных (НФТ-19). */
        const val DETAIL_WATCH_MIRROR = "mirror_to_watch"
    }
}
