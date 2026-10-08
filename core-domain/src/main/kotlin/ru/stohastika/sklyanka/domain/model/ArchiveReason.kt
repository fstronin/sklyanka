package ru.stohastika.sklyanka.domain.model

/** Причина архивации курса (docs/v2/10-behavior.md §5). Задаёт раздел экрана «Архив». */
enum class ArchiveReason {
    COURSE_FINISHED,
    MANUAL,
}
