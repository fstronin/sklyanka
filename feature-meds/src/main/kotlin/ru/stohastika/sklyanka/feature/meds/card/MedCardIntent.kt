package ru.stohastika.sklyanka.feature.meds.card

/**
 * Намерения карточки лекарства.
 *
 * Разметка состояние не меняет: переключатель часов, ввод остатка и действие архивации
 * связаны правилами домена, и хранение части состояния в компонентах означало бы, что
 * экран показывает одно, а база хранит другое.
 */
sealed interface MedCardIntent {

    /** Стрелка «Назад»: возврат в список лекарств. Разбирает маршрут. */
    data object Back : MedCardIntent

    /** «Изменить»: та же форма, что и заведение лекарства (SKL-09). Разбирает маршрут. */
    data object Edit : MedCardIntent

    /** «Пополнить»: открывает ввод нового остатка. */
    data object RefillRequested : MedCardIntent

    data class RefillValueChanged(val text: String) : MedCardIntent

    data object RefillConfirmed : MedCardIntent

    data object RefillCancelled : MedCardIntent

    /** Переключатель «Напоминать на часах» (ЧАС-15). */
    data class WatchMirrorToggled(val enabled: Boolean) : MedCardIntent

    /** «В архив»: причина — «убрано вручную» (ИНВ-9). */
    data object Archive : MedCardIntent

    /** Подтверждение предложения убрать законченный курс: причина — «курс завершён» (ОВ-42). */
    data object ArchiveFinishedCourse : MedCardIntent

    /** «Вернуть» у архивированного лекарства (SKL-11). */
    data object Restore : MedCardIntent

    data object NoticeDismissed : MedCardIntent
}
