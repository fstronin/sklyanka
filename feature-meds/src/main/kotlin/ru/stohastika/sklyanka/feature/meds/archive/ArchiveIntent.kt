package ru.stohastika.sklyanka.feature.meds.archive

/** Намерения вкладки «Архив». */
sealed interface ArchiveIntent {

    /** «Активные · N»: возврат к списку активных лекарств. Разбирает маршрут. */
    data object SelectActive : ArchiveIntent

    /** Нажатие на карточку: карточка лекарства в архивном виде (SKL-10). Разбирает маршрут. */
    data class OpenMedication(val medicationId: String) : ArchiveIntent

    /** «Вернуть»: очищает момент и причину архивации вместе и перепланирует приёмы. */
    data class Restore(val medicationId: String) : ArchiveIntent

    /** «Отменить» в снэкбаре удаления: очищает `deleted_at` и возвращает лекарство (ОВ-124). */
    data class UndoDeletion(val medicationId: String) : ArchiveIntent

    data object NoticeDismissed : ArchiveIntent
}
