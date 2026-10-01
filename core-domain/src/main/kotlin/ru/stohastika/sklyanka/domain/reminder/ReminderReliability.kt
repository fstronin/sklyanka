package ru.stohastika.sklyanka.domain.reminder

import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.PermissionStateReader
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission

/**
 * Чего не хватает, чтобы напоминания были надёжными (НАП-18, НАП-21, ОВ-107).
 *
 * Перечисление, а не булев флаг «всё плохо»: человеку нужно сказать, что именно сделать,
 * а «напоминания ненадёжны» без причины — это предупреждение, которое нечем закрыть.
 * Одно и то же перечисление называют карточка «Настроить надёжность», баннер «Сегодня»
 * и диагностика — слова не могут разойтись между экранами.
 */
enum class ReminderWarning {
    /** Уведомления отозваны: напоминание планируется, срабатывает и не показывается. */
    NOTIFICATIONS_OFF,

    /** Канал уведомлений приложения выключен — напоминание не покажется вовсе (SKL-79). */
    NOTIFICATION_CHANNEL_OFF,

    /** Нет права на точные будильники: система сдвигает срабатывание (НАП-16). */
    EXACT_ALARMS_OFF,

    /** Не выдано право на полноэкранные уведомления: карточка не появится на заблокированном экране. */
    FULL_SCREEN_INTENT_OFF,

    /** Устоявшаяся пара «уведомления + точные будильники» — существующий текст ОВ-82. */
    BOTH_OFF,
}

/**
 * Состояние надёжности напоминаний.
 *
 * Читается из системы при каждом обращении (НФТ-52) и показывается там, где человек
 * бывает: «Сегодня» и диагностика. Отказ в разрешении не делает ни один экран
 * неработающим — приложение продолжает работать как ручной журнал (НАП-18).
 *
 * Порядок причин — решение ОВ-107: «не приходят» (уведомления, канал) выше «опаздывают»
 * (точные будильники) выше «показываются не полностью» (полноэкранные). Полноэкранные
 * называются, только когда всё остальное в порядке: иначе человек чинил бы третье,
 * пока не работает первое. При выключенных уведомлениях канал не называется отдельно —
 * он внутри разрешения.
 */
data class ReminderReliability(
    val notificationsEnabled: Boolean,
    val channelEnabled: Boolean,
    val exactAlarmsAllowed: Boolean,
    val fullScreenIntentAllowed: Boolean,
) {
    val warning: ReminderWarning? = when {
        !notificationsEnabled && !exactAlarmsAllowed -> ReminderWarning.BOTH_OFF
        !notificationsEnabled -> ReminderWarning.NOTIFICATIONS_OFF
        !channelEnabled -> ReminderWarning.NOTIFICATION_CHANNEL_OFF
        !exactAlarmsAllowed -> ReminderWarning.EXACT_ALARMS_OFF
        !fullScreenIntentAllowed -> ReminderWarning.FULL_SCREEN_INTENT_OFF
        else -> null
    }

    val isReliable: Boolean get() = warning == null
}

/**
 * Сборка состояния надёжности из состояния разрешений системы.
 *
 * Автозапуск и политика «Не беспокоить» в причины баннера не входят: первый системой
 * не читается вовсе (НАП-20), второй не мешает доставке — напоминание приходит, пусть
 * и беззвучно (НАП-17).
 */
class ReadReminderReliabilityUseCase(private val permissions: PermissionStateReader) {

    operator fun invoke(): ReminderReliability = ReminderReliability(
        notificationsEnabled = permissions.state(ReminderPermission.NOTIFICATIONS) == PermissionState.GRANTED,
        channelEnabled = permissions.state(ReminderPermission.NOTIFICATION_CHANNEL) == PermissionState.GRANTED,
        exactAlarmsAllowed = permissions.state(ReminderPermission.EXACT_ALARMS) == PermissionState.GRANTED,
        fullScreenIntentAllowed = permissions.state(ReminderPermission.FULL_SCREEN_INTENT) == PermissionState.GRANTED,
    )
}
