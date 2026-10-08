package ru.stohastika.sklyanka.domain.model

/**
 * Источник отметки (docs/v2/10-behavior.md §5).
 *
 * AUTO — перевод в MISSED самим приложением по истечении окна (ПР-1).
 * Источник без журнала невосстановим, поэтому он идёт и в запись приёма, и в op_log.
 */
enum class IntakeSource {
    PHONE,
    WATCH,
    AUTO,
}
