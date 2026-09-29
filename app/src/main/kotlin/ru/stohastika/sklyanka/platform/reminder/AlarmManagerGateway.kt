package ru.stohastika.sklyanka.platform.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.stohastika.sklyanka.domain.reminder.PersistentRepeatPolicy
import ru.stohastika.sklyanka.domain.reminder.port.AlarmGateway
import ru.stohastika.sklyanka.domain.reminder.port.AlarmRegistry
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Точные будильники поверх `AlarmManager` (НАП-5).
 *
 * Только `setExactAndAllowWhileIdle`: неточные и повторяющиеся будильники не применяются —
 * первые отдают время на усмотрение системы, вторые на Android 8+ неточны по определению.
 * Doze пробивается тем же вызовом, и это обязательное средство, а не оптимизация (ПЛ-7).
 *
 * Чего здесь нет и не может быть: перечисления поставленных будильников. Android такого
 * API не даёт, поэтому набор хранится в [AlarmRegistry], а существование каждого будильника
 * проверяется пробой `PendingIntent` с `FLAG_NO_CREATE`. Проба и есть ответ на вопрос
 * «план есть, а будильник стоит?» — без неё расхождение остаётся незамеченным, то есть
 * ровно тот отказ, который нужно увидеть (`TDD.md §5.3`).
 */
@Singleton
class AlarmManagerGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    private val registry: AlarmRegistry,
) : AlarmGateway {

    private val alarms: AlarmManager = requireNotNull(context.getSystemService(AlarmManager::class.java))

    /**
     * Поставить или заменить будильник приёма (НАП-5, НАП-25).
     *
     * Номер показа уезжает дополнением намерения: `0` — первое напоминание, `1…` —
     * настойчивый повтор, который звучит настойчивым каналом. Момент записывается
     * в список поставленных будильников под тем же приёмом: пересборка окна судит
     * по нему, снимать ли будильник, а назначенное время приёма после первого показа
     * уже в прошлом и в окно не попадает (НАП-31). Без записи повтор снимался бы
     * первой же пересборкой как осиротевший.
     */
    override suspend fun schedule(intakeId: String, at: Instant, attempt: Int) {
        if (setExact(at, pendingIntent(ReminderAlarms.intake(context, intakeId, attempt)))) {
            registry.put(intakeId, at)
        }
    }

    override suspend fun cancel(intakeId: String) {
        // Снимаются и первый показ, и все повторы: повтор адресуется тем же приёмом,
        // и осиротевший повтор напомнил бы о приёме, выбывшем из плана (НАП-26).
        for (attempt in 0..PersistentRepeatSlots.LAST) {
            existing(ReminderAlarms.intake(context, intakeId, attempt))?.let { pending ->
                alarms.cancel(pending)
                pending.cancel()
            }
        }
        registry.remove(intakeId)
    }

    /**
     * Что действительно стоит в системе.
     *
     * Сохранённый список — подсказка, а решает проба: запись, которой не соответствует
     * живой `PendingIntent`, из ответа исчезает, и пересборка поставит будильник заново.
     * Так выглядит восстановление после перезагрузки и после принудительной остановки.
     */
    override suspend fun scheduled(): Map<String, Instant> = registry.all()
        .filter { (intakeId, _) -> existing(ReminderAlarms.intake(context, intakeId, attempt = 0)) != null }

    override suspend fun scheduleAnchor(at: Instant) {
        setExact(at, pendingIntent(ReminderAlarms.anchor(context)))
    }

    override suspend fun scheduleDayBoundary(at: Instant) {
        setExact(at, pendingIntent(ReminderAlarms.dayBoundary(context)))
    }

    /**
     * Право на точные будильники (НАП-16).
     *
     * До Android 12 разрешение не спрашивают вовсе, поэтому там ответ всегда
     * положительный — это свойство платформы, а не предположение.
     */
    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    /**
     * @return поставлен ли будильник. Отказ системы не роняет приложение и **не** приводит
     *   к подмене точного будильника неточным: молчаливая деградация запрещена (НАП-16),
     *   а предупреждение человеку поднимает пересборка по результату [canScheduleExact].
     */
    private fun setExact(at: Instant, pending: PendingIntent): Boolean = try {
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        true
    } catch (_: SecurityException) {
        false
    }

    private fun pendingIntent(intent: Intent): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun existing(intent: Intent): PendingIntent? = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        /**
         * Код запроса один для всех будильников.
         *
         * Различает их `Uri` в самом намерении (см. [ReminderAlarms]), а не код: код,
         * собранный из хеша идентификатора, даёт совпадения на разных приёмах, и один
         * будильник молча вытесняет другой.
         */
        const val REQUEST_CODE = 0
    }
}

/** Номера показов, под которыми могут стоять будильники одного приёма (НАП-26). */
internal object PersistentRepeatSlots {
    /** Первый показ плюс предел повторов. */
    val LAST: Int = PersistentRepeatPolicy.MAX_REPEATS
}
