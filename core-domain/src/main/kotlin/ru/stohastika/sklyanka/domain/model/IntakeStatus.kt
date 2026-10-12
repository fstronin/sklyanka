package ru.stohastika.sklyanka.domain.model

/**
 * Состояние приёма (docs/norm/04-behavior.md §6).
 *
 * SKIPPED и MISSED различаются содержательно: первое — решение человека,
 * второе — отсутствие решения. В отчёте врачу они не смешиваются.
 */
enum class IntakeStatus {
    PENDING,
    TAKEN,
    SKIPPED,
    MISSED,
}
