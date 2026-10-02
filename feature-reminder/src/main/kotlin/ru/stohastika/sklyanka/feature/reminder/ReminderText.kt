package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.doseUnit
import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckOutcome
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckState
import ru.stohastika.sklyanka.domain.reminder.ReminderDeviationWords
import ru.stohastika.sklyanka.domain.reminder.ReminderEvent
import ru.stohastika.sklyanka.domain.reminder.ReminderJournalExportOutcome
import ru.stohastika.sklyanka.domain.reminder.ReminderLogEntry
import ru.stohastika.sklyanka.domain.reminder.RescheduleReason
import ru.stohastika.sklyanka.domain.reminder.SuppressReason
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.ui.locale.interfaceLocale
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Перевод предметных значений напоминания в тексты интерфейса.
 *
 * Формулировки — из ресурсов (НФТ-42), склонения — через `plurals` (НФТ-44), время —
 * форматтером с шаблоном из ресурсов (НФТ-43). Собрано в одном файле намеренно:
 * разложенные по компонентам, эти соответствия расходятся, и одна и та же доза начинает
 * называться по-разному в карточке и в её строке.
 */

/** «14:00» — время приёма над названием лекарства. */
@Composable
internal fun LocalDateTime.timeLabel(): String =
    format(DateTimeFormatter.ofPattern(stringResource(R.string.reminder_time_pattern), interfaceLocale()))

/** «2 таблетки · 48 мг» — доза и дозировка одной строкой (НАП-29). */
@Composable
internal fun ReminderRow.doseLine(): String {
    val dose = doseLabel(doseAmount, form.doseUnit())
    val strengthValue = strength
    val unit = strengthUnit
    if (strengthValue == null || unit.isNullOrBlank()) return dose
    val strengthLabel = stringResource(R.string.reminder_strength, strengthValue.toPlainString(), unit)
    return dose + stringResource(R.string.reminder_dose_separator) + strengthLabel
}

/**
 * «2 таблетки» — доза с единицей по форме выпуска.
 *
 * Число для склонения берётся целой частью: «1,5 таблетки» склоняется по «1», как
 * и в остальном интерфейсе.
 */
@Composable
private fun doseLabel(amount: BigDecimal, unit: DoseUnit): String = pluralStringResource(
    when (unit) {
        DoseUnit.TABLET -> R.plurals.reminder_dose_tablets
        DoseUnit.CAPSULE -> R.plurals.reminder_dose_capsules
        DoseUnit.MILLILITRE -> R.plurals.reminder_dose_millilitres
        DoseUnit.DOSE -> R.plurals.reminder_dose_doses
    },
    amount.toInt(),
    amount.stripTrailingZeros().toPlainString(),
)

/** «Отложить 15 мин» — подпись собирается из настройки «Отложить на», а не из константы. */
@Composable
internal fun snoozeLabel(minutes: Int): String = stringResource(R.string.reminder_snooze, minutes)

/** Момент в журнале диагностики: «25 сентября, 14:00:37». */
@Composable
internal fun LocalDateTime.journalMoment(): String = format(
    // Локаль указана явно: `ofPattern` без неё берёт локаль процесса, а она остаётся
    // локалью телефона и после закрепления конфигурации (ADR-0008).
    DateTimeFormatter.ofPattern(stringResource(R.string.diagnostics_datetime_pattern), interfaceLocale()),
)

/** Название разрешения строкой диагностики (НАП-37). */
@Composable
internal fun ReminderPermission.label(): String = stringResource(
    when (this) {
        ReminderPermission.NOTIFICATIONS -> R.string.diagnostics_permission_notifications
        ReminderPermission.NOTIFICATION_CHANNEL -> R.string.diagnostics_permission_channel
        ReminderPermission.EXACT_ALARMS -> R.string.diagnostics_permission_exact_alarms
        ReminderPermission.FULL_SCREEN_INTENT -> R.string.diagnostics_permission_full_screen
        ReminderPermission.DND_POLICY_ACCESS -> R.string.diagnostics_permission_dnd
        ReminderPermission.BOOT_RECEIVER -> R.string.diagnostics_permission_boot
        ReminderPermission.AUTOSTART -> R.string.diagnostics_permission_autostart
    },
)

