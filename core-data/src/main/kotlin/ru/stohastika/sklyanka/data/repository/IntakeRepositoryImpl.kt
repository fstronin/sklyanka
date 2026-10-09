package ru.stohastika.sklyanka.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.stohastika.sklyanka.data.room.SklyankaConverters
import ru.stohastika.sklyanka.data.room.dao.IntakeDao
import ru.stohastika.sklyanka.data.room.dao.MedicationDao
import ru.stohastika.sklyanka.data.room.entity.IntakeEntity
import ru.stohastika.sklyanka.data.room.mapper.Decoded
import ru.stohastika.sklyanka.data.room.mapper.decode
import ru.stohastika.sklyanka.data.room.mapper.toEntity
import ru.stohastika.sklyanka.domain.Transactor
import ru.stohastika.sklyanka.domain.intake.IgnoreReason
import ru.stohastika.sklyanka.domain.intake.IntakeTransition
import ru.stohastika.sklyanka.domain.intake.MarkCommand
import ru.stohastika.sklyanka.domain.intake.MarkDecision
import ru.stohastika.sklyanka.domain.intake.MarkOutcome
import ru.stohastika.sklyanka.domain.intake.PrnCommand
import ru.stohastika.sklyanka.domain.intake.PrnDailyLimit
import ru.stohastika.sklyanka.domain.intake.PrnOutcome
import ru.stohastika.sklyanka.domain.intake.RevokeDecision
import ru.stohastika.sklyanka.domain.intake.StockEffect
import ru.stohastika.sklyanka.domain.intake.UndoCommand
import ru.stohastika.sklyanka.domain.intake.UndoDecision
import ru.stohastika.sklyanka.domain.intake.UndoOutcome
import ru.stohastika.sklyanka.domain.log.OpEvent
import ru.stohastika.sklyanka.domain.log.OperationLog
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Invariants
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.repository.IntakeRepository
import ru.stohastika.sklyanka.domain.schedule.IntakePlanDiff
import ru.stohastika.sklyanka.domain.schedule.PlanApplyResult
import ru.stohastika.sklyanka.domain.stock.StockLedger
import ru.stohastika.sklyanka.domain.time.TimeSource
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Приёмы: выборки в контексте профиля и идемпотентная отметка со списанием остатка.
 *
 * Сутки и периоды считаются по локальной полуночи (НФТ-37) и превращаются в границы
 * текстового диапазона до обращения к базе: арифметики над временем в SQL нет вовсе.
 */
