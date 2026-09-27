package ru.heuri.sklyanka.feature.settings.transfer

import ru.heuri.sklyanka.domain.transfer.MatchDecision
import ru.heuri.sklyanka.domain.transfer.TransferMode

/**
 * Намерения человека на экране переноса.
 *
 * Ссылки на файл и на место сохранения приходят строками: экран получает их от системного
 * выбора, а домен не знает ни про `Uri`, ни про Android вообще (НФТ-46).
 */
sealed interface TransferIntent {

    data class SelectMode(val mode: TransferMode) : TransferIntent

    /** Место сохранения выбрано системой; путь нигде не сохраняется (ПЕР-36). */
    data class SaveTo(val target: String) : TransferIntent

    /** Файл выбран системой: дальше проверка и показ сводки, и ничего больше (ПЕР-15 п. 2). */
    data class FileChosen(val source: String) : TransferIntent

    /** Сводка прочитана: дальше разбор совпадений либо подтверждение замены. */
    data object ContinueFromSummary : TransferIntent

    data class Decide(val fileMedicationId: String, val decision: MatchDecision) : TransferIntent

    /** Выход из длинного списка совпадений: не загружать ничего из совпавших (ПЕР-22). */
    data object DropAllMatches : TransferIntent

    /** Подтверждение загрузки: единственная точка, после которой что-то пишется. */
    data object Apply : TransferIntent

    /** Отчёт прочитан: дальше экран настройки разрешений (ПЕР-25). */
    data object AcknowledgeReport : TransferIntent

    /** Отказ прочитан. */
    data object DismissFailure : TransferIntent

    /**
     * Системный выбор файлов не открылся: обрабатывать его отсутствие может только экран,
     * потому что чужую активность запускает он.
     */
    data object NoFilePicker : TransferIntent

    /**
     * Уход с экрана.
     *
     * Загрузка не переживает уход с экрана (ПЕР-17): состояние сбрасывается, и при
     * возвращении перенос начинается сначала.
     */
    data object Leave : TransferIntent
}