/** «выдано», «не выдано», «проверьте вручную» (НАП-20). */
@Composable
internal fun PermissionState.label(): String = stringResource(
    when (this) {
        PermissionState.GRANTED -> R.string.diagnostics_state_granted
        PermissionState.DENIED -> R.string.diagnostics_state_denied
        PermissionState.UNKNOWN -> R.string.diagnostics_state_unknown
    },
)

/**
 * Причина пересборки плана словами человека (таблица НАП-11).
 *
 * Соответствие держится картой, а не ветвлением: ветвление на четырнадцать причин
 * перестаёт читаться, а полноту карты проверяет тест на JVM — забытая причина обязана
 * валить прогон, а не показывать человеку пустую строку.
 */
internal val REASON_LABELS: Map<RescheduleReason, Int> = mapOf(
    RescheduleReason.BOOT to R.string.diagnostics_reason_boot,
    RescheduleReason.LOCKED_BOOT to R.string.diagnostics_reason_locked_boot,
    RescheduleReason.USER_UNLOCKED to R.string.diagnostics_reason_user_unlocked,
    RescheduleReason.PACKAGE_REPLACED to R.string.diagnostics_reason_package_replaced,
    RescheduleReason.TIMEZONE_CHANGED to R.string.diagnostics_reason_timezone_changed,
    RescheduleReason.TIME_CHANGED to R.string.diagnostics_reason_time_changed,
    RescheduleReason.SCHEDULE_EDITED to R.string.diagnostics_reason_schedule_edited,
    RescheduleReason.MEDICATION_ADDED to R.string.diagnostics_reason_medication_added,
    RescheduleReason.MEDICATION_ARCHIVED to R.string.diagnostics_reason_medication_archived,
    RescheduleReason.MEDICATION_UNARCHIVED to R.string.diagnostics_reason_medication_unarchived,
    RescheduleReason.ALARM_FIRED to R.string.diagnostics_reason_alarm_fired,
    RescheduleReason.PROFILE_SET_CHANGED to R.string.diagnostics_reason_profile_set_changed,
    RescheduleReason.ANCHOR to R.string.diagnostics_reason_anchor,
    RescheduleReason.MANUAL to R.string.diagnostics_reason_manual,
    RescheduleReason.DATA_IMPORTED to R.string.diagnostics_reason_data_imported,
    RescheduleReason.APP_START to R.string.diagnostics_reason_app_start,
    RescheduleReason.EXACT_ALARM_GRANTED to R.string.diagnostics_reason_exact_alarm_granted,
)

@Composable
internal fun RescheduleReason.label(): String = stringResource(REASON_LABELS.getValue(this))

/**
 * Вид записи журнала словами человека (`TDD.md §11.1`).
 *
 * Та же карта вместо ветвления и та же проверка полноты тестом.
 */
internal val EVENT_LABELS: Map<ReminderEvent, Int> = mapOf(
    ReminderEvent.FIRED to R.string.diagnostics_event_fired,
    ReminderEvent.SHOWN to R.string.diagnostics_event_shown,
    ReminderEvent.SUPPRESSED to R.string.diagnostics_event_suppressed,
    ReminderEvent.REPEATED to R.string.diagnostics_event_repeated,
    ReminderEvent.SNOOZED to R.string.diagnostics_event_snoozed,
    ReminderEvent.DEFERRED to R.string.diagnostics_event_deferred,
    ReminderEvent.RESCHEDULED to R.string.diagnostics_event_rescheduled,
    ReminderEvent.MISSED_SWEEP to R.string.diagnostics_event_missed_sweep,
    ReminderEvent.OUTAGE to R.string.diagnostics_event_outage,
    ReminderEvent.SETTINGS_CHANGED to R.string.diagnostics_event_settings_changed,
    ReminderEvent.ENVIRONMENT to R.string.diagnostics_event_environment,
    ReminderEvent.DELIVERY_CHECK to R.string.diagnostics_event_delivery_check,
)