@Singleton
@Suppress("TooManyFunctions")
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

    override suspend fun hasTaken(profileId: String, medicationId: String): Boolean =
        dao.hasTaken(profileId, medicationId)

    override fun observeHasTaken(profileId: String, medicationId: String): Flow<Boolean> =
        dao.observeHasTaken(profileId, medicationId)

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
            SklyankaConverters.textToEnum<ScheduleMode>(stored.scheduleMode)
                ?: error("Intake refers to a medication with an unreadable enum: $id")
        }
        // Граница записи произвольной модели: поля отметки согласованы со статусом
        // (ИНВ-10, ИНВ-11), «по потребности» не записывается ожидающимся (ИНВ-14).
        intakes.forEach { intake ->
            Invariants.requireValid(Invariants.writeViolations(intake, requireNotNull(modes[intake.medicationId])))
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

    override suspend fun scheduledBetween(
        profileId: String,
        medicationId: String,
        from: LocalDateTime,
        toExclusive: LocalDateTime,
    ): List<Intake> = dao.selectScheduledBetween(
        profileId = profileId,
        medicationId = medicationId,
        from = SklyankaConverters.dateTimeToText(from),
        to = SklyankaConverters.dateTimeToText(toExclusive),
    ).mapNotNull { decodeOrLog(it) }

    /**
     * Применение плана генерации одной транзакцией (SKL-06).
     *
     * Порядок шагов — сначала снятие лишних, потом вставка недостающих: перенос приёма
     * с 08:00 на 09:00 в обратном порядке упёрся бы в уникальный ключ, если бы новое время
     * совпало со снимаемым старым у соседней даты.
     *
     * Журнал ведёт домен, а не этот метод: причина запуска и границы окна известны там,
     * и одна операция генерации обязана оставлять одну запись, а не две.
     */
    override suspend fun applyPlan(profileId: String, medicationId: String, diff: IntakePlanDiff): PlanApplyResult =
        transactor.inTransaction {
            val medication = medications.getById(medicationId)
            requireNotNull(medication) { "Plan refers to a medication that does not exist: $medicationId" }
            require(medication.profileId == profileId) {
                "Plan refers to a medication of another profile: $medicationId"
            }
            val course = (medication.decode() as? Decoded.Value)?.model
                ?: error("Plan refers to a medication with an unreadable enum: $medicationId")

            val deleted = if (diff.toDelete.isEmpty()) 0 else dao.deletePending(diff.toDelete)
            val rows = diff.toCreate.map { moment -> pendingIntake(medicationId, moment) }
            // Граница генерации: строка, рождённая расписанием, обязана лежать в
            // курсе (ИНВ-15) и ссылаться на своё лекарство; отказ здесь — баг
            // экспандера, а не данные человека (SKL-107).
            rows.forEach { intake ->
                Invariants.requireValid(Invariants.violations(intake, course, time.zone()))
            }
            val created = if (rows.isEmpty()) {
                0
            } else {
                dao.insertAll(rows.map { it.toEntity(ScheduleMode.SCHEDULED) }).count { it != NOT_INSERTED }
            }
            PlanApplyResult(created = created, deleted = deleted)
        }

    /**
     * Модель нового ожидающегося приёма.
     *
     * Слот проставит маппер: сюда попадают только приёмы, порождённые расписанием, и
     * именно слот делает их уникальными на момент времени (ИНВ-13).
     */
    private fun pendingIntake(medicationId: String, moment: LocalDateTime) = Intake(
        id = UUID.randomUUID().toString(),
        medicationId = medicationId,
        scheduledAt = moment,
        status = IntakeStatus.PENDING,
        actedAt = null,
        source = null,
        actorProfileId = null,
    )

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
        if (status == IntakeStatus.TAKEN) applyStock(intake.medicationId, intake.id, StockEffect.DECREMENT)
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
     * Отметка приёма целиком одной транзакцией (ЧАС-24, НФТ-10).
     *
     * Последовательность неразрывна: прочитать текущее состояние, решить правилом домена,
     * записать под условием и только затем привести остаток. Разорви её — и две отметки,
     * пришедшие одновременно с телефона и с часов, спишут дозу дважды.
     *
     * Само правило здесь не живёт: [IntakeTransition] — чистая функция, проверяемая на JVM,
     * а этот метод отвечает только за неразрывность.
     */
    override suspend fun markAtomically(command: MarkCommand): MarkOutcome = transactor.inTransaction {
        val current = dao.getById(command.intakeId)?.let { decodeOrLog(it) }
            ?: return@inTransaction MarkOutcome.NotFound

        when (val decision = IntakeTransition.decide(current, command)) {
            is MarkDecision.Keep -> {
                log.record(
                    event = OpEvent.INTAKE_MARK_IGNORED,
                    medicationId = current.medicationId,
                    intakeId = command.intakeId,
                    before = current.status.name,
                    after = command.result.status.name,
                    source = command.source.name,
                    detail = decision.reason.code,
                )
                MarkOutcome.Ignored(current, decision.reason)
            }

            is MarkDecision.Write -> writeMark(current, command, decision)
        }
    }

    /**
     * Отмена отметки и снятие записи целиком одной транзакцией (SKL-30, SKL-127).
     *
     * Последовательность неразрывна: прочитать действие целиком, убедиться, что оно
     * одного профиля, применить правило своего режима и только затем вернуть дозу.
     * Отсутствие любого приёма отменяет всё действие без единой записи — та же «вся
     * группа или никак», что у групповой отметки.
     *
     * Режим выбирает исход, а не запрещает его: у планового приёма отметка возвращается
     * в «ожидается» (`IntakeTransition.undo`), а запись «по потребности» **снимается**
     * мягким признаком (Р-46): ожидающихся приёмов у режима не существует (ПР-4),
     * а физическое удаление запрещено (`ADR-0012` п. 5). Смешение режимов в одном
     * действии — ошибка вызова: человек отменяет одно действие одного лекарства.
     */
    override suspend fun undoAtomically(command: UndoCommand): UndoOutcome = transactor.inTransaction {
        val current = command.intakeIds.map { intakeId ->
            dao.getById(intakeId)?.let { decodeOrLog(it) }
                ?: return@inTransaction UndoOutcome.NotFound
        }
        val medicationsById = current.map { it.medicationId }.distinct().associateWith { medicationId ->
            requireNotNull(medications.getById(medicationId)) {
                "Undo refers to a medication that does not exist: $medicationId"
            }
        }
        val profiles = medicationsById.values.map { it.profileId }.distinct()
        require(profiles.size == 1) { "Undo command spans several profiles: $profiles" }
        val modes = medicationsById.values.map { stored ->
            SklyankaConverters.textToEnum<ScheduleMode>(stored.scheduleMode) ?: ScheduleMode.SCHEDULED
        }.distinct()
        require(modes.size == 1) { "Undo command spans several schedule modes: $modes" }

        when (modes.single()) {
            ScheduleMode.SCHEDULED -> undoMarks(current, command)
            ScheduleMode.PRN -> revokeRecords(current, command)
        }
    }

    /** Плановая отмена: приём возвращается в «ожидается», доза возвращается (SKL-30). */
    private suspend fun undoMarks(current: List<Intake>, command: UndoCommand): UndoOutcome {
        var undone = 0
        current.forEach { intake ->
            when (val decision = IntakeTransition.undo(intake)) {
                is UndoDecision.Keep -> Unit
                is UndoDecision.Revert -> {
                    // Отмена обязана очистить поля отметки: возврат в «ожидается» без
                    // очистки — отказ записи, а не испорченная история (ИНВ-11, SKL-107).
                    Invariants.requireValid(Invariants.violations(decision.intake))
                    val changed = dao.unmark(intake.id)
                    if (changed == 0) {
                        log.record(
                            event = OpEvent.DB_WRITE_FAILED,
                            medicationId = intake.medicationId,
                            intakeId = intake.id,
                            before = intake.status.name,
                            source = command.source.name,
                            detail = STALE_READ,
                        )
                    } else {
                        applyStock(intake.medicationId, intake.id, decision.stock)
                        log.record(
                            event = OpEvent.INTAKE_MARK_UNDONE,
                            // Профиль того, кто отменил; пусто означает, что отменил
                            // владелец профиля приёма (ПР-12). Имён профилей нет — только ids.
                            profileId = command.actorProfileId,
                            medicationId = intake.medicationId,
                            intakeId = intake.id,
                            before = intake.status.name,
                            after = IntakeStatus.PENDING.name,
                            source = command.source.name,
                        )
                        undone++
                    }
                }
            }
        }
        return UndoOutcome.Applied(undone)
    }

    /**
     * Снятие записи «по потребности» (Р-46): признак «снято», доза возвращается.
     *
     * Ноль изменённых строк — не ошибка человека, а проигранная гонка (запись успели
     * снять или перевести в «пропущено»): возвращать дозу в этом случае нельзя, иначе
     * остаток разойдётся с упаковкой, и след об этом остаётся в журнале.
     */
    private suspend fun revokeRecords(current: List<Intake>, command: UndoCommand): UndoOutcome {
        val at = time.now()
        var revoked = 0
        current.forEach { intake ->
            when (val decision = IntakeTransition.revoke(intake, at)) {
                is RevokeDecision.Keep -> Unit
                is RevokeDecision.Revoked -> {
                    // Снятие не переписывает решение человека: статус остаётся «принято»,
                    // поля отметки не чистятся (ИНВ-10/11 не затрагиваются).
                    Invariants.requireValid(Invariants.violations(decision.intake))
                    val changed = dao.revoke(intake.id, at.toEpochMilli())
                    if (changed == 0) {
                        log.record(
                            event = OpEvent.DB_WRITE_FAILED,
                            medicationId = intake.medicationId,
                            intakeId = intake.id,
                            before = intake.status.name,
                            source = command.source.name,
                            detail = STALE_READ,
                        )
                    } else {
                        applyStock(intake.medicationId, intake.id, decision.stock)
                        log.record(
                            event = OpEvent.INTAKE_REVOKED,
                            profileId = command.actorProfileId,
                            medicationId = intake.medicationId,
                            intakeId = intake.id,
                            before = intake.status.name,
                            after = intake.status.name,
                            source = command.source.name,
                        )
                        revoked++
                    }
                }
            }
        }
        return UndoOutcome.Applied(revoked)
    }

    /**
     * Создание приёма по потребности: проверка суточного лимита и вставка в одной
     * транзакции (ПР-4, ИНВ-17).
     *
     * Лимит читается здесь, а не приходит снаружи: между чтением карточки лекарства и
     * нажатием «Принять» лимит могли изменить, и решать обязано то значение, которое
     * лежит в базе в момент вставки.
     */
    override suspend fun createPrnAtomically(command: PrnCommand): PrnOutcome = transactor.inTransaction {
        val medication = medications.getById(command.medicationId)
            ?: return@inTransaction PrnOutcome.UnknownMedication
        // Удалённое лекарство новых приёмов не порождает (ПР-14): кнопка «Принять» на экране
        // недостижима, а проверка здесь — защита будущего пути записи.
        if (medication.deletedAt != null) return@inTransaction PrnOutcome.Deleted
        val mode = SklyankaConverters.textToEnum<ScheduleMode>(medication.scheduleMode)
        if (mode != ScheduleMode.PRN) return@inTransaction PrnOutcome.NotOnDemand

        val day = command.scheduledAt.toLocalDate()
        val countToday = dao.countOnDay(
            profileId = medication.profileId,
            medicationId = medication.id,
            dayStart = startOfDay(day),
            nextDayStart = startOfDay(day.plusDays(1)),
        )
        val limit = medication.maxPerDay
        if (PrnDailyLimit.reached(limit, countToday)) {
            log.record(
                event = OpEvent.INTAKE_PRN_LIMIT_REACHED,
                profileId = medication.profileId,
                medicationId = medication.id,
                before = countToday.toString(),
                after = limit.toString(),
            )
            return@inTransaction PrnOutcome.RejectedDailyLimit(limit = requireNotNull(limit), countToday = countToday)
        }

        val intake = Intake(
            id = UUID.randomUUID().toString(),
            medicationId = medication.id,
            scheduledAt = command.scheduledAt,
            status = IntakeStatus.TAKEN,
            actedAt = command.actedAt,
            source = command.source,
            actorProfileId = command.actorProfileId,
        )
        val course = requireNotNull((medication.decode() as? Decoded.Value)?.model) {
            "On-demand intake addresses a medication with an unreadable enum: ${medication.id}"
        }
        // Рождение приёма «по потребности»: назначенное время равно моменту отметки (ИНВ-14);
        // курс не судится — кнопка жива, пока курс не убран в архив (SKL-107).
        Invariants.requireValid(Invariants.prnBirthViolations(intake, course, time.zone()))

        // Слот не проставляется: приём по потребности расписанию не подчиняется, и два
        // таких приёма в одну минуту законны (ИНВ-14).
        dao.insertAll(listOf(intake.toEntity(ScheduleMode.PRN)))
        applyStock(medication.id, intake.id, StockEffect.DECREMENT)
        log.record(
            event = OpEvent.INTAKE_PRN_CREATED,
            profileId = command.actorProfileId ?: medication.profileId,
            medicationId = medication.id,
            intakeId = intake.id,
            after = (countToday + 1).toString(),
            source = command.source.name,
        )
        PrnOutcome.Created(intake)
    }

    /**
     * Пакетный перевод просроченных приёмов в MISSED (ПР-1).
     *
     * Пачка режется на части: число параметров запроса в SQLite ограничено, а человек,
     * не открывавший приложение полгода, приносит проходу тысячи записей. Части идут
     * внутри одной транзакции — половина переведённых приёмов хуже, чем ни одного.
     */
    override suspend fun markMissed(ids: List<String>, at: Instant): Int = transactor.inTransaction {
        if (ids.isEmpty()) return@inTransaction 0
        val moment = at.toEpochMilli()
        val changed = ids.chunked(MISSED_BATCH).sumOf { chunk -> dao.markMissedIfPending(chunk, moment) }
        changed
    }

    /**
     * Запись отметки под условием, вычисленным доменом.
     *
     * Условие стоит в самом UPDATE: первая отметка проверяет «приём ещё ожидается»,
     * замещающая — «записанная отметка позже пришедшей». Ноль изменённых строк означает,
     * что между чтением и записью состояние изменилось; внутри транзакции этого быть
     * не может, поэтому случай не игнорируется молча, а попадает в журнал (`TDD.md §9`).
     */
    private suspend fun writeMark(current: Intake, command: MarkCommand, decision: MarkDecision.Write): MarkOutcome {
        // Граница отметки: поля отметки согласованы со статусом (ИНВ-10, ИНВ-11), отметивший
        // — не профиль владельца лекарства (ИНВ-18). Курс и режим не судят уже живущую
        // строку: план правится после рождения приёма (обоснование — SKL-107/TDD.md §2).
        Invariants.requireValid(Invariants.markingViolations(decision.intake, medicationModel(current.medicationId)))

        val actedAt = command.actedAt.toEpochMilli()
        val status = command.result.status.name
        val changed = if (decision.superseded) {
            dao.supersedeIfLater(command.intakeId, status, actedAt, command.source.name, command.actorProfileId)
        } else {
            dao.markIfPending(command.intakeId, status, actedAt, command.source.name, command.actorProfileId)
        }
        if (changed == 0) {
            log.record(
                event = OpEvent.DB_WRITE_FAILED,
                medicationId = current.medicationId,
                intakeId = command.intakeId,
                before = current.status.name,
                source = command.source.name,
                detail = STALE_READ,
            )
            return MarkOutcome.Ignored(current, IgnoreReason.ALREADY_MARKED)
        }

        applyStock(current.medicationId, command.intakeId, decision.stock)
        log.record(
            event = if (decision.superseded) OpEvent.INTAKE_MARK_SUPERSEDED else OpEvent.INTAKE_MARKED,
            // Профиль того, кто отметил; пусто означает, что отметил владелец профиля
            // лекарства (ПР-12). Имён профилей в журнале нет — только идентификаторы.
            profileId = command.actorProfileId,
            medicationId = current.medicationId,
            intakeId = command.intakeId,
            before = current.status.name,
            after = status,
            source = command.source.name,
        )
        return if (decision.superseded) {
            MarkOutcome.Superseded(decision.intake, current)
        } else {
            MarkOutcome.Applied(decision.intake)
        }
    }

    /** Лекарство приёма для парных проверок; нечитаемое перечисление в продукте недостижимо. */
    private suspend fun medicationModel(medicationId: String): Medication {
        val stored = requireNotNull(medications.getById(medicationId)) {
            "An intake addresses a medication that does not exist: $medicationId"
        }
        return (stored.decode() as? Decoded.Value)?.model
            ?: error("An intake addresses a medication with an unreadable enum: $medicationId")
    }

    /**
     * Приведение остатка к результату отметки.
     *
     * Арифметика — в домене: остаток хранится текстом, потому что двоичная дробь не
     * представляет 0,1 точно, а упаковка уменьшается шагами по половине таблетки сотни
     * раз подряд. Ниже нуля остаток не уходит (INV-8). Лекарство без учёта остатка
     * правилу не подчиняется (ПР-5).
     */
    private suspend fun applyStock(medicationId: String, intakeId: String, effect: StockEffect) {
        val medication = medications.getById(medicationId) ?: return
        val stock = SklyankaConverters.textToDecimal(medication.stockLeft)
        val dose = requireNotNull(SklyankaConverters.textToDecimal(medication.doseAmount))
        // Арифметика — доменного правила, а не этого метода (SKL-12): второй экземпляр
        // правила списания рано или поздно разойдётся с первым, а остаток истории
        // не хранит и разойтись ему нельзя.
        val change = StockLedger.apply(stock, dose, effect) ?: return
        medications.setStock(medication.id, change.after.toPlainString())
        log.record(
            event = if (effect == StockEffect.RESTORE) OpEvent.STOCK_RESTORED else OpEvent.STOCK_DECREMENTED,
            profileId = medication.profileId,
            medicationId = medication.id,
            intakeId = intakeId,
            before = change.before.toPlainString(),
            after = change.after.toPlainString(),
            // Остаток упёрся в ноль: доза оказалась больше остатка (ИНВ-8). Частота
            // таких записей — прямой признак того, что счётчик разошёлся с упаковкой.
            detail = CLAMPED_AT_ZERO.takeIf { change.clamped },
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

        /** Списание упёрлось в ноль: служебный код журнала, а не текст для человека. */
        const val CLAMPED_AT_ZERO = "clamped_at_zero"
        const val ALREADY_MARKED = "already_marked"

        /** Доменная проверка и база разошлись: внутри транзакции такого быть не должно. */
        const val STALE_READ = "stale_read"

        /** Предел числа параметров запроса в SQLite — 999; пачка режется с запасом. */
        const val MISSED_BATCH = 500
    }
}
