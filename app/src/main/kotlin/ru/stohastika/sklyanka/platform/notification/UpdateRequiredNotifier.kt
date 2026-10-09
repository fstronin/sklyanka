package ru.stohastika.sklyanka.platform.notification

import android.content.Context
import ru.stohastika.sklyanka.R

/**
 * «Обновите приложение» — первая из двух причин, по которым сообщение о базе показывается
 * (Р-44, `ADR-0012` п. 7, П-7).
 *
 * Отдельный объект, а не `ReminderNotifier`: тот показывает карточку приёма из приёма,
 * лежащего в базе, а читать базу в этом состоянии нельзя. Здесь нет ни приёма, ни действий,
 * ни полноэкранного намерения — только честная просьба.
 */
object UpdateRequiredNotifier {

    /** Идентификатор уведомления: одно на все сработавшие будильники, а не очередь. */
    const val NOTIFICATION_ID = 108

    fun show(context: Context) {
        showDatabaseNotice(
            context = context,
            notificationId = NOTIFICATION_ID,
            titleRes = R.string.database_too_new_notification_title,
            textRes = R.string.database_too_new_notification_text,
        )
    }
}
