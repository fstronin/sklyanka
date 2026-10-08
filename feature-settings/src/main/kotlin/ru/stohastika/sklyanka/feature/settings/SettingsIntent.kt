package ru.stohastika.sklyanka.feature.settings

/**
 * Что человек сделал на экране настроек.
 *
 * Переходы наружу — тоже намерения, а не вызовы: `feature-settings` не зависит от других
 * экранных модулей, и решать, куда ведёт строка, обязан тот, кто собирает маршруты, то
 * есть `app` (`TDD.md §3`).
 */
sealed interface SettingsIntent {

    /** Выбран профиль в списке: переключение — осознанное отдельное действие (ПРФ-19). */
    data class SelectProfile(val profileId: String) : SettingsIntent

    data object AddProfile : SettingsIntent

    /**
     * «Удалить профиль «Имя»» — переход на экран подтверждения (ОВ-7, SKL-36).
     *
     * Строка появляется только у активного чужого профиля: удалить можно ровно тот,
     * в контексте которого человек находится, — а свой не удаляется вовсе (ПРФ-28).
     */
    data object DeleteProfile : SettingsIntent

    data class SetPersistentReminder(val enabled: Boolean) : SettingsIntent

    /** «Звук» — переход в системные настройки уведомлений приложения (ОВ-98). */
    data object OpenSound : SettingsIntent

    /** «Отложить на» — открыть выбор интервала из [ru.stohastika.sklyanka.domain.reminder.SnoozeIntervals]. */
    data object OpenSnooze : SettingsIntent

    /** Закрыть выбор интервала без изменения («Отмена», системная «Назад», тап вне окна). */
    data object DismissSnooze : SettingsIntent

    /** Выбран интервал откладывания в минутах (ОВ-98). */
    data class SetSnooze(val minutes: Int) : SettingsIntent

    /** Строка «Разрешения и автозапуск» — вход в диагностику напоминаний (решение по `ОВ-48`). */
    data object OpenPermissions : SettingsIntent

    data object OpenHistory : SettingsIntent

    data object OpenDataTransfer : SettingsIntent

    /** «Политика конфиденциальности» — системный переход в браузер (ОВ-123). */
    data object OpenPolicy : SettingsIntent

    data object OpenToday : SettingsIntent

    data object OpenMedications : SettingsIntent
}
