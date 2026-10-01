package ru.stohastika.sklyanka.feature.onboarding

import ru.stohastika.sklyanka.domain.onboarding.StepOpenResult

/**
 * Переходы в системные настройки (`TDD.md §5.7`).
 *
 * Интерфейс объявлен здесь, а реализация живёт в `app` и подставляется через Hilt — тем же
 * способом, что `ReminderCheckNow` в SKL-13. Причина не в чистоте: экран, собирающий `Intent`
 * сам, начинает знать имена Activity чужих прошивок, и знание расползается по экранам
 * ровно тогда, когда прошивка их переименует.
 *
 * Каждый метод возвращает [StepOpenResult]: «системный экран не открылся» — не исключение,
 * а исход, при котором экран показывает запасную инструкцию (НФТ-8).
 *
 * Системный запрос разрешения на уведомления сюда не входит: он требует Activity
 * и контракта результата, поэтому живёт в хозяине экрана
 * ([ru.stohastika.sklyanka.feature.onboarding.PermissionsRoute]).
 */
interface SystemSettingsNavigator {

    /**
     * Экран особого доступа «Будильники и напоминания» (ПЛ-14).
     *
     * Системного диалога у этого права нет: его выдают на экране настроек, и приложение
     * может только туда привести.
     */
    fun openExactAlarmSettings(): StepOpenResult

    /**
     * Настройки уведомлений приложения; с [channelId] — сразу конкретный канал (ОВ-107).
     *
     * @param channelId канал, который нужно показать человеку; `null` — общий экран приложения.
     */
    fun openNotificationSettings(channelId: String? = null): StepOpenResult

    /** Экран права на полноэкранные уведомления — Android 14 и новее (ОВ-107). */
    fun openFullScreenIntentSettings(): StepOpenResult

    /** Экран управления запуском приложений производителя (ПЛ-18). */
    fun openAppStartupSettings(): StepOpenResult

    /** Экран ограничений энергосбережения приложения (ПЛ-6). */
    fun openBatterySavingSettings(): StepOpenResult
}
