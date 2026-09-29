package ru.stohastika.sklyanka.domain.model

/** Причина архивации курса (docs/spec/02-data-model.md §5). Задаёт раздел экрана «Архив». */
enum class ArchiveReason {
    COURSE_FINISHED,
    MANUAL,
}
