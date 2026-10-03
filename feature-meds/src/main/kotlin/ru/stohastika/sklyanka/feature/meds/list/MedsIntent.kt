package ru.stohastika.sklyanka.feature.meds.list

/**
 * Намерения экрана «Лекарства».
 *
 * Разметка не меняет состояние сама: у списка есть переключатель вкладок, счётчики
 * и кнопка «Принять», связанные правилами, и хранение части состояния в компонентах
 * означало бы, что счётчик и список расходятся на первом же переключении профиля.
 */
sealed interface MedsIntent {

    /** Переключение «Активные · N» / «Архив · N». */
    data class SelectTab(val tab: MedsTab) : MedsIntent

    /** Нажатие на карточку: открывает карточку лекарства (SKL-10). */
    data class OpenMedication(val medicationId: String) : MedsIntent

    /** Круглая кнопка добавления: открывает форму нового лекарства. */
    data object AddMedication : MedsIntent

    /**
     * «Принять» в карточке «по потребности» (ПР-4).
     *
     * Создаёт принятый приём с назначенным временем, равным фактическому; при
     * исчерпанном суточном ограничении кнопка недоступна и намерение не порождается.
     */
    data class TakeNow(val medicationId: String) : MedsIntent

    /** «Отменить» в снэкбаре удаления: очищает `deleted_at` и возвращает лекарство (ОВ-124). */
    data class UndoDeletion(val medicationId: String) : MedsIntent
}
