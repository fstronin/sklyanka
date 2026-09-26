package ru.heuri.sklyanka.platform.reminder

import android.content.Context
import android.content.Intent
import android.net.Uri
import ru.heuri.sklyanka.reminder.AlarmReceiver
import java.time.Instant

/**
 * Адресация будильников напоминаний.
 *
 * Собрана в одном месте, потому что адрес будильника — это контракт между тем, кто его
 * ставит, и тем, кто его снимает. Разъехавшийся адрес не ломает сборку и не виден в тестах
 * на устройстве: будильник просто не снимается, и человек получает напоминание о приёме,
 * которого больше нет.
 *
 * Различаются будильники **данными** (`Uri`), а не кодом запроса: сравнение `PendingIntent`
 * не смотрит на дополнения (`extras`), поэтому два будильника с одинаковым действием
 * и разными дополнениями система считает одним и тем же (документация `PendingIntent`).
 */
internal object ReminderAlarms {

    const val ACTION_INTAKE = "ru.heuri.sklyanka.action.REMINDER_INTAKE"
    const val ACTION_ANCHOR = "ru.heuri.sklyanka.action.REMINDER_ANCHOR"
    const val ACTION_DAY_BOUNDARY = "ru.heuri.sklyanka.action.REMINDER_DAY_BOUNDARY"
    const val ACTION_TEST = "ru.heuri.sklyanka.action.REMINDER_TEST"

    const val EXTRA_INTAKE_ID = "intake_id"
    const val EXTRA_ATTEMPT = "attempt"
    const val EXTRA_PLANNED_AT = "planned_at"

    private const val SCHEME = "sklyanka"

    /** Будильник приёма: один приём — один адрес, повторная постановка заменяет (НАП-9). */
    fun intake(context: Context, intakeId: String, attempt: Int): Intent = Intent(context, AlarmReceiver::class.java)
        .setAction(ACTION_INTAKE)
        .setData(Uri.parse("$SCHEME://reminder/intake/$intakeId"))
        .putExtra(EXTRA_INTAKE_ID, intakeId)
        .putExtra(EXTRA_ATTEMPT, attempt)

    /** Якорный будильник перепланирования (НАП-8). Адрес один: он всегда ровно один. */
    fun anchor(context: Context): Intent = Intent(context, AlarmReceiver::class.java)
        .setAction(ACTION_ANCHOR)
        .setData(Uri.parse("$SCHEME://reminder/anchor"))

    /** Граница суток (НАП-34): перевод просроченных приёмов и снятие их карточек. */
    fun dayBoundary(context: Context): Intent = Intent(context, AlarmReceiver::class.java)
        .setAction(ACTION_DAY_BOUNDARY)
        .setData(Uri.parse("$SCHEME://reminder/day-boundary"))

    /** Пробное напоминание (НАП-22): плановый момент нужен для отклонения в журнале. */
    fun test(context: Context, at: Instant): Intent = Intent(context, AlarmReceiver::class.java)
        .setAction(ACTION_TEST)
        .setData(Uri.parse("$SCHEME://reminder/test"))
        .putExtra(EXTRA_PLANNED_AT, at.toEpochMilli())
}
