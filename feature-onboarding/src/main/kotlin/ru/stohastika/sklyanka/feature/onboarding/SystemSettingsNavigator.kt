package ru.stohastika.sklyanka.feature.onboarding

import ru.stohastika.sklyanka.domain.onboarding.AutostartOpenResult

/**
 * Переходы в системные настройки (`TDD.md §5.2`).
 *
 * Интерфейс объявлен здесь, а реализация живёт в `app` и подставляется через Hilt — тем же
 * способом, что `ReminderCheckNow` в SKL-13. Причина не в чистоте: экран, собирающий `Intent`
 * сам, начинает знать имена Activity чужих прошивок, и знание расползается по экранам
 * ровно тогда, когда прошивка их переименует.
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
    fun openExactAlarmSettings()

    /** Настройки уведомлений приложения — когда диалог запроса система больше не покажет. */
    fun openNotificationSettings()

    /**
     * Экран управления запуском приложений производителя (ПЛ-18).
     *
     * @return открылся ли экран; [AutostartOpenResult.FALLBACK_TO_INSTRUCTIONS] означает,
     * что ни один известный адрес не сработал и человеку нужно показать текстовую инструкцию.
     */
    fun openAutostartSettings(): AutostartOpenResult
}
