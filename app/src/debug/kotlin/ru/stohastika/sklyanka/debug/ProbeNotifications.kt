package ru.stohastika.sklyanka.debug

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import ru.stohastika.sklyanka.R

/**
 * Нейтральное уведомление пробы (SKL-89).
 *
 * Отдельный тестовый канал и никаких медицинских данных: показ уведомления — это и есть
 * доказательство срабатывания (НАП-2), но человеку в шторке не должно приходить ничего,
 * что можно принять за настоящее напоминание о приёме. Названия канала и уведомления —
 * в `src/debug/res/values/strings.xml`; в релизный артефакт файл не попадает.
 */
internal object ProbeNotifications {

    private const val NOTIFICATION_ID_BASE = 89_000

    fun show(context: Context, probe: Int) {
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        manager.createNotificationChannel(
            NotificationChannel(
                ProbeProtocol.CHANNEL_ID,
                context.getString(R.string.debug_probe_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        val notification = NotificationCompat.Builder(context, ProbeProtocol.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_mark)
            .setContentTitle(context.getString(R.string.debug_probe_notification_title, probe))
            .setContentText(context.getString(R.string.debug_probe_notification_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID_BASE + probe, notification)
    }
}
