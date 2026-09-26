package ru.heuri.sklyanka.domain.reminder

import ru.heuri.sklyanka.domain.reminder.port.PermissionState
import ru.heuri.sklyanka.domain.reminder.port.PermissionStateReader
import ru.heuri.sklyanka.domain.reminder.port.ReminderPermission

/**
 * Чего не хватает, чтобы напоминания были надёжными (НАП-18, НАП-21).
 *
 * Перечисление, а не булев флаг «всё плохо»: человеку нужно сказать, что именно сделать,
 * а «напоминания ненадёжны» без причины — это предупреждение, которое нечем закрыть.
 */
enum class ReminderWarning {
    /** Уведомления отозваны: напоминание планируется, срабатывает и не показывается. */
    NOTIFICATIONS_OFF,

    /** Нет права на точные будильники: система сдвигает срабатывание (НАП-16). */
    EXACT_ALARMS_OFF,

    /** Не выдано ни то, ни другое. */
    BOTH_OFF,
}

/**
 * Состояние надёжности напоминаний.
 *
 * Читается из системы при каждом обращении (НФТ-52) и показывается там, где человек
 * бывает: «Сегодня» (SKL-09 владеет экраном) и диагностика. Отказ в разрешении не делает
 * ни один экран неработающим — приложение продолжает работать как ручной журнал (НАП-18).
 */
data class ReminderReliability(val notificationsEnabled: Boolean, val exactAlarmsAllowed: Boolean) {
    val warning: ReminderWarning? = when {
        !notificationsEnabled && !exactAlarmsAllowed -> ReminderWarning.BOTH_OFF
        !notificationsEnabled -> ReminderWarning.NOTIFICATIONS_OFF
        !exactAlarmsAllowed -> ReminderWarning.EXACT_ALARMS_OFF
        else -> null
    }

    val isReliable: Boolean get() = warning == null
}

/** Сборка состояния надёжности из состояния разрешений системы. */
class ReadReminderReliabilityUseCase(private val permissions: PermissionStateReader) {

    operator fun invoke(): ReminderReliability = ReminderReliability(
        notificationsEnabled = permissions.state(ReminderPermission.NOTIFICATIONS) == PermissionState.GRANTED,
        exactAlarmsAllowed = permissions.state(ReminderPermission.EXACT_ALARMS) == PermissionState.GRANTED,
    )
}
