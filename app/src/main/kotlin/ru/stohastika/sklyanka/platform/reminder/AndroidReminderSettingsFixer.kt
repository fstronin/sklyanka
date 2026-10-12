package ru.stohastika.sklyanka.platform.reminder

import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.stohastika.sklyanka.domain.onboarding.PermissionStep
import ru.stohastika.sklyanka.domain.onboarding.StepOpenResult
import ru.stohastika.sklyanka.domain.onboarding.VendorProfileReader
import ru.stohastika.sklyanka.domain.reminder.ReminderChannel
import ru.stohastika.sklyanka.domain.reminder.port.ReminderPermission
import ru.stohastika.sklyanka.feature.reminder.ReminderFixResult
import ru.stohastika.sklyanka.feature.reminder.ReminderSettingsFixer
import ru.stohastika.sklyanka.platform.onboarding.AndroidSystemSettingsNavigator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Переход «Исправить» к системному экрану разрешения (ОВ-49, НАП-15, ОВ-106).
 *
 * Приложение не выдаёт разрешение за человека: оно только приводит его в системный экран,
 * где тот выдаёт право сам. Соответствие разрешения и экрана — таблица `SKL-79/TDD.md §6.2`
 * и `SKL-84/TDD.md §9.4`.
 *
 * Исход возвращается наружу: «системный экран не открылся» — не то же самое, что «открылся»,
 * и экран диагностики показывает запасную инструкцию вместо молчания (НФТ-8).
 *
 * Реализация в `app`, а не в `feature-reminder`: экран не должен зависеть от онбординга
 * (`ADR-0007`), а знание системных `Intent` — платформенное.
 */
@Singleton
class AndroidReminderSettingsFixer @Inject constructor(
    private val navigator: AndroidSystemSettingsNavigator,
    private val vendor: VendorProfileReader,
    @ApplicationContext private val context: Context,
) : ReminderSettingsFixer {

    override fun fix(permission: ReminderPermission): ReminderFixResult = when (permission) {
        // Уведомления живут на системном экране приложения.
        ReminderPermission.NOTIFICATIONS -> navigator.openNotificationSettings().toFixResult()

        // Канал открывается сразу выключенный: человеку не нужно искать его в списке (ОВ-107).
        ReminderPermission.NOTIFICATION_CHANNEL ->
            navigator.openNotificationSettings(disabledChannelId()).toFixResult()

        // Полноэкранное право с Android 14 — отдельный экран, ниже его нет.
        ReminderPermission.FULL_SCREEN_INTENT -> navigator.openFullScreenIntentSettings().toFixResult()

        ReminderPermission.EXACT_ALARMS -> navigator.openExactAlarmSettings().toFixResult()

        // Исход политики «Не беспокоить» не используется: инструкции для неё нет (НАП-17).
        ReminderPermission.DND_POLICY_ACCESS -> {
            navigator.openDndAccessSettings()
            ReminderFixResult.OPENED
        }

        ReminderPermission.AUTOSTART ->
            navigator.openVendorStep(vendor.profile(), PermissionStep.APP_STARTUP).toFixResult()

        // Приёмник загрузки человеку исправлять нечего: состояние объявлено манифестом (ПЛ-9).
        ReminderPermission.BOOT_RECEIVER -> ReminderFixResult.NO_SCREEN
    }

    /**
     * Первый выключенный канал напоминаний — на него и ведёт «Исправить».
     *
     * Каналов два, и выключен может быть любой; человеку нужен тот, из-за которого
     * напоминание не показывается, а не общий список. Нет выключенных или канал ещё
     * не создан — `null`: экран уведомлений откроется без выбранного канала.
     */
    private fun disabledChannelId(): String? {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return null
        return ReminderChannel.entries
            .firstOrNull { manager.getNotificationChannel(it.id)?.importance == NotificationManager.IMPORTANCE_NONE }
            ?.id
    }

    private fun StepOpenResult.toFixResult(): ReminderFixResult = when (this) {
        StepOpenResult.OPENED -> ReminderFixResult.OPENED
        StepOpenResult.FALLBACK_TO_INSTRUCTIONS -> ReminderFixResult.FALLBACK_TO_INSTRUCTIONS
    }
}
