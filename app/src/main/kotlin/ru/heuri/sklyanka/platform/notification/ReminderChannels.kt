package ru.heuri.sklyanka.platform.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.heuri.sklyanka.R
import ru.heuri.sklyanka.domain.reminder.ReminderChannel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Два статичных канала уведомлений (НАП-24).
 *
 * Статичных — значит создаваемых один раз при старте с фиксированными идентификаторами
 * и **никогда не пересоздаваемых**. Причина не в аккуратности: параметры канала неизменяемы
 * после создания, а удаление канала и создание нового с тем же идентификатором
 * восстанавливает прежний канал со старыми настройками (ПЛ-10). Отсюда же следует, что
 * индивидуальный звук на лекарство невозможен без версионированных каналов и отложен
 * во вторую версию (НАП-27).
 *
 * Канала тихих часов здесь нет: решение по НАП-28 не принято, а канал, который глушит
 * напоминание, прямо противоречит НАП-1.
 *
 * `VISIBILITY_PUBLIC` выбрана сознательно вопреки приватности: напоминание, не показывающее
 * названия лекарства на экране блокировки, не выполняет своей задачи — человек не понимает,
 * что принимать, и вынужден разблокировать телефон (НАП-24).
 */
@Singleton
class ReminderChannels @Inject constructor(@ApplicationContext private val context: Context) {

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
    }

    private fun regular() = NotificationChannel(
        ReminderChannel.REGULAR.id,
        context.getString(R.string.reminder_channel_regular_name),
        NotificationManager.IMPORTANCE_HIGH,
    ).apply {
        description = context.getString(R.string.reminder_channel_regular_description)
        // Штатный сигнал уведомления: своего звукового файла у продукта нет (НАП-27 —
        // общий звук в первой версии), а системный человек уже знает.
        setSound(
            RingtoneManager.getDefaultUri(
                RingtoneManager.TYPE_NOTIFICATION,
            ) ?: Settings.System.DEFAULT_NOTIFICATION_URI,
            alarmAudio(),
        )
        enableVibration(true)
        vibrationPattern = SHORT_PATTERN
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
        vibrationPattern = LONG_PATTERN
        // Действует только при выданном доступе к политике «Не беспокоить» (НАП-17);
        // без него система флаг игнорирует, и диагностика показывает это как ограничение,
        // а не как ошибку.
        setBypassDnd(true)
        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        setShowBadge(false)
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

    private companion object {
        /** Короткий паттерн обычного напоминания (НАП-24). */
        val SHORT_PATTERN = longArrayOf(0, 350, 250, 350)

        /** Длинный выраженный паттерн настойчивого напоминания (НАП-24). */
        val LONG_PATTERN = longArrayOf(0, 700, 300, 700, 300, 700)
    }
}
