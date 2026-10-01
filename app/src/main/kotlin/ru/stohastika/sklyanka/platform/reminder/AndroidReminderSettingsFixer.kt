package ru.stohastika.sklyanka.platform.reminder

import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.feature.reminder.ReminderSettingsFixer
import ru.stohastika.sklyanka.platform.onboarding.AndroidSystemSettingsNavigator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Переход «Исправить» к системному экрану разрешения (ОВ-49, НАП-15).
 *
 * Приложение не выдаёт разрешение за человека: оно только приводит его в системный экран,
 * где тот выдаёт право сам. Соответствие разрешения и экрана — таблица `SKL-79/TDD.md §6.2`.
 *
 * Реализация в `app`, а не в `feature-reminder`: экран не должен зависеть от онбординга
 * (`ADR-0007`), а знание системных `Intent` — платформенное.
 */
@Singleton
class AndroidReminderSettingsFixer @Inject constructor(private val navigator: AndroidSystemSettingsNavigator) :
    ReminderSettingsFixer {

    override fun fix(permission: ReminderPermission) {
        when (permission) {
            // Уведомления, оба канала и полноэкранное право живут на системном экране приложения.
            ReminderPermission.NOTIFICATIONS,
            ReminderPermission.NOTIFICATION_CHANNEL,
            ReminderPermission.FULL_SCREEN_INTENT,
            -> navigator.openNotificationSettings()

            ReminderPermission.EXACT_ALARMS -> navigator.openExactAlarmSettings()

            ReminderPermission.DND_POLICY_ACCESS -> navigator.openDndAccessSettings()

            ReminderPermission.AUTOSTART -> navigator.openAutostartSettings()

            // Приёмник загрузки человеку исправлять нечего: состояние объявлено манифестом (ПЛ-9).
            ReminderPermission.BOOT_RECEIVER -> Unit
        }
    }
}
