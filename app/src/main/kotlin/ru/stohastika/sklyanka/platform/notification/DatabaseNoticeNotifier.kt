package ru.stohastika.sklyanka.platform.notification

import android.app.NotificationManager
import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import ru.stohastika.sklyanka.R
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale

/**
 * Сообщение о базе, работать с которой нельзя: одна форма на обе причины (`ADR-0012` пп. 3, 7).
 *
 * Форма общая намеренно. Отличаются только причина и её слова; всё остальное — закреплённый
 * язык интерфейса (НФТ-43а), канал напоминаний (ПЛ-10), отсутствие действия и полноэкранного
 * намерения, постоянное сообщение в шторке — обязано совпадать дословно. Вторая копия этого
 * тела однажды получила бы не то поведение, и разошлось бы оно не в пользу человека.
 *
 * Каналом пользуется обычный канал напоминаний: своих каналов продукт не заводит (ПЛ-10),
 * а создаётся он на старте приложения, который в этих состояниях всё равно доходит до
 * создания каналов — они нужны, чтобы сообщить хоть что-то.
 */
internal fun showDatabaseNotice(
    context: Context,
    notificationId: Int,
    @StringRes titleRes: Int,
    @StringRes textRes: Int,
) {
    // Тот же приём, что у напоминания: текст строится из контекста с закреплённым языком
    // интерфейса, иначе на телефоне с чужим языком сообщение вышло бы не на языке
    // приложения (НФТ-43а).
    val localized = context.withInterfaceLocale()
    val manager = localized.getSystemService(NotificationManager::class.java) ?: return
    val notification = NotificationCompat.Builder(localized, ReminderChannel.REGULAR.id)
        .setSmallIcon(R.drawable.ic_notification_mark)
        .setContentTitle(localized.getString(titleRes))
        .setContentText(localized.getString(textRes))
        // Сообщение остаётся в шторке, пока причина не устранена: это не напоминание
        // о приёме, которое уместно закрыть отметкой или временем.
        .setAutoCancel(false)
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_ERROR)
        .build()
    runCatching { manager.notify(notificationId, notification) }
}
