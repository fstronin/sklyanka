package ru.heuri.sklyanka.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.heuri.sklyanka.data.room.dao.MedicationDao
import ru.heuri.sklyanka.data.room.entity.MedicationEntity
import ru.heuri.sklyanka.data.room.mapper.Decoded
import ru.heuri.sklyanka.data.room.mapper.decode
import ru.heuri.sklyanka.data.room.mapper.toEntity
import ru.heuri.sklyanka.domain.log.OpEvent
import ru.heuri.sklyanka.domain.log.OperationLog
import ru.heuri.sklyanka.domain.model.ArchiveReason
import ru.heuri.sklyanka.domain.model.Invariants
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.repository.MedicationRepository
import ru.heuri.sklyanka.domain.time.TimeSource
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Лекарства и курсы.
 *
 * Строка с непонятым значением перечисления не роняет выборку: она выпадает из списка,
 * а в журнал уходит ENUM_UNKNOWN с именем поля и идентификатором записи — без названия
 * лекарства (МГ-6, НФТ-19).
 */
@Singleton
class MedicationRepositoryImpl @Inject constructor(
    private val dao: MedicationDao,
    private val time: TimeSource,
    private val log: OperationLog,
) : MedicationRepository {

    override fun observeActive(profileId: String): Flow<List<Medication>> =
        dao.observeActive(profileId).map { entities -> entities.mapNotNull { decodeOrLog(it) } }

    override fun observeArchived(profileId: String): Flow<List<Medication>> =
        dao.observeArchived(profileId).map { entities -> entities.mapNotNull { decodeOrLog(it) } }

    override suspend fun get(medicationId: String): Medication? = dao.getById(medicationId)?.let { decodeOrLog(it) }

    override suspend fun save(medication: Medication) {
        Invariants.requireValid(Invariants.violations(medication))
        dao.upsert(medication.toEntity())
    }

    override suspend fun archive(medicationId: String, reason: ArchiveReason): Boolean {
        val at = time.now().toEpochMilli()
        val changed = dao.archive(medicationId, at, reason.name)
        if (changed == 0) return false
        val medication = dao.getById(medicationId)
        log.record(
            event = OpEvent.MEDICATION_ARCHIVED,
            profileId = medication?.profileId,
            medicationId = medicationId,
            after = reason.name,
        )
        return true
    }

    override suspend fun setStock(medicationId: String, stockLeft: BigDecimal?) {
        dao.setStock(medicationId, stockLeft?.toPlainString())
    }

    private suspend fun decodeOrLog(entity: MedicationEntity): Medication? = when (val decoded = entity.decode()) {
        is Decoded.Value -> decoded.model
        is Decoded.UnknownEnum -> {
            log.record(
                event = OpEvent.ENUM_UNKNOWN,
                profileId = entity.profileId,
                medicationId = entity.id,
                detail = decoded.field,
            )
            null
        }
    }
}
