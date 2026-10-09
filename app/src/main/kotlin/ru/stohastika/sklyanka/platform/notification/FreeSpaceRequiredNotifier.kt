package ru.stohastika.sklyanka.platform.notification

import android.content.Context
import ru.stohastika.sklyanka.R

/**
 * «Не хватает места» — вторая из двух причин, по которым сообщение о базе показывается
 * (`ADR-0012` п. 3, П-7).
 *
 * Отдельно от [UpdateRequiredNotifier] намеренно: там человеку нужно обновить приложение,
 * здесь — освободить место, и сообщение не по причине бесполезно. Отдельно от
 * `ReminderNotifier` по той же причине, что и у обновления: тот показывает карточку приёма
 * из приёма, лежащего в базе, которую открывать нельзя.
 */
object FreeSpaceRequiredNotifier {

    /** Идентификатор уведомления: одно на все сработавшие будильники, а не очередь. */
    const val NOTIFICATION_ID = 109

    fun show(context: Context) {
        showDatabaseNotice(
            context = context,
            notificationId = NOTIFICATION_ID,
            titleRes = R.string.database_no_space_notification_title,
            textRes = R.string.database_no_space_notification_text,
        )
    }
}
