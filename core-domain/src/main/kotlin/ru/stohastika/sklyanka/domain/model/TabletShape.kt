package ru.stohastika.sklyanka.domain.model

/**
 * Вид таблетки (docs/v2/10-behavior.md §5).
 *
 * Служит узнаванию в списке: человек помнит «та круглая с риской», а не название.
 * На смысл приёма не влияет.
 */
enum class TabletShape {
    ROUND,
    ROUND_SCORED,
    OVAL,
    OBLONG,
    TRIANGULAR,
}
