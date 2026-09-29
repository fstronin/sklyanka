package ru.stohastika.sklyanka.domain.medication

import ru.stohastika.sklyanka.domain.Transactor
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.domain.schedule.FakeMedicationRepository
import ru.stohastika.sklyanka.domain.schedule.GenerationWindow
import ru.stohastika.sklyanka.domain.schedule.MedicationPlan
import ru.stohastika.sklyanka.domain.schedule.MedicationPlanner
import ru.stohastika.sklyanka.domain.schedule.PlanReason
import ru.stohastika.sklyanka.domain.schedule.PlanResult
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Заготовки карточки лекарства и архива.
 *
 * Даты и время те же, что в `docs/tasks/SKL-10/acceptance.feature`: 25 сентября 2026,
 * 10:00, курс 12 сентября — 12 октября. Настоящих названий лекарств здесь нет —
 * тестовые данные попадают в отчёты (НФТ-19).
 */
internal object Cards {

    const val PROFILE_ID = "11111111-1111-4111-8111-111111111111"
    const val MEDICATION_ID = "33333333-3333-4333-8333-333333333333"
    const val OTHER_MEDICATION_ID = "66666666-6666-4666-8666-666666666666"

    val ZONE: ZoneId = ZoneId.of("Europe/Moscow")
    val TODAY: LocalDate = LocalDate.of(2026, 9, 25)
    val COURSE_START: LocalDate = LocalDate.of(2026, 9, 12)
    val COURSE_END: LocalDate = LocalDate.of(2026, 10, 12)
    val MORNING: LocalTime = LocalTime.of(8, 0)
    val EVENING: LocalTime = LocalTime.of(20, 0)

    fun clock(at: LocalDateTime = TODAY.atTime(10, 0)): FixedTimeSource =
        FixedTimeSource(at.atZone(ZONE).toInstant(), ZONE)

    fun instant(at: LocalDateTime): Instant = at.atZone(ZONE).toInstant()

    @Suppress("LongParameterList")
    fun scheduled(
        id: String = MEDICATION_ID,
        name: String = "med-a",
        form: MedicationForm = MedicationForm.TABLET,
        shape: TabletShape? = TabletShape.ROUND_SCORED,
        foodRelation: FoodRelation = FoodRelation.BEFORE_MEAL,
        times: List<LocalTime> = listOf(MORNING, EVENING),
        startDate: LocalDate = COURSE_START,
        endDate: LocalDate? = COURSE_END,
        stockLeft: String? = "9",
        thresholdDays: Int? = 5,
        archivedAt: Instant? = null,
        archiveReason: ArchiveReason? = null,
        mirrorToWatch: Boolean = true,
    ) = Medication(
        id = id,
        profileId = PROFILE_ID,
        name = name,
        form = form,
        shape = shape,
        doseAmount = BigDecimal("2"),
        doseUnit = "tablet",
        strength = BigDecimal("48"),
        strengthUnit = "mg",
        foodRelation = foodRelation,
        scheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay = null,
        times = times,
        startDate = startDate,
        endDate = endDate,
        stockLeft = stockLeft?.let { BigDecimal(it) },
        stockThresholdDays = thresholdDays,
        archivedAt = archivedAt,
        archiveReason = archiveReason,
        mirrorToWatch = mirrorToWatch,
    )

    fun asNeeded(id: String = MEDICATION_ID, maxPerDay: Int = 3) = scheduled(id = id).copy(
        scheduleMode = ScheduleMode.PRN,
        times = emptyList(),
        maxPerDay = maxPerDay,
        endDate = null,
    )

    fun intake(medicationId: String = MEDICATION_ID, at: LocalDateTime, status: IntakeStatus = IntakeStatus.TAKEN) =
        Intake(
            id = "intake-$medicationId-$at",
            medicationId = medicationId,
            scheduledAt = at,
            status = status,
            actedAt = if (status == IntakeStatus.PENDING) null else instant(at),
            source = if (status == IntakeStatus.PENDING) null else IntakeSource.PHONE,
            actorProfileId = null,
        )

    /** Оба приёма дня: восемь утра и восемь вечера. */
    fun day(date: LocalDate, taken: Int, planned: Int = 2, medicationId: String = MEDICATION_ID): List<Intake> =
        listOf(MORNING, EVENING).take(planned).mapIndexed { index, time ->
            intake(
                medicationId = medicationId,
                at = date.atTime(time),
                status = if (index < taken) IntakeStatus.TAKEN else IntakeStatus.MISSED,
            )
        }
}

/**
 * Запись вызовов перепланирования.
 *
 * Архивация и возврат обязаны перепланировать ровно один раз и ровно после записи полей:
 * порядок проверяется тем, что фальшивка видит в хранилище в момент вызова.
 */
internal class RecordingPlanner(
    private val medications: FakeMedicationRepository? = null,
    private val created: Int = 0,
    private val deleted: Int = 0,
) : MedicationPlanner {

    val calls: MutableList<Pair<String, PlanReason>> = mutableListOf()

    /** Был ли курс уже архивирован, когда позвали планировщик. */
    var archivedAtCall: Boolean? = null
        private set

    override suspend fun plan(medicationId: String, reason: PlanReason): PlanResult {
        calls += medicationId to reason
        archivedAtCall = medications?.stored?.get(medicationId)?.isArchived
        return PlanResult(
            window = GenerationWindow.of(Cards.TODAY, WINDOW_DAYS),
            reason = reason,
            medications = listOf(
                MedicationPlan(
                    profileId = Cards.PROFILE_ID,
                    medicationId = medicationId,
                    created = created,
                    deleted = deleted,
                    unchanged = 0,
                ),
            ),
        )
    }

    private companion object {
        const val WINDOW_DAYS = GenerationWindow.MIN_DEPTH_DAYS
    }
}

/** Транзакция без базы: блок выполняется как есть. */
internal class DirectTransactor : Transactor {

    var opened: Int = 0
        private set

    override suspend fun <T> inTransaction(block: suspend () -> T): T {
        opened++
        return block()
    }
}
