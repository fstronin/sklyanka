package ru.heuri.sklyanka.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.schedule.IntakePlanDiff
import ru.heuri.sklyanka.domain.schedule.PlanApplyResult
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
     * Приёмы по расписанию одного лекарства внутри окна генерации (SKL-06).
     *
     * Профиль — обязательный параметр, хотя лекарство задаёт его однозначно: выборка без
     * профиля выглядит одинаково правильно и в верном, и в ошибочном вызове (ПР-10).
     * Приёмы по потребности в выборку не попадают: слота они не занимают, плану не
     * подчиняются и удалению при перепланировании не подлежат (ИНВ-14).
     *
     * Верхняя граница исключительная: сутки выбираются диапазоном, а не арифметикой
     * «минус 24 часа» — в сутках с переводом времени их 23 или 25 (НФТ-37).
     */
    suspend fun scheduledBetween(
        profileId: String,
        medicationId: String,
        from: LocalDateTime,
        toExclusive: LocalDateTime,
    ): List<Intake>

    /**
     * Применение плана одной транзакцией: удалить лишнее и создать недостающее.
     *
     * Именно одной: сбой между шагами оставил бы человека без части приёмов дня, а это
     * пропущенное напоминание, то есть отказ продукта. Возвращается то, что действительно
     * применилось, а не то, что просил домен: вставка пропускает конфликт по уникальному
     * ключу (ИНВ-13), а удаление не трогает уже отмеченные приёмы.
     */
    suspend fun applyPlan(profileId: String, medicationId: String, diff: IntakePlanDiff): PlanApplyResult

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
