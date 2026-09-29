package ru.stohastika.sklyanka.domain.log

import java.time.Instant

/** Одна запись журнала. Момент — в UTC: это факт, а не правило. */
@Suppress("LongParameterList")
data class OpLogRecord(
    val id: Long,
    val at: Instant,
    val event: OpEvent,
    val profileId: String?,
    val medicationId: String?,
    val intakeId: String?,
    val before: String?,
    val after: String?,
    val source: String?,
    val detail: String?,
)
