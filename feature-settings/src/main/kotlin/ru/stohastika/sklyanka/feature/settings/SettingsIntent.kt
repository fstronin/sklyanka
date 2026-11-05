package ru.stohastika.sklyanka.feature.settings

import ru.stohastika.sklyanka.domain.appearance.AccentTheme
import ru.stohastika.sklyanka.domain.appearance.IconVariant

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
     * «Изменить» в строке профиля — открыть экран правки этого профиля (§5.14, SKL-121).
     *
     * Идентификатор обязателен: правка встаёт у того профиля, который правят, **в том числе
     * у неактивного** — чтобы исправить имя или цвет, в профиль не переключаются (ПРФ-19).
     */
    data class EditProfile(val profileId: String) : SettingsIntent

    /**
     * «Удалить профиль «Имя»» — переход на экран подтверждения (ОВ-7, SKL-36).
     *
     * Строка появляется только у активного чужого профиля: удалить можно ровно тот,
     * в контексте которого человек находится, — а свой не удаляется вовсе (ПРФ-28).
     */
    data object DeleteProfile : SettingsIntent

    data class SetPersistentReminder(val enabled: Boolean) : SettingsIntent

    /** Переход строки «Системные настройки уведомлений» в системный экран приложения (ОВ-98, Р-52). */
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

    /**
     * Строка «Экспорт истории» — открыть шторку выгрузки (SKL-119, Р-35).
     *
     * Настройки шторки не рисуют: она живёт в `feature-history` и хостится `app`
     * поверх корня (§5.16).
     */
    data object OpenHistoryExport : SettingsIntent

    /** «Политика конфиденциальности» — системный переход в браузер (ОВ-123). */
    data object OpenPolicy : SettingsIntent

    /**
     * Строка «Акцентная тема» (§5.17, Р-31, SKL-120).
     *
     * Открывает диалог выбора: человек видит весь набор до нажатия, а выбор применяется
     * сразу (§5.17).
     */
    data object OpenThemePicker : SettingsIntent

    /** Закрыть выбор темы без изменения («Отмена», системная «Назад», тап вне окна). */
    data object DismissThemePicker : SettingsIntent

    /** Выбрана акцентная тема: применяется сразу, без «Сохранить» (§5.17). */
    data class SelectTheme(val theme: AccentTheme) : SettingsIntent

    /** Строка «Иконка приложения» — тот же диалог, что у темы (§5.17). */
    data object OpenIconPicker : SettingsIntent

    /** Закрыть выбор иконки без изменения. */
    data object DismissIconPicker : SettingsIntent

    /** Выбран вариант иконки: применяется сразу (§5.17, §2.4). */
    data class SelectIconVariant(val variant: IconVariant) : SettingsIntent

    data object OpenToday : SettingsIntent

    data object OpenMedications : SettingsIntent
}
