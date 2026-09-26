package ru.heuri.sklyanka.domain.schedule

/**
 * Причина запуска генерации — перечень событий НАП-11 плюс старт приложения.
 *
 * Уходит в журнал служебным кодом: без причины запись «создано 7 приёмов» не отвечает
 * на вопрос «почему они появились именно сейчас» (`TDD.md §9`).
 */
enum class PlanReason(val code: String) {
    APP_START("app_start"),
    BOOT("boot"),
    TIMEZONE_CHANGED("timezone_changed"),
    TIME_CHANGED("time_changed"),
    MEDICATION_SAVED("medication_saved"),
    MEDICATION_ARCHIVED("medication_archived"),
    ANCHOR_ALARM("anchor_alarm"),
    MANUAL_CHECK("manual_check"),
}
