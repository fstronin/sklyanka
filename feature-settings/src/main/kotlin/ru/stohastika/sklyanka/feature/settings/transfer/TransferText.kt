package ru.stohastika.sklyanka.feature.settings.transfer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.transfer.MatchDecision
import ru.stohastika.sklyanka.domain.transfer.MatchDifference
import ru.stohastika.sklyanka.domain.transfer.TransferNotice
import ru.stohastika.sklyanka.domain.transfer.TransferReject
import ru.stohastika.sklyanka.domain.transfer.TransferRejectReason
import ru.stohastika.sklyanka.feature.settings.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Перевод предметных значений в тексты экрана переноса.
 *
 * Все формулировки — из ресурсов (НФТ-42), склонения — через `plurals` (НФТ-44), даты —
 * форматтером с локалью конфигурации, а не конкатенацией (НФТ-43). Собрано в одном файле
 * намеренно: разложенные по экранам, эти соответствия расходятся, и одно и то же решение
 * по совпадению начинает называться по-разному в карточке и в отчёте.
 */

@Composable
internal fun MatchDecision.label(): String = stringResource(
    when (this) {
        MatchDecision.KEEP_MINE -> R.string.transfer_decision_keep_mine
        MatchDecision.TAKE_FROM_FILE -> R.string.transfer_decision_take_from_file
        MatchDecision.ADD_SEPARATELY -> R.string.transfer_decision_add_separately
        MatchDecision.DO_NOT_LOAD -> R.string.transfer_decision_do_not_load
    },
)

/**
 * Строка различия.
 *
 * Время приёма названо значениями с обеих сторон, остальное — самим фактом различия:
 * времена человек сравнивает глазами и по ним узнаёт свой курс, а «связь с едой»
 * в карточке решения ничего не решает.
 */
@Composable
internal fun MatchDifference.label(mine: String, theirs: String): String = when (this) {
    MatchDifference.TIMES -> stringResource(R.string.transfer_match_difference_times, mine, theirs)
    MatchDifference.SCHEDULE_MODE -> stringResource(R.string.transfer_match_difference_schedule_mode)
    MatchDifference.FORM -> stringResource(R.string.transfer_match_difference_form)
    MatchDifference.SHAPE -> stringResource(R.string.transfer_match_difference_shape)
    MatchDifference.DOSE_AMOUNT -> stringResource(R.string.transfer_match_difference_dose_amount)
    MatchDifference.FOOD_RELATION -> stringResource(R.string.transfer_match_difference_food_relation)
    MatchDifference.PERIOD -> stringResource(R.string.transfer_match_difference_period)
    MatchDifference.STOCK -> stringResource(R.string.transfer_match_difference_stock)
    MatchDifference.MAX_PER_DAY -> stringResource(R.string.transfer_match_difference_max_per_day)
}

/** Техническое пояснение одной строкой (ПЕР-28): что не так, без содержимого файла (НФТ-19). */
@Composable
internal fun TransferReject.explanation(): String = when (reason) {
    TransferRejectReason.NOT_JSON -> stringResource(R.string.transfer_reason_not_json)
    TransferRejectReason.MISSING_MARKER -> stringResource(R.string.transfer_reason_missing_marker)
    TransferRejectReason.FOREIGN_MARKER -> stringResource(R.string.transfer_reason_foreign_marker)
    TransferRejectReason.INVALID_VERSION, TransferRejectReason.FUTURE_FORMAT ->
        stringResource(R.string.transfer_reason_invalid_version)

    TransferRejectReason.MISSING_SECTION -> stringResource(R.string.transfer_reason_missing_section, detail)
    TransferRejectReason.INVALID_VALUE -> stringResource(R.string.transfer_reason_invalid_value, detail)
    TransferRejectReason.DUPLICATE_ID -> stringResource(R.string.transfer_reason_duplicate_id, detail)
    TransferRejectReason.BROKEN_LINK -> stringResource(R.string.transfer_reason_broken_link, detail)
    TransferRejectReason.SELF_PROFILE -> stringResource(R.string.transfer_reason_self_profile)
    TransferRejectReason.INVALID_RECORD -> stringResource(R.string.transfer_reason_invalid_record, detail)
}

/**
 * Замечание отчёта человеческим языком (ПЕР-12, МГ-6).
 *
 * @param medicationName название лекарства, к которому относится замена; берётся из уже
 *   загруженного снимка. В журнал оно не попадает — там только идентификатор (НФТ-19).
 */
