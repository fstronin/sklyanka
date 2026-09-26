package ru.heuri.sklyanka.domain.model

/**
 * Вид таблетки (docs/spec/02-data-model.md §5).
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
