package ru.stohastika.sklyanka.domain.model

/**
 * Режим приёма (docs/norm/04-behavior.md §5).
 *
 * Развилка, от которой зависит половина правил модели: расписание порождает записи
 * заранее, приём по потребности — в момент нажатия «Принять» (ПР-4).
 */
enum class ScheduleMode {
    SCHEDULED,
    PRN,
}
