package ru.stohastika.sklyanka.feature.settings.profile

/**
 * Что человек сделал на экране удаления профиля.
 *
 * Ссылка на место сохранения приходит строкой: её даёт системный выбор, а домен
 * не знает ни про `Uri`, ни про Android вообще (НФТ-46). Намерения ухода с экрана нет
 * в списке переходов: переход решает `app`, а сюда приходит только `Leave` при
 * выбрасывании композиции.
 */
sealed interface DeleteProfileIntent {

    /** Человек набрал слово-подтверждение (ОВ-7); доступность кнопки по нему считает экран. */
    data class WordChanged(val text: String) : DeleteProfileIntent

    /** Место сохранения выбрано системой; путь нигде не сохраняется (ПЕР-36). */
    data class ExportTargetChosen(val target: String) : DeleteProfileIntent

    /**
     * Системный выбор файлов не открылся: обработать это может только экран,
     * потому что чужую активность запускает он.
     */
    data object NoFilePicker : DeleteProfileIntent

    /**
     * Вход на экран: сверить удаляемый профиль с активным (поворот и повторное открытие).
     *
     * ViewModel живёт в Activity и создаётся, как только открыт любой экран второго уровня
     * настроек, поэтому к моменту открытия удаления состояние могло быть снято для другого
     * профиля. Экран обязан начинаться с того профиля, которого называет строка настроек.
     */
    data object Refresh : DeleteProfileIntent

    /** Подтверждение словом: единственная точка, после которой что-то стирается. */
    data object Confirm : DeleteProfileIntent

    /** Отказ выгрузки прочитан. */
    data object DismissFailure : DeleteProfileIntent

    /** Уход с экрана: состояние начинается заново, как перенос (ПЕР-17). */
    data object Leave : DeleteProfileIntent
}
