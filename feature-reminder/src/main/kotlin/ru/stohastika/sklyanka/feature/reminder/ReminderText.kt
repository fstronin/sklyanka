package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.doseUnit
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckOutcome
import ru.stohastika.sklyanka.domain.reminder.DeliveryCheckState
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.ui.locale.interfaceLocale
import java.math.BigDecimal
import java.time.LocalDate
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
    val strengthLabel = stringResource(
        R.string.reminder_strength,
        strengthValue.toPlainString(),
        strengthUnitLabel(unit),
    )
    return dose + stringResource(R.string.reminder_dose_separator) + strengthLabel
}

/**
 * Единица дозировки словами языка: канонический код «mg» показывается как «мг»/«毫克»,
 * как в списке и форме лекарства. Остальные единицы — ввод человека и не переводятся.
 */
@Composable
private fun strengthUnitLabel(unit: String): String =
    if (unit == MILLIGRAM_CODE) stringResource(R.string.strength_unit_milligram) else unit

private const val MILLIGRAM_CODE = "mg"

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
 * Последствие строки разрешения словами: чем грозит, а не повтор состояния (ОВ-129, AC-10).
 *
 * `null` — у приёмника загрузки последствия нет: человеку исправлять нечего, и строки
 * у него тоже нет (ПЛ-9).
 */
@Composable
internal fun ReminderPermission.consequence(): String? = when (this) {
    ReminderPermission.NOTIFICATIONS -> stringResource(R.string.diagnostics_consequence_notifications)
    ReminderPermission.NOTIFICATION_CHANNEL -> stringResource(R.string.diagnostics_consequence_channel)
    ReminderPermission.EXACT_ALARMS -> stringResource(R.string.diagnostics_consequence_exact_alarms)
    ReminderPermission.AUTOSTART -> stringResource(R.string.diagnostics_consequence_autostart)
    ReminderPermission.FULL_SCREEN_INTENT -> stringResource(R.string.diagnostics_consequence_full_screen)
    ReminderPermission.DND_POLICY_ACCESS -> stringResource(R.string.diagnostics_consequence_dnd)
    ReminderPermission.BOOT_RECEIVER -> null
}

/**
 * «8:00» — время напоминания на «Проверке напоминаний» (AC-26, AC-27).
 *
 * Формат «H:mm» без ведущего нуля — как в макете; секунды человеку здесь не нужны.
 */
@Composable
internal fun LocalDateTime.diagnosticsClock(): String =
    format(DateTimeFormatter.ofPattern(stringResource(R.string.diagnostics_clock_pattern), interfaceLocale()))

/**
 * Время следующего напоминания: «8:00», «завтра, 8:00» или «26 сентября, 8:00» (AC-27).
 *
 * [today] приходит из состояния экрана, а не из системных часов: экран работает
 * с подменяемым временем (НФТ-48).
 */
@Composable
internal fun LocalDateTime.nextReminderMoment(today: LocalDate): String = when (toLocalDate()) {
    today -> diagnosticsClock()
    today.plusDays(1) -> stringResource(R.string.diagnostics_next_tomorrow, diagnosticsClock())
    else -> format(
        DateTimeFormatter.ofPattern(stringResource(R.string.diagnostics_next_day_pattern), interfaceLocale()),
    )
}

/**
 * Исход самопроверки доставки в блоке «Проверка доставки» (SKL-86, ОВ-108).
 *
 * Успех на первом уровне назван «Последняя проверка пройдена…»: блок говорит об исходе
 * как о последнем. Опоздание подставляется склоняемой формой `plurals`, а не числом
 * с «мин» (НФТ-44).
 */
@Composable
internal fun DeliveryCheckState.Finished.resultText(): String = when (outcome) {
    DeliveryCheckOutcome.ON_TIME -> stringResource(R.string.diagnostics_check_delivered)
    DeliveryCheckOutcome.LATE -> stringResource(R.string.diagnostics_check_late, latePhrase())
    DeliveryCheckOutcome.NOT_DELIVERED -> stringResource(R.string.diagnostics_check_not_delivered)
    DeliveryCheckOutcome.NOT_COMPLETED -> stringResource(R.string.diagnostics_check_not_completed)
}

/**
 * Исход самопроверки для app-нотиса на «Сегодня» (ОВ-131).
 *
 * Текст app-нотиса этой задачей не меняется: успех на «Сегодня» звучит как «Проверка
 * доставки пройдена…», а не как «Последняя проверка…» — это разные поверхности.
 * Остальные исходы у блока и нотиса совпадают.
 */
@Composable
internal fun DeliveryCheckState.Finished.noticeText(): String = when (outcome) {
    DeliveryCheckOutcome.ON_TIME -> stringResource(R.string.diagnostics_check_on_time)
    else -> resultText()
}

@Composable
private fun DeliveryCheckState.Finished.latePhrase(): String {
    val minutes = (lateMinutes ?: 0L).toInt()
    return pluralStringResource(R.plurals.diagnostics_late, minutes, minutes)
}

/** Момент, с которого напоминания не приходили, в сообщении о перерыве (НАП-38). */
@Composable
internal fun LocalDateTime.outageMoment(): String = format(
    DateTimeFormatter.ofPattern(stringResource(R.string.reminder_outage_notice_moment_pattern), interfaceLocale()),
)
