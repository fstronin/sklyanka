package ru.stohastika.sklyanka.feature.settings.transfer

import ru.stohastika.sklyanka.domain.transfer.MatchDecision
import ru.stohastika.sklyanka.domain.transfer.MedicationMatch
import ru.stohastika.sklyanka.domain.transfer.TransferCounts
import ru.stohastika.sklyanka.domain.transfer.TransferMode
import ru.stohastika.sklyanka.domain.transfer.TransferReject
import ru.stohastika.sklyanka.domain.transfer.TransferReport
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Шаг переноса (ПЕР-15, ПЕР-16).
 *
 * Шаги — перечисление, а не набор булевых флагов: флаги позволяют собрать «показан отчёт
 * и одновременно идёт разбор совпадений», а порядок шагов здесь и есть содержание
 * требования — между «человек выбрал файл» и «данные применены» обязан лежать показ
 * и подтверждение.
 */
enum class TransferStep {
    /** Экран переноса: выгрузка и выбор режима загрузки. */
    OVERVIEW,

    /** Сводка выбранного файла. Общий шаг для обоих режимов (ПЕР-15 п. 3). */
    SUMMARY,

    /** Разбор совпадений: по одному решению на совпадение (ПЕР-21, ПЕР-22). */
    MATCHES,

    /** Подтверждение замены всего с числами стираемого (ПЕР-16 п. 2). */
    REPLACE_CONFIRM,

    /** Отчёт о загрузке (ПЕР-15 п. 7). */
    REPORT,
}

/**
 * Отказ, о котором надо сказать человеку.
 *
 * Отдельный тип, а не строка: текст собирает экран из ресурсов (НФТ-42), а решение
 * «какое сообщение показать» предметное. Сообщений о файле ровно два (ПЕР-28, ПЕР-29),
 * и техническое пояснение к первому — одна строка.
 */
sealed interface TransferFailure {

    /** «Файл не похож на выгрузку Склянки» плюс пояснение одной строкой (ПЕР-28). */
    data class NotAnExport(val reject: TransferReject) : TransferFailure

    /** «Файл создан более новой версией Склянки. Обновите приложение» (ПЕР-29). */
    data object FromFuture : TransferFailure

    /** Места недостаточно — проверено до начала записи (ПЕР-31). */
    data object NoSpace : TransferFailure

    /** Загрузка не состоялась: транзакция откатилась, состояние прежнее (ПЕР-27). */
    data object ImportNotApplied : TransferFailure

    /** Выгрузка не состоялась; готового файла в выбранном месте нет (ПЕР-32). */
    data object ExportFailed : TransferFailure

    /**
     * Системного выбора файлов на устройстве нет.
     *
     * Выбор места и файла делает чужая активность, и на устройстве, где её нет вовсе,
     * запрос на неё — отказ, а не исключение: приложение, падающее по нажатию кнопки,
     * теряет доверие целиком, а не только к переносу данных.
     */
    data object NoFilePicker : TransferFailure
}

/**
 * Состояние экрана «Перенос данных».
 *
 * Снимок выбранного файла живёт только здесь, в памяти экрана: загрузка не уходит в фон
 * и не переживает уход с экрана (ПЕР-17), а значит и состояние не должно его переживать.
 */
data class TransferUiState(
    val step: TransferStep = TransferStep.OVERVIEW,
    /** Состав текущих данных устройства: из него собраны чипы карточки выгрузки (критерий 3). */
    val deviceCounts: TransferCounts? = null,
    /**
     * Глубина истории в месяцах — чип «история за N месяцев» из макета.
     *
     * Считается от самой ранней записи до сегодняшней даты и приходит готовой: экран
     * системных часов не читает (НФТ-48).
     */
    val deviceHistoryMonths: Int? = null,
    val exportFileName: String = "",
    /** Null означает «выгрузки не было ни разу»: строки о ней на экране нет (критерий 5). */
    val lastExportDate: LocalDate? = null,
    val mode: TransferMode = TransferMode.MERGE,
    val busy: Boolean = false,
    val fileCounts: TransferCounts? = null,
    val fileExportedAt: Instant? = null,
    val oldFormatVersion: Int? = null,
    val matches: List<MedicationMatch> = emptyList(),
    /**
     * Название лекарства по его идентификатору — для замечаний отчёта.
     *
     * Названия нужны только экрану: в журнал уходят одни идентификаторы (НФТ-19), а сказать
     * человеку «форма выпуска не распознана», не назвав лекарства, значит не сказать ничего.
     */
    val medicationNames: Map<String, String> = emptyMap(),
    val decisions: Map<String, MatchDecision> = emptyMap(),
    /**
     * Набранное слово-подтверждение замены всего (ОВ-67).
     *
     * Живёт в состоянии, а не в памяти экрана: значение обязано переживать поворот,
     * а состояние экрана — единственное место, где оно это делает по конвенции модуля.
     * Доступность кнопки по нему считает экран: слово-образец — ресурс, а ViewModel
     * ресурсов не читает (НФТ-42). Уход с экрана (`Leave`) начинается с нового
     * состояния, и слово очищается вместе с переносом (ПЕР-17).
     */
    val confirmationWord: String = "",
    val report: TransferReport? = null,
    val safetyCopyName: String? = null,
    val failure: TransferFailure? = null,
    /** Прошлый запуск оборвался посреди загрузки: об этом надо сказать один раз (ПЕР-31). */
    val importDidNotHappen: Boolean = false,
    /**
     * Пояс устройства: нужен, чтобы показать время выгрузки файла.
     *
     * Приходит из инжектируемых часов, а не из системных: прямые обращения к часам
     * запрещены и в экранах тоже (НФТ-48).
     */
    val zone: ZoneId = ZoneId.of("UTC"),
) {
    /** Решение по совпадению: до выбора человека действует «оставить моё» (ПЕР-21). */
    fun decisionFor(fileMedicationId: String): MatchDecision = decisions[fileMedicationId] ?: MatchDecision.DEFAULT
}
