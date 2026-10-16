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
     * Строка «Экспорт истории» — открыть шторку выгрузки или витрину (SKL-119, Р-35).
     *
     * Одно намерение на оба исхода: доступ проверяет поверхность, которая шторку и собирает, —
     * настройки о подписке не решают и шторки не рисуют (§5.16). Шторка живёт в `feature-history`
     * и хостится `app` поверх корня, как витрина (Р-67).
     */
    data object OpenHistoryExport : SettingsIntent

    /** «Политика конфиденциальности» — системный переход в браузер (ОВ-123). */
    data object OpenPolicy : SettingsIntent

    /**
     * Строка блока «Склянка Про» — открыть витрину (SKL-122, Р-67 п. 6).
     *
     * Шторку хостит `app`, а не настройки: поверхность покупки одна на все точки входа,
     * и заводить её копию в модуле настроек значило бы получить вторую поверхность (Р-67).
     */
    data object OpenPro : SettingsIntent

    data object OpenToday : SettingsIntent

    data object OpenMedications : SettingsIntent
}
