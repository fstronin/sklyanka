package ru.heuri.sklyanka.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.heuri.sklyanka.data.room.SklyankaConverters
import ru.heuri.sklyanka.data.room.dao.IntakeDao
import ru.heuri.sklyanka.data.room.dao.MedicationDao
import ru.heuri.sklyanka.data.room.entity.IntakeEntity
import ru.heuri.sklyanka.data.room.mapper.Decoded
import ru.heuri.sklyanka.data.room.mapper.decode
import ru.heuri.sklyanka.data.room.mapper.toEntity
import ru.heuri.sklyanka.domain.Transactor
import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.log.OperationLog
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.repository.IntakeRepository
import ru.heuri.sklyanka.domain.time.TimeSource
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Приёмы: выборки в контексте профиля и идемпотентная отметка со списанием остатка.
 *
 * Сутки и периоды считаются по локальной полуночи (НФТ-37) и превращаются в границы
 * текстового диапазона до обращения к базе: арифметики над временем в SQL нет вовсе.
 */
@Singleton
class IntakeRepositoryImpl @Inject constructor(
    private val dao: IntakeDao,
    private val medications: MedicationDao,
    private val transactor: Transactor,
    private val time: TimeSource,
    private val log: OperationLog,
) : IntakeRepository {

    override fun observeDay(profileId: String, date: LocalDate): Flow<List<Intake>> =
        dao.observeDay(profileId, startOfDay(date), startOfDay(date.plusDays(1)))
            .map { entities -> entities.mapNotNull { decodeOrLog(it) } }

    override fun observePeriod(profileId: String, from: LocalDate, to: LocalDate): Flow<List<Intake>> =
        dao.observePeriod(profileId, startOfDay(from), startOfDay(to.plusDays(1)))
            .map { entities -> entities.mapNotNull { decodeOrLog(it) } }

    override fun observeByMedication(
        profileId: String,
        medicationId: String,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<Intake>> =
        dao.observeByMedication(profileId, medicationId, startOfDay(from), startOfDay(to.plusDays(1)))
            .map { entities -> entities.mapNotNull { decodeOrLog(it) } }

    override suspend fun get(intakeId: String): Intake? = dao.getById(intakeId)?.let { decodeOrLog(it) }

    /**
     * Вставка пачкой одной транзакцией.
     *
     * Режим приёма читается у лекарства, потому что от него зависит колонка-слот:
     * приём по расписанию уникален на момент времени, приём по потребности — нет.
     */
    override suspend fun insertMissing(intakes: List<Intake>): Int = transactor.inTransaction {
        if (intakes.isEmpty()) return@inTransaction 0
        val modes = intakes.map { it.medicationId }.distinct().associateWith { id ->
            val stored = medications.getById(id)
            requireNotNull(stored) { "Intake refers to a medication that does not exist: $id" }
            SklyankaConverters.textToEnum<ScheduleMode>(stored.scheduleMode) ?: ScheduleMode.SCHEDULED
        }
        val rows = intakes.map { it.toEntity(requireNotNull(modes[it.medicationId])) }
        val inserted = dao.insertAll(rows).count { it != NOT_INSERTED }
        if (inserted > 0) {
            log.record(
                event = OpEvent.INTAKES_GENERATED,
                medicationId = modes.keys.singleOrNull(),
                after = inserted.toString(),
            )
        }
        inserted
    }

    /**
     * Отметка приёма и списание остатка — вместе или никак (критерий 10, НФТ-11).
     *
     * Порядок шагов важен: сначала условная отметка и только при её успехе — остаток.
     * Обратный порядок дал бы двойное списание в гонке «часы против телефона».
     */
    override suspend fun markIfPending(
        intakeId: String,
        status: IntakeStatus,
        source: IntakeSource,
        actorProfileId: String?,
    ): Boolean = transactor.inTransaction {
        val actedAt = time.now().toEpochMilli()
        val changed = dao.markIfPending(intakeId, status.name, actedAt, source.name, actorProfileId)
        if (changed == 0) {
            log.record(
                event = OpEvent.INTAKE_MARK_IGNORED,
                intakeId = intakeId,
                source = source.name,
                detail = ALREADY_MARKED,
            )
            return@inTransaction false
        }

        val intake = requireNotNull(dao.getById(intakeId))
        if (status == IntakeStatus.TAKEN) decrementStock(intake)
        log.record(
            event = OpEvent.INTAKE_MARKED,
            medicationId = intake.medicationId,
            intakeId = intakeId,
            before = IntakeStatus.PENDING.name,
            after = status.name,
            source = source.name,
        )
        true
    }

    override suspend fun expiredPending(before: LocalDateTime): List<Intake> =
        dao.selectExpiredPending(SklyankaConverters.dateTimeToText(before)).mapNotNull { decodeOrLog(it) }

    /**
     * Списание остатка.
     *
     * Арифметика — в домене: остаток хранится текстом, потому что двоичная дробь не
     * представляет 0,1 точно, а упаковка уменьшается шагами по половине таблетки сотни
     * раз подряд. Ниже нуля остаток не уходит (INV-8).
     */
    private suspend fun decrementStock(intake: IntakeEntity) {
        val medication = medications.getById(intake.medicationId) ?: return
        val stock = SklyankaConverters.textToDecimal(medication.stockLeft) ?: return
        val dose = requireNotNull(SklyankaConverters.textToDecimal(medication.doseAmount))
        val next = (stock - dose).max(BigDecimal.ZERO)
        medications.setStock(medication.id, next.toPlainString())
        log.record(
            event = OpEvent.STOCK_DECREMENTED,
            profileId = medication.profileId,
            medicationId = medication.id,
            intakeId = intake.id,
            before = stock.toPlainString(),
            after = next.toPlainString(),
        )
    }

    private fun startOfDay(date: LocalDate): String = SklyankaConverters.dateTimeToText(date.atStartOfDay())

    private suspend fun decodeOrLog(entity: IntakeEntity): Intake? = when (val decoded = entity.decode()) {
        is Decoded.Value -> decoded.model
        is Decoded.UnknownEnum -> {
            log.record(
                event = OpEvent.ENUM_UNKNOWN,
                medicationId = entity.medicationId,
                intakeId = entity.id,
                detail = decoded.field,
            )
            null
        }
    }

    private companion object {
        /** Room возвращает -1 у строки, которую пропустил конфликт-стратегией IGNORE. */
        const val NOT_INSERTED = -1L
        const val ALREADY_MARKED = "already_marked"
    }
}
