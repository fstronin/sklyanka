package ru.stohastika.sklyanka.platform.notification

import ru.stohastika.sklyanka.domain.reminder.ReminderChannel

/**
 * Звучит ли обновление карточки напоминания.
 *
 * Обновление живой карточки молчит: несколько приёмов одного слота показываются одной
 * карточкой (НАП-36), и повторная отрисовка не имеет права звонить второй раз. Настойчивый
 * повтор — исключение: это новое обращение к человеку, и НАП-25 требует, чтобы оно звучало
 * и вибрировало заметнее первого, даже если карточка всё ещё висит в шторке.
 *
 * Отдельная функция, а не выражение по месту вызова, по образцу [ru.stohastika.sklyanka.reminder.ReminderPlanReasons]:
 * правило проверяется тестом на JVM, хотя само уведомление собирается только на устройстве.
 */
internal fun onlyAlertOnce(channel: ReminderChannel): Boolean =
    channel != ReminderChannel.PERSISTENT
