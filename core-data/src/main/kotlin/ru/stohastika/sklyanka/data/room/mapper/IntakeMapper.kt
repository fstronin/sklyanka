package ru.stohastika.sklyanka.data.room.mapper

import ru.stohastika.sklyanka.data.room.SklyankaConverters
import ru.stohastika.sklyanka.data.room.entity.IntakeEntity
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.ScheduleMode

/** Чтение приёма. Незнакомый статус или источник останавливают чтение строки (МГ-6). */
@Suppress("ReturnCount")
fun IntakeEntity.decode(): Decoded<Intake> {
    val decodedStatus = SklyankaConverters.textToEnum<IntakeStatus>(status)
        ?: return Decoded.UnknownEnum("status")
    val decodedSource = source?.let {
        SklyankaConverters.textToEnum<IntakeSource>(it) ?: return Decoded.UnknownEnum("source")
    }

    return Decoded.Value(
        Intake(
            id = id,
            medicationId = medicationId,
            scheduledAt = SklyankaConverters.textToDateTime(scheduledAt),
            status = decodedStatus,
            actedAt = SklyankaConverters.millisToInstant(actedAt),
            source = decodedSource,
            actorProfileId = actorProfileId,
        ),
    )
}

/**
 * Запись приёма.
 *
 * Режим приёма приходит снаружи, потому что колонка-слот — свойство не приёма,
 * а его лекарства: приём по расписанию занимает слот и потому уникален на момент
 * времени (INV-13), приём по потребности слота не занимает и может повториться
 * в ту же минуту (INV-14).
 */
fun Intake.toEntity(scheduleMode: ScheduleMode): IntakeEntity = IntakeEntity(
    id = id,
    medicationId = medicationId,
    scheduledAt = SklyankaConverters.dateTimeToText(scheduledAt),
    scheduleSlot = if (scheduleMode == ScheduleMode.SCHEDULED) IntakeEntity.SCHEDULE_SLOT else null,
    status = status.name,
    actedAt = SklyankaConverters.instantToMillis(actedAt),
    source = source?.name,
    actorProfileId = actorProfileId,
)
