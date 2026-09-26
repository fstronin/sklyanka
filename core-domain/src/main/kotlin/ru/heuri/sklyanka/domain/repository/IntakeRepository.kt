package ru.heuri.sklyanka.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Приёмы.
 *
 * Запроса, возвращающего приёмы нескольких профилей сразу, в приложении не существует —
 * не «не используется», а отсутствует в интерфейсе (ПР-10).
 */
interface IntakeRepository {

    fun observeDay(profileId: String, date: LocalDate): Flow<List<Intake>>

    fun observePeriod(profileId: String, from: LocalDate, to: LocalDate): Flow<List<Intake>>

    fun observeByMedication(
        profileId: String,
        medicationId: String,
        from: LocalDate,
        to: LocalDate,
    ): Flow<List<Intake>>

    suspend fun get(intakeId: String): Intake?

    /**
     * Вставляет приёмы, пропуская уже существующие.
     *
     * Повторная генерация того же приёма — ожидаемый ход событий, а не ошибка:
     * уникальность держит база (INV-13), а вызывающий получает число реально
     * вставленных строк.
     */
    suspend fun insertMissing(intakes: List<Intake>): Int

    /**
     * Ядро идемпотентности (ПР-2, INV-19): отметка применяется, только если приём ещё ожидается.
     *
     * Проверять «а отмечен ли уже?» отдельным чтением нельзя — между чтением и записью
     * помещается отметка с часов.
     *
     * @return true, если отметка применена; false, если приём уже был отмечен.
     */
    suspend fun markIfPending(
        intakeId: String,
        status: IntakeStatus,
        source: IntakeSource,
        actorProfileId: String? = null,
    ): Boolean

    /** Просроченные ожидающие приёмы для перевода в MISSED (ПР-1); граница окна приходит снаружи (ОВ-4). */
    suspend fun expiredPending(before: LocalDateTime): List<Intake>
}
