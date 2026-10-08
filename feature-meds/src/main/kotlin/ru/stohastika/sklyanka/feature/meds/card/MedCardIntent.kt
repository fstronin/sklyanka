package ru.stohastika.sklyanka.feature.meds.card

/**
 * Намерения карточки лекарства.
 *
 * Разметка состояние не меняет: ввод остатка и действие архивации
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

    /** «В архив»: причина — «убрано вручную» (ИНВ-9). */
    data object Archive : MedCardIntent

    /** Подтверждение предложения убрать законченный курс: причина — «курс завершён» (ОВ-42). */
    data object ArchiveFinishedCourse : MedCardIntent

    /** «Вернуть» у архивированного лекарства (SKL-11). */
    data object Restore : MedCardIntent

    /** «Удалить»: открывает шторку подтверждения (ОВ-124). */
    data object DeleteRequested : MedCardIntent

    /** «Отмена», системная «Назад» и тап вне шторки: закрывают её без изменений. */
    data object DeleteDismissed : MedCardIntent

    /** Подтверждение шторки: мягкое удаление и окно «Отменить» (ПМ-24, ОВ-124). */
    data object DeleteConfirmed : MedCardIntent

    data object NoticeDismissed : MedCardIntent
}
