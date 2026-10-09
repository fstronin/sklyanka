package ru.stohastika.sklyanka.platform.notification

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import ru.stohastika.sklyanka.R
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale

/**
 * «Обновите приложение» — единственное, что приложение говорит человеку при базе новее
 * установленной сборки (Р-44, `ADR-0012` п. 7, П-7).
 *
 * Затем отдельный объект, а не `ReminderNotifier`: тот показывает карточку приёма из
 * приёма, лежащего в базе, а читать базу в этом состоянии нельзя. Здесь нет ни приёма,
 * ни действий, ни полноэкранного намерения — только просьба обновиться, потому что
 * обещать больше нечего: напоминание отработать не может, а данных этой сборке не видно.
 *
 * Каналом пользуется обычный канал напоминаний: своих каналов продукт не заводит (ПЛ-10),
 * а создаётся он на старте приложения, который в этом состоянии всё равно доходит до
 * создания каналов — они нужны, чтобы сообщить хоть что-то.
 */
object UpdateRequiredNotifier {

    /** Идентификатор уведомления: одно на все сработавшие будильники, а не очередь. */
    const val NOTIFICATION_ID = 108

    fun show(context: Context) {
        // Тот же приём, что у напоминания: текст строится из контекста с закреплённым языком
        // интерфейса, иначе на телефоне с чужим языком сообщение вышло бы не на языке
        // приложения (НФТ-43а).
        val localized = context.withInterfaceLocale()
        val manager = localized.getSystemService(NotificationManager::class.java) ?: return
        val notification = NotificationCompat.Builder(localized, ReminderChannel.REGULAR.id)
            .setSmallIcon(R.drawable.ic_notification_mark)
            .setContentTitle(localized.getString(R.string.database_too_new_notification_title))
            .setContentText(localized.getString(R.string.database_too_new_notification_text))
            // Сообщение остаётся в шторке, пока человек не обновится: это не напоминание
            // о приёме, которое уместно закрыть отметкой или временем.
            .setAutoCancel(false)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}
