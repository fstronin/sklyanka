package ru.heuri.sklyanka.domain.model

/**
 * Режим приёма (docs/spec/02-data-model.md §5).
 *
 * Развилка, от которой зависит половина правил модели: расписание порождает записи
 * заранее, приём по потребности — в момент нажатия «Принять» (ПР-4).
 */
enum class ScheduleMode {
    SCHEDULED,
    PRN,
}
