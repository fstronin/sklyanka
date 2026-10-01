package ru.stohastika.sklyanka.platform.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.stohastika.sklyanka.MainActivity
import ru.stohastika.sklyanka.domain.reminder.AlarmKind
import ru.stohastika.sklyanka.domain.reminder.DirectBootAlarm
import ru.stohastika.sklyanka.domain.reminder.PersistentRepeatPolicy
import ru.stohastika.sklyanka.domain.reminder.port.AlarmGateway
import ru.stohastika.sklyanka.domain.reminder.port.AlarmRegistry
import ru.stohastika.sklyanka.domain.reminder.port.DirectBootAlarmStore
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Будильники поверх `AlarmManager` (НАП-5).
 *
 * Напоминания о приёмах ставятся `setAlarmClock` — он получает исключение из Doze
 * и заморозки, система его не сдвигает, а признак в статус-баре показывает ближайшее
 * напоминание (ОВ-110). Служебные будильники (якорь и граница суток) остаются
 * `setExactAndAllowWhileIdle` и признак не показывают (НАП-42). Неточные и повторяющиеся
 * будильники не применяются — первые отдают время на усмотрение системы, вторые
 * на Android 8+ неточны по определению.
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
    private val strategy: EffectiveAlarmStrategy,
    private val directBoot: DirectBootAlarmStore,
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
     *
     * Механизм выбирается на месте, а номер показа остаётся единственным параметром
     * различия показов: адрес будильника прежний, поэтому переключение варианта
     * измерения не оставляет «второго» будильника (НАП-9).
     */
    override suspend fun schedule(intakeId: String, at: Instant, attempt: Int) {
        val pending = ReminderAlarms.pendingIntent(context, ReminderAlarms.intake(context, intakeId, attempt))
        if (placeExact(at, pending)) {
            registry.put(intakeId, at)
            // Зеркало в DE-хранилище обновляется в той же точке, где будильник ставится:
            // восстановление до разблокировки читает снимок, а не CE-реестр (SKL-87).
            runCatching { directBoot.put(DirectBootAlarm(intakeId, at, attempt)) }
        }
    }

    /**
     * Постановка будильника действующим механизмом по произвольному адресу (SKL-89).
     *
     * Вынесено из [schedule] ради измерительного пробника: он обязан ставить тестовые
     * срабатывания тем же вызовом, каким живёт продукт. Дубль ветвления в debug-сборке
     * разошёлся бы с продуктом при первой же правке механизма и мерил бы сам себя.
     * Виден только внутри модуля: стенд живёт в `src/debug` той же сборки, а в релизном
     * артефакте потребителя у метода нет.
     */
    internal suspend fun placeExact(at: Instant, pending: PendingIntent): Boolean = when (strategy.kind()) {
        AlarmKind.ALARM_CLOCK -> setAlarmClock(at, pending)
        AlarmKind.EXACT_IDLE -> setExact(at, pending)
    }

    override suspend fun cancel(intakeId: String) {
        // Снимаются и первый показ, и все повторы: повтор адресуется тем же приёмом,
        // и осиротевший повтор напомнил бы о приёме, выбывшем из плана (НАП-26).
        for (attempt in 0..PersistentRepeatSlots.LAST) {
            ReminderAlarms.existing(context, ReminderAlarms.intake(context, intakeId, attempt))?.let { pending ->
                alarms.cancel(pending)
                pending.cancel()
            }
        }
        registry.remove(intakeId)
        runCatching { directBoot.remove(intakeId) }
    }

    /**
     * Полная пересборка оставляет в зеркале ровно то, что стоит в системе.
     *
     * Состав окна приходит из плана, но запись решает проба `PendingIntent`: она отсеивает
     * и будильники, которым не удалось встать, и осиротевшие записи прежнего снимка,
     * которые не смогли сняться. Записи, уже известные зеркалу, сохраняются, поэтому
     * восстановленный до разблокировки будильник не пропадает, даже если в текущее окно
     * он не попал. Пустой дифф пересборки — не повод пропустить вызов: именно он
     * заполняет снимок после обновления, когда все будильники уже стояли.
     */
    override suspend fun syncMirror(placed: List<DirectBootAlarm>) {
        runCatching {
            val recorded = directBoot.load()?.alarms.orEmpty().associateBy { it.intakeId }
            val standing = (recorded + placed.associateBy { it.intakeId }).values.filter { alarm ->
                ReminderAlarms.existing(context, ReminderAlarms.intake(context, alarm.intakeId, alarm.attempt)) != null
            }
            directBoot.replace(standing)
        }
    }

    /**
     * Что действительно стоит в системе.
     *
     * Сохранённый список — подсказка, а решает проба: запись, которой не соответствует
     * живой `PendingIntent`, из ответа исчезает, и пересборка поставит будильник заново.
     * Так выглядит восстановление после перезагрузки и после принудительной остановки.
     *
     * К CE-реестру добавляется DE-зеркало: будильник, восстановленный в locked-фазе, в реестр
     * не попадает, и без зеркала пересборка после разблокировки считала бы его несуществующим —
     * оставив после перелёта осиротевшее срабатывание, против которого написан SKL-13.
     * Зеркало при расхождении важнее реестра: записи расходятся только при отказе записи
     * в одно из хранилищ или при locked-восстановлении, и в обоих случаях актуальнее зеркало.
     */
    override suspend fun scheduled(): Map<String, Instant> {
        val replayed = directBoot.load()?.alarms.orEmpty().associate { it.intakeId to it.at }
        return (registry.all() + replayed)
            .filter { (intakeId, _) ->
                ReminderAlarms.existing(context, ReminderAlarms.intake(context, intakeId, attempt = 0)) != null
            }
    }

    override suspend fun scheduleAnchor(at: Instant) {
        setExact(at, ReminderAlarms.pendingIntent(context, ReminderAlarms.anchor(context)))
    }

    /**
     * Тестовое срабатывание самопроверки (SKL-86, ОВ-108).
     *
     * Ставится тем же точным вызовом, что боевое напоминание: проверка обязана мерить
     * настоящий путь доставки, а не его облегчённую копию. В [AlarmRegistry] и DE-зеркало
     * запись не попадает: проверка не приём, и пересборка окна не имеет права видеть
     * в ней осиротевший будильник.
     */
    override suspend fun scheduleDeliveryCheck(at: Instant) {
        placeExact(at, ReminderAlarms.pendingIntent(context, ReminderAlarms.deliveryCheck(context)))
    }

    override suspend fun cancelDeliveryCheck() {
        ReminderAlarms.existing(context, ReminderAlarms.deliveryCheck(context))?.let { pending ->
            alarms.cancel(pending)
            pending.cancel()
        }
    }

    override suspend fun scheduleDayBoundary(at: Instant) {
        setExact(at, ReminderAlarms.pendingIntent(context, ReminderAlarms.dayBoundary(context)))
    }

    /**
     * Право на точные будильники (НАП-16).
     *
     * До Android 12 разрешение не спрашивают вовсе, поэтому там ответ всегда
     * положительный — это свойство платформы, а не предположение.
     */
    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    /** Действующий механизм: тот же, что только что ставил будильник (SKL-85). */
    override suspend fun alarmKind(): AlarmKind = strategy.kind()

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

    /**
     * Системный будильник приёма: doze-exempt, не сдвигается, виден в статус-баре (ОВ-110).
     *
     * `showIntent` — переход по нажатию на признак будильника: он ведёт в приложение,
     * а не в карточку конкретного приёма, потому что выбор ближайшего будильника
     * при нескольких приложениях остаётся за системой (`TDD.md §9.1`).
     */
    private fun setAlarmClock(at: Instant, pending: PendingIntent): Boolean = try {
        alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at.toEpochMilli(), alarmClockShowIntent()), pending)
        true
    } catch (_: SecurityException) {
        false
    }

    private fun alarmClockShowIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        SHOW_REQUEST_CODE,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        /**
         * Код запроса show-намерения системного будильника.
         *
         * Отдельный от [ReminderAlarms.REQUEST_CODE]: тот адресует broadcast'ы будильников
         * приёма, а этот — activity, открываемую нажатием на признак в статус-баре; общий код
         * дал бы `PendingIntent`-коллизию между разными видами намерений.
         */
        const val SHOW_REQUEST_CODE = 1
    }
}

/** Номера показов, под которыми могут стоять будильники одного приёма (НАП-26). */
internal object PersistentRepeatSlots {
    /** Первый показ плюс предел повторов. */
    val LAST: Int = PersistentRepeatPolicy.MAX_REPEATS
}
