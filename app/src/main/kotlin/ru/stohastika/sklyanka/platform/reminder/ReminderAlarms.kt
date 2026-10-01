package ru.stohastika.sklyanka.platform.reminder

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import ru.stohastika.sklyanka.reminder.AlarmReceiver

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

    const val ACTION_INTAKE = "ru.stohastika.sklyanka.action.REMINDER_INTAKE"
    const val ACTION_ANCHOR = "ru.stohastika.sklyanka.action.REMINDER_ANCHOR"
    const val ACTION_DAY_BOUNDARY = "ru.stohastika.sklyanka.action.REMINDER_DAY_BOUNDARY"

    /** Тестовое срабатывание самопроверки доставки (SKL-86, ОВ-108). */
    const val ACTION_DELIVERY_CHECK = "ru.stohastika.sklyanka.action.REMINDER_DELIVERY_CHECK"

    const val EXTRA_INTAKE_ID = "intake_id"
    const val EXTRA_ATTEMPT = "attempt"

    private const val SCHEME = "sklyanka"

    /**
     * Код запроса один для всех будильников.
     *
     * Различает их `Uri` в самом намерении, а не код: код, собранный из хеша идентификатора,
     * даёт совпадения на разных приёмах, и один будильник молча вытесняет другой.
     */
    const val REQUEST_CODE = 0

    /**
     * `PendingIntent` будильника: повторная постановка заменяет прежний (НАП-9).
     *
     * Вынесено сюда из `AlarmManagerGateway`, потому что восстановитель в locked-фазе обязан
     * адресовать будильники тем же построением, каким их ставит планировщик. Разъехавшийся
     * код запроса или флаги не ломают сборку и не видны в тестах: адреса расходятся молча,
     * и после разблокировки пересборка либо плодит вторые будильники, либо не снимает
     * восстановленные (НАП-9).
     */
    fun pendingIntent(context: Context, intent: Intent): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Проба существования будильника тем же адресом; `null` — его нет в системе. */
    fun existing(context: Context, intent: Intent): PendingIntent? = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

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

    /** Адрес тестового срабатывания: один на проверку, повторная постановка заменяет (НАП-9). */
    fun deliveryCheck(context: Context): Intent = Intent(context, AlarmReceiver::class.java)
        .setAction(ACTION_DELIVERY_CHECK)
        .setData(Uri.parse("$SCHEME://reminder/delivery-check"))
}
