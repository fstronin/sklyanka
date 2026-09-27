package ru.heuri.sklyanka.feature.settings

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

    data class SetPersistentReminder(val enabled: Boolean) : SettingsIntent

    data object OpenSound : SettingsIntent

    data object OpenSnooze : SettingsIntent

    data object OpenWatchSetup : SettingsIntent

    /** Строка «Разрешения и автозапуск» — вход в диагностику напоминаний (решение по `ОВ-48`). */
    data object OpenPermissions : SettingsIntent

    data object OpenHistory : SettingsIntent

    data object OpenDataTransfer : SettingsIntent

    data object OpenToday : SettingsIntent

    data object OpenMedications : SettingsIntent
}
