package ru.stohastika.sklyanka.ui.component

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Пересчёт даты между моделью и состоянием `DatePicker` (SKL-69, TDD §10.1).
 *
 * M3 `DatePickerState` хранит выбранный день как миллисекунды эпохи **полуночи UTC**.
 * Локальный пояс к этому пересчёту не применяется: иначе у пояса с положительным
 * смещением дата съезжала бы на день назад, и человек выбирал бы один день, а в форме
 * оказывался предыдущий. `startDate`/`endDate` модели остаются локальными календарными
 * датами — это границы, а не моменты (НФТ-34).
 */
internal fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Обратный пересчёт: миллисекунды полуночи UTC в календарную дату без пояса. */
internal fun Long.toLocalDateUtc(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