@Composable
internal fun ReminderEvent.label(): String = stringResource(EVENT_LABELS.getValue(this))

/** Вариант измерения пилота словами человека: «Системный будильник» / «Точный будильник». */
@Composable
internal fun AlarmKind.label(): String = stringResource(
    when (this) {
        AlarmKind.ALARM_CLOCK -> R.string.diagnostics_measurement_alarm_clock
        AlarmKind.EXACT_IDLE -> R.string.diagnostics_measurement_exact_idle
    },
)

/**
 * Вид записи журнала словами человека; подавленное по порогу названо отдельно (ОВ-109).
 *
 * Подавленное срабатывание не выдаётся за показ и не называется безлико: запись говорит,
 * что напоминание опоздало и почему человек его не увидел.
 */
@Composable
internal fun ReminderLogEntry.journalLabel(): String =
    if (event == ReminderEvent.SUPPRESSED && reason == SuppressReason.TOO_LATE.code) {
        stringResource(R.string.diagnostics_journal_too_late, latePhrase())
    } else {
        event.label()
    }

/** «с опозданием на N минут» — та же форма, что на первом уровне (НФТ-44). */
@Composable
private fun ReminderLogEntry.latePhrase(): String {
    val minutes = ReminderDeviationWords.lateMinutes(deviationSeconds ?: 0L).toInt()
    return pluralStringResource(R.plurals.diagnostics_late, minutes, minutes)
}

/**
 * Исход самопроверки доставки словами (SKL-86, ОВ-108).
 *
 * Один и тот же текст у блока и у баннера: «тот же результат» из приёмки — это буквально
 * одна формулировка, а не два похожих предложения. Опоздание подставляется склоняемой
 * формой `plurals`, а не числом с «мин» (НФТ-44).
 */
@Composable
internal fun DeliveryCheckState.Finished.resultText(): String = when (outcome) {
    DeliveryCheckOutcome.ON_TIME -> stringResource(R.string.diagnostics_check_on_time)
    DeliveryCheckOutcome.LATE -> stringResource(R.string.diagnostics_check_late, latePhrase())
    DeliveryCheckOutcome.NOT_DELIVERED -> stringResource(R.string.diagnostics_check_not_delivered)
    DeliveryCheckOutcome.NOT_COMPLETED -> stringResource(R.string.diagnostics_check_not_completed)
}

@Composable
private fun DeliveryCheckState.Finished.latePhrase(): String {
    val minutes = (lateMinutes ?: 0L).toInt()
    return pluralStringResource(R.plurals.diagnostics_late, minutes, minutes)
}

/**
 * Исход выгрузки журнала словами (НФТ-51).
 *
 * Число записей — через `plurals` (НФТ-44); размер файла и место, куда он ушёл, человеку
 * не называются: место выбрала система по его просьбе, и приложение его не запоминает.
 */
@Composable
internal fun ReminderJournalExportOutcome.label(): String = when (this) {
    is ReminderJournalExportOutcome.Saved -> stringResource(
        R.string.diagnostics_export_saved,
        pluralStringResource(R.plurals.diagnostics_export_records, records, records),
    )

    ReminderJournalExportOutcome.NotEnoughSpace -> stringResource(R.string.diagnostics_export_no_space)
    ReminderJournalExportOutcome.Failed -> stringResource(R.string.diagnostics_export_failed)
}

/** Момент, с которого напоминания не приходили, в сообщении о перерыве (НАП-38). */
@Composable
internal fun LocalDateTime.outageMoment(): String = format(
    DateTimeFormatter.ofPattern(stringResource(R.string.reminder_outage_notice_moment_pattern), interfaceLocale()),
)
