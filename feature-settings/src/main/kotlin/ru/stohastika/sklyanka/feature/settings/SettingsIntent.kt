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
     * Переключатель строки «Аналитика» (ПМ-25, Р-96).
     *
     * Включение открывает вопрос о согласии и пишет показ, но ничего не разрешает само:
     * решает только кнопка «Разрешить». Выключение — мгновенное и без диалога: отказ
     * не должен требовать подтверждения, как и сам вопрос (АН-1, АН-2).
     */
    data class SetAnalyticsEnabled(val enabled: Boolean) : SettingsIntent

    /** Вопрос закрыт без решения: выбор не меняется, очередь остаётся как была (АН-2). */
    data object DismissAnalyticsPrompt : SettingsIntent

    /** «Разрешить» в вопросе: согласие даётся здесь и только здесь (ПМ-25 п. 4). */
    data object ConfirmAnalytics : SettingsIntent

    /**
     * Строка блока «Склянка Про» — открыть витрину (SKL-122, Р-67 п. 6).
     *
     * Шторку хостит `app`, а не настройки: поверхность покупки одна на все точки входа,
     * и заводить её копию в модуле настроек значило бы получить вторую поверхность (Р-67).
     */
    data object OpenPro : SettingsIntent

    /**
     * Строка «Акцентная тема» (§5.17, Р-31, SKL-120).
     *
     * Открывает диалог выбора всегда — и без доступа тоже: «Про» стоит у платных строк
     * диалога, а не у поверхности, и человек имеет право увидеть набор до покупки
     * (Р-67 п. 5, П-7). Исход решает домен на **выборе**, а не на входе.
     */
    data object OpenThemePicker : SettingsIntent

    /** Закрыть выбор темы без изменения («Отмена», системная «Назад», тап вне окна). */
    data object DismissThemePicker : SettingsIntent

    /** Выбрана акцентная тема: применяется сразу, без «Сохранить» (§5.17). */
    data class SelectTheme(val theme: AccentTheme) : SettingsIntent

    /** Строка «Иконка приложения» — тот же диалог и тот же гейт на выборе, что у темы (§5.17). */
    data object OpenIconPicker : SettingsIntent

    /** Закрыть выбор иконки без изменения. */
    data object DismissIconPicker : SettingsIntent

    /** Выбран вариант иконки: применяется сразу (§5.17, §2.4). */
    data class SelectIconVariant(val variant: IconVariant) : SettingsIntent

    data object OpenToday : SettingsIntent

    data object OpenMedications : SettingsIntent
}
