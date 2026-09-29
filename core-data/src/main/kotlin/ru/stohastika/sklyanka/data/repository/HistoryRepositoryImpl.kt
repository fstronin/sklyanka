package ru.stohastika.sklyanka.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import ru.stohastika.sklyanka.data.room.SklyankaConverters
import ru.stohastika.sklyanka.data.room.dao.HistoryDao
import ru.stohastika.sklyanka.data.room.dao.IntakeDao
import ru.stohastika.sklyanka.data.room.entity.IntakeEntity
import ru.stohastika.sklyanka.data.room.entity.MedicationEntity
import ru.stohastika.sklyanka.data.room.mapper.Decoded
import ru.stohastika.sklyanka.data.room.mapper.decode
import ru.stohastika.sklyanka.domain.history.HistorySlice
import ru.stohastika.sklyanka.domain.history.HistoryWindow
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.repository.HistoryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * История приёмов за период (SKL-18).
 *
 * Три потока сводятся в один срез намеренно: сводка, посчитанная из приёмов одного месяца
 * и лекарств, приехавших отдельно, однажды разойдётся — а расходится она в отчёте врачу.
 *
 * Границы периода превращаются в границы текстового диапазона здесь же, до обращения
 * к базе: арифметики над временем в SQL нет вовсе, потому что в сутках с переводом
 * времени их 23 или 25 (НФТ-37). Верхняя граница исключительная — начало следующих
 * суток, а не «конец дня»: приём в 23:59:30 иначе выпал бы из своего месяца.
 *
 * Строка с непонятым значением перечисления не роняет экран: она выпадает из среза,
 * а в журнал уходит ENUM_UNKNOWN с именем поля и идентификатором записи — без названия
 * лекарства (МГ-6, НФТ-19).
 */
@Singleton
class HistoryRepositoryImpl @Inject constructor(
    private val intakes: IntakeDao,
    private val history: HistoryDao,
    private val log: OperationLog,
) : HistoryRepository {

    override fun observe(profileId: String, window: HistoryWindow): Flow<HistorySlice> = combine(
        intakes.observePeriod(
            profileId = profileId,
            from = SklyankaConverters.dateTimeToText(window.from.atStartOfDay()),
            to = SklyankaConverters.dateTimeToText(window.to.plusDays(1).atStartOfDay()),
        ),
        history.observeMedicationsForHistory(profileId),
        history.observeFirstScheduledAt(profileId),
    ) { intakeRows, medicationRows, firstScheduledAt ->
        HistorySlice(
            profileId = profileId,
            intakes = intakeRows.mapNotNull { decodeOrLog(it) },
            medications = medicationRows.mapNotNull { decodeOrLog(it) },
            // Дата первой записи, а не дата начала первого курса: пустой курс без приёмов
            // истории не начинает, и его дни обязаны остаться нейтральными.
            historyStartsAt = firstScheduledAt?.let { SklyankaConverters.textToDateTime(it).toLocalDate() },
        )
    }

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