@Composable
internal fun TransferNotice.label(medicationName: String?): String = when (this) {
    TransferNotice.SoundNotFound -> stringResource(R.string.transfer_report_notice_sound)

    is TransferNotice.UnknownEnum -> if (medicationName == null) {
        stringResource(R.string.transfer_report_notice_enum_settings, fieldLabel(fieldName))
    } else {
        stringResource(R.string.transfer_report_notice_enum, fieldLabel(fieldName), medicationName)
    }

    is TransferNotice.OldFormat -> stringResource(R.string.transfer_report_notice_old_format, fileVersion)

    is TransferNotice.DuplicateIntakes -> stringResource(R.string.transfer_report_intakes_skipped, count)
}

/** Имя поля файла человеческим языком: в отчёте не должно быть латинских имён полей. */
@Composable
private fun fieldLabel(fieldName: String): String = stringResource(
    when (fieldName) {
        "form" -> R.string.transfer_field_form
        "shape" -> R.string.transfer_field_shape
        "food_relation" -> R.string.transfer_field_food_relation
        "schedule_mode" -> R.string.transfer_field_schedule_mode
        "archive_reason" -> R.string.transfer_field_archive_reason
        "status" -> R.string.transfer_field_status
        "source" -> R.string.transfer_field_source
        else -> R.string.transfer_field_unknown
    },
)

@Composable
internal fun medicationsChip(count: Int): String =
    pluralStringResource(R.plurals.transfer_chip_medications, count, count)

@Composable
internal fun archivedChip(count: Int): String = pluralStringResource(R.plurals.transfer_chip_archived, count, count)

@Composable
internal fun historyChip(months: Int): String =
    pluralStringResource(R.plurals.transfer_chip_history_months, months, months)

@Composable
internal fun profilesChip(count: Int): String = pluralStringResource(R.plurals.transfer_chip_profiles, count, count)

@Composable
internal fun intakesCount(count: Int): String = pluralStringResource(R.plurals.transfer_count_intakes, count, count)

/** «2 сентября» — месяц в родительном падеже даёт сам форматтер локали. */
@Composable
internal fun LocalDate.dayAndMonth(): String = format(formatter(R.string.transfer_date_pattern_day_month))

/** «5 октября 2026». */
@Composable
internal fun LocalDate.fullDate(): String = format(formatter(R.string.transfer_date_pattern_full))

/**
 * «5 октября 2026, 21:14» — время выгрузки файла в поясе устройства.
 *
 * Пояс приходит параметром, а не берётся системным: прямые обращения к часам запрещены,
 * и это не формальность — иначе сводку невозможно проверить тестом (НФТ-48).
 */
@Composable
internal fun Instant.dateAndTime(zone: ZoneId): String {
    val local = atZone(zone)
    return stringResource(
        R.string.transfer_summary_moment,
        local.toLocalDate().fullDate(),
        local.toLocalTime().shortTime(),
    )
}

@Composable
internal fun LocalTime.shortTime(): String = format(formatter(R.string.transfer_time_pattern))

/**
 * Список времён приёма одной строкой: «08:00 и 20:00».
 *
 * Форматтер и разделитель берутся до обхода списка: вызов @Composable внутри лямбды
 * `joinToString` невозможен, а строить формат времени руками нельзя — порядок частей
 * задаёт локаль (НФТ-43).
 */
@Composable
internal fun List<LocalTime>.timesLabel(): String {
    if (isEmpty()) return stringResource(R.string.transfer_times_empty)
    val separator = stringResource(R.string.transfer_times_separator)
    val pattern = formatter(R.string.transfer_time_pattern)
    return joinToString(separator) { it.format(pattern) }
}

/**
 * Форматтер с локалью конфигурации.
 *
 * Локаль берётся из конфигурации, а не из `Locale.getDefault()`: при смене языка системы
 * конфигурация обновляется, а статическая локаль процесса — не всегда, и дата остаётся
 * на прежнем языке до перезапуска.
 */
@Composable
private fun formatter(pattern: Int): DateTimeFormatter {
    val text = stringResource(pattern)
    val configuration = LocalConfiguration.current
    return remember(text, configuration) {
        DateTimeFormatter.ofPattern(text, configuration.locales[0])
    }
}
