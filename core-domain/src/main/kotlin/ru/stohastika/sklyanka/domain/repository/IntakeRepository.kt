package ru.stohastika.sklyanka.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.stohastika.sklyanka.domain.intake.MarkCommand
import ru.stohastika.sklyanka.domain.intake.MarkOutcome
import ru.stohastika.sklyanka.domain.intake.PrnCommand
import ru.stohastika.sklyanka.domain.intake.PrnOutcome
import ru.stohastika.sklyanka.domain.intake.UndoCommand
import ru.stohastika.sklyanka.domain.intake.UndoOutcome
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.schedule.IntakePlanDiff
import ru.stohastika.sklyanka.domain.schedule.PlanApplyResult
import java.time.Instant
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

    /**
     * Отметка приёма целиком: чтение текущего состояния, решение правила перехода,
     * запись и приведение остатка — одна транзакция (ЧАС-24, НФТ-10).
     *
     * Разнести это на «прочитать», «решить» и «записать» нельзя: между чтением и записью
     * помещается отметка с часов, и остаток списывается дважды. [markIfPending] остаётся
     * тем же ядром идемпотентности и используется здесь, но сам по себе он не умеет
     * замещать более позднюю отметку более ранней (ПР-3).
     */
    suspend fun markAtomically(command: MarkCommand): MarkOutcome

    /**
     * Отмена отметки целиком: чтение текущего состояния, решение правила отмены,
     * запись и возврат дозы — одна транзакция (ПР-3, SKL-30).
     *
     * Действие либо отменяется целиком, либо не отменяется: отсутствие любого приёма —
     * [UndoOutcome.NotFound] без единой записи. `MISSED` не воскрешается, ожидающий приём
     * не трогается ([IntakeTransition.undo]): второй отмены не существует, а доза
     * не возвращается дважды.
     */
    suspend fun undoAtomically(command: UndoCommand): UndoOutcome

    /**
     * Создание приёма по потребности: проверка суточного лимита и вставка — одна
     * транзакция (ПР-4, ИНВ-17).
     *
     * Иначе два быстрых нажатия подряд дают четвёртый приём при лимите три: оба успевают
     * посчитать «уже три» до того, как первое вставит запись.
     */
    suspend fun createPrnAtomically(command: PrnCommand): PrnOutcome

    /**
     * Пакетный перевод ожидающихся приёмов в MISSED (ПР-1).
     *
     * Переводятся только те, что всё ещё ожидаются: повторный проход по тем же данным
     * не меняет ни одной записи и не переписывает момент перевода (НАП-34).
     *
     * @return сколько записей действительно изменилось.
     */
    suspend fun markMissed(ids: List<String>, at: Instant): Int
}
