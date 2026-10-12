package ru.stohastika.sklyanka.domain.model

/** Причина архивации курса (docs/norm/04-behavior.md §5). Задаёт раздел экрана «Архив». */
enum class ArchiveReason {
    COURSE_FINISHED,
    MANUAL,
}
