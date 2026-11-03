package ru.stohastika.sklyanka.platform.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.stohastika.sklyanka.R
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.ReminderVibration
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Два статичных канала уведомлений (НАП-24).
 *
 * Статичных — значит создаваемых один раз при старте с фиксированными идентификаторами
 * и **никогда не пересоздаваемых**. Причина не в аккуратности: параметры канала неизменяемы
 * после создания, а удаление канала и создание нового с тем же идентификатором
 * восстанавливает прежний канал со старыми настройками (ПЛ-10). Там же причина версии
 * у обычного канала: свой сигнал (Р-99) поставлен после публикации, поэтому новый звук живёт
 * под новым идентификатором, а канал прежнего выпуска убирается.
 *
 * Индивидуальный звук на лекарство по-прежнему невозможен без канала на лекарство (НАП-27):
 * версионируется звук продукта, а не лекарства.
 *
 * Канала тихих часов здесь нет: решение по НАП-28 не принято, а канал, который глушит
 * напоминание, прямо противоречит НАП-1.
 *
 * `VISIBILITY_PUBLIC` выбрана сознательно вопреки приватности: напоминание, не показывающее
 * названия лекарства на экране блокировки, не выполняет своей задачи — человек не понимает,
 * что принимать, и вынужден разблокировать телефон (НАП-24).
 */
@Singleton
class ReminderChannels @Inject constructor(@ApplicationContext applicationContext: Context) {

    /**
     * Контекст с закреплённым языком интерфейса (НФТ-43а, `ADR-0008`).
     *
     * Названия и описания каналов человек читает в системных настройках телефона, и на
     * телефоне с чужим языком они пришли бы на его языке — точнее, русские строки
     * с чужими правилами. Канал создаётся один раз и не пересоздаётся (ПЛ-10), поэтому
     * язык названия выбирается ровно один раз в жизни установки.
     */
    private val context: Context = applicationContext.withInterfaceLocale()

    private val manager: NotificationManager =
        requireNotNull(context.getSystemService(NotificationManager::class.java))

    /**
     * Создание каналов при старте приложения.
     *
     * Идемпотентно средствами системы: `createNotificationChannel` для существующего
     * канала меняет только название и описание, а важность, звук и вибрацию не трогает.
     */
    fun ensure() {
        manager.createNotificationChannel(regular())
        manager.createNotificationChannel(persistent())
        removeRetiredRegularChannel()
    }

    private fun regular() = NotificationChannel(
        ReminderChannel.REGULAR.id,
        context.getString(R.string.reminder_channel_regular_name),
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = context.getString(R.string.reminder_channel_regular_description)
        // Свой короткий сигнал (Р-99): системный сигнал уведомления звучит у всех приложений,
        // и на EMUI человек не отличал напоминание о приёме от любого другого уведомления.
        setSound(ownSound(), alarmAudio())
        enableVibration(true)
        vibrationPattern = ReminderVibration.of(ReminderChannel.REGULAR)
        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        setShowBadge(false)
    }

    private fun persistent() = NotificationChannel(
        ReminderChannel.PERSISTENT.id,
        context.getString(R.string.reminder_channel_persistent_name),
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = context.getString(R.string.reminder_channel_persistent_description)
        // Сигнал будильника, а не уведомления: повтор об уже пропущенном приёме — ровно
        // та ситуация, ради которой продукт существует, и он обязан быть заметнее первого.
        setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: Settings.System.DEFAULT_ALARM_ALERT_URI,
            alarmAudio(),
        )
        enableVibration(true)
        vibrationPattern = ReminderVibration.of(ReminderChannel.PERSISTENT)
        // Действует только при выданном доступе к политике «Не беспокоить» (НАП-17);
        // без него система флаг игнорирует, и диагностика показывает это как ограничение,
        // а не как ошибку.
        setBypassDnd(true)
        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        setShowBadge(false)
    }

    /**
     * Свой сигнал продукта — короткий двухтоновый сигнал из `res/raw/reminder_chime.wav` (Р-99).
     *
     * URI собирается по имени ресурса, а не по числовому идентификатору: канал хранит строку
     * и перечитывает её при каждом срабатывании, а числовой идентификатор меняется от сборки
     * к сборке — после обновления приложения канал остался бы без звука.
     *
     * Схема `android.resource` читается системой из пакета приложения: файл лежит в APK,
     * и ни разрешений, ни копии на диске не нужно.
     */
    private fun ownSound(): Uri = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(context.packageName)
        .appendPath(context.resources.getResourceTypeName(R.raw.reminder_chime))
        .appendPath(context.resources.getResourceEntryName(R.raw.reminder_chime))
        .build()

    /**
     * Убирает обычный канал прежнего выпуска (Р-99).
     *
     * Прежний канал звучит системным сигналом уведомления и больше не используется: иначе
     * человек искал бы напоминание в двух пунктах системных настроек. Удаление канала снимает
     * и его уведомления, поэтому висящее в шторке напоминание — повод отложить уборку
     * до следующего запуска: показанное напоминание это обещание, а не мусор (НАП-1).
     */
    private fun removeRetiredRegularChannel() {
        val retired = ReminderChannel.RETIRED_REGULAR_ID
        if (manager.getNotificationChannel(retired) == null) return
        val hasLiveNotification = manager.activeNotifications.any { it.notification?.channelId == retired }
        if (hasLiveNotification) return
        manager.deleteNotificationChannel(retired)
    }

    /**
     * Звук напоминания идёт по назначению «будильник», а не «уведомление».
     *
     * Иначе он глушится вместе с прочими уведомлениями и подчиняется их громкости —
     * то есть напоминание о приёме теряется в потоке сообщений (НАП-1).
     */
    private fun alarmAudio(): AudioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setUsage(AudioAttributes.USAGE_ALARM)
        .build()
}
