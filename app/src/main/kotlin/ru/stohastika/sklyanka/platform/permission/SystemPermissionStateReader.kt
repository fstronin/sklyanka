package ru.stohastika.sklyanka.platform.permission

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.port.PermissionState
import ru.stohastika.sklyanka.domain.reminder.port.PermissionStateReader
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Чтение состояния разрешений из системы (НАП-15, НФТ-52).
 *
 * Каждое обращение спрашивает систему заново и ничего не запоминает: сохранённый флаг
 * устаревает ровно в тот момент, когда человек отозвал разрешение, — то есть диагностика
 * начинает врать именно там, где её открыли, чтобы узнать правду.
 */
@Singleton
class SystemPermissionStateReader @Inject constructor(@ApplicationContext private val context: Context) :
    PermissionStateReader {

    override fun state(permission: ReminderPermission): PermissionState = when (permission) {
        ReminderPermission.NOTIFICATIONS -> granted(NotificationManagerCompat.from(context).areNotificationsEnabled())
        ReminderPermission.NOTIFICATION_CHANNEL -> channelsEnabled()
        ReminderPermission.EXACT_ALARMS -> granted(canScheduleExact())
        ReminderPermission.FULL_SCREEN_INTENT -> fullScreenIntent()
        ReminderPermission.DND_POLICY_ACCESS -> dndPolicyAccess()
        ReminderPermission.BOOT_RECEIVER -> granted(declared(Manifest.permission.RECEIVE_BOOT_COMPLETED))

        // Состояние фирменных ограничений EMUI в публичный API не выставлено (НАП-20, ПЛ-18).
        // Честный ответ — «неизвестно, проверьте вручную», а не зелёная галочка: зелёная
        // галочка там, где приложение не знает ответа, хуже отсутствия строки.
        ReminderPermission.AUTOSTART -> PermissionState.UNKNOWN
    }

    /** До Android 12 разрешения на точные будильники не существует, и это не «не выдано». */
    private fun canScheduleExact(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        requireNotNull(context.getSystemService(AlarmManager::class.java)).canScheduleExactAlarms()

    /**
     * Доступ к политике «Не беспокоить».
     *
     * Контракт экрана относит «Не беспокоить» к ограничениям с нейтральным «проверьте
     * вручную» (AC-10): невыданный доступ возвращается как [PermissionState.UNKNOWN],
     * а не как «не выдано». Выданный доступ остаётся [PermissionState.GRANTED] — тогда
     * строки на экране нет вовсе.
     */
    private fun dndPolicyAccess(): PermissionState = if (notifications().isNotificationPolicyAccessGranted) {
        PermissionState.GRANTED
    } else {
        PermissionState.UNKNOWN
    }

    /**
     * Оба канала напоминаний включены (SKL-79).
     *
     * Выключенный человеком канал — это отказ: напоминание не покажется вовсе. Канала
     * может ещё не быть (приложение не успело создать его при старте) — тогда читать
     * нечего, и честный ответ «не выдано»: строка приведёт человека в системный экран,
     * где канал появится после первого показа.
     */
    private fun channelsEnabled(): PermissionState {
        val manager = notifications()
        val enabled = ReminderChannel.entries.all { channel ->
            manager.getNotificationChannel(channel.id)?.importance != NotificationManager.IMPORTANCE_NONE
        }
        return granted(enabled)
    }

    /**
     * Полноэкранные уведомления.
     *
     * Три состояния платформы, а не два: с Android 14 система спрашивает человека и сама
     * отвечает на вопрос; с Android 10 по 13 право даётся объявлением в манифесте и отозвать
     * его нельзя; до Android 10 разрешения не существует вовсе, и полноэкранное уведомление
     * работает без него — отвечать здесь «не выдано» значило бы пугать человека тем,
     * что у него в порядке.
     */
    private fun fullScreenIntent(): PermissionState = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            granted(notifications().canUseFullScreenIntent())

        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> granted(declared(FULL_SCREEN_INTENT))

        else -> PermissionState.GRANTED
    }

    private fun notifications(): NotificationManager =
        requireNotNull(context.getSystemService(NotificationManager::class.java))

    private fun declared(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun granted(value: Boolean): PermissionState =
        if (value) PermissionState.GRANTED else PermissionState.DENIED

    private companion object {
        /**
         * Имя разрешения строкой, а не константой платформы.
         *
         * Константа `Manifest.permission.USE_FULL_SCREEN_INTENT` появилась в API 29 и при
         * компиляции подставляется значением, поэтому Android Lint справедливо считает её
         * обращением к более новому API (`InlinedApi`, ошибка по конвейеру проекта).
         * Само значение неизменно, а ветвление по версии стоит выше.
         */
        const val FULL_SCREEN_INTENT = "android.permission.USE_FULL_SCREEN_INTENT"
    }
}
