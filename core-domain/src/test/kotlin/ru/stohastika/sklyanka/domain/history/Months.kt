package ru.stohastika.sklyanka.domain.history

import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.time.FixedTimeSource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Заготовки истории приёмов.
 *
 * Даты и время те же, что в `acceptance/src/main/resources/features/SKL-18.feature`: сентябрь 2026,
 * «сейчас» — 30 сентября, 10:00, приёмы в 08:00 и 20:00. Настоящих названий лекарств
 * здесь нет: тестовые данные попадают в отчёты (НФТ-19).
 */
internal object Months {

    const val PROFILE_ID = "11111111-1111-4111-8111-111111111111"
    const val OTHER_PROFILE_ID = "22222222-2222-4222-8222-222222222222"
    const val MEDICATION_ID = "33333333-3333-4333-8333-333333333333"
    const val SECOND_MEDICATION_ID = "44444444-4444-4444-8444-444444444444"
    const val ARCHIVED_MEDICATION_ID = "55555555-5555-4555-8555-555555555555"

    val ZONE: ZoneId = ZoneId.of("Europe/Moscow")
    val SEPTEMBER: YearMonth = YearMonth.of(2026, 9)
    val OCTOBER: YearMonth = YearMonth.of(2026, 10)
    val TODAY: LocalDate = LocalDate.of(2026, 9, 30)
    val MORNING: LocalTime = LocalTime.of(8, 0)
    val EVENING: LocalTime = LocalTime.of(20, 0)
    val WINDOW: HistoryWindow = HistoryWindow.of(SEPTEMBER)

    fun clock(at: LocalDateTime = TODAY.atTime(10, 0)): FixedTimeSource =
        FixedTimeSource(at.atZone(ZONE).toInstant(), ZONE)

    fun instant(at: LocalDateTime): Instant = at.atZone(ZONE).toInstant()

    @Suppress("LongParameterList")
    fun medication(
        id: String = MEDICATION_ID,
        profileId: String = PROFILE_ID,
        name: String = "med-a",
        times: List<LocalTime> = listOf(MORNING),
        startDate: LocalDate = LocalDate.of(2026, 9, 1),
        endDate: LocalDate? = null,
        archivedAt: Instant? = null,
    ) = Medication(
        id = id,
        profileId = profileId,
        name = name,
        form = MedicationForm.TABLET,
        shape = null,
        doseAmount = BigDecimal.ONE,
        doseUnit = "tablet",
        strength = BigDecimal("50"),
        strengthUnit = "mg",
        foodRelation = FoodRelation.ANY,
        scheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay = null,
        times = times,
        startDate = startDate,
        endDate = endDate,
        stockLeft = null,
        stockThresholdDays = null,
        archivedAt = archivedAt,
        archiveReason = archivedAt?.let { ArchiveReason.COURSE_FINISHED },
    )

    fun intake(
        at: LocalDateTime,
        status: IntakeStatus,
        medicationId: String = MEDICATION_ID,
        actedAt: Instant? = if (status == IntakeStatus.PENDING) null else instant(at),
        actorProfileId: String? = null,
    ) = Intake(
        id = "intake-$medicationId-$at-$status",
        medicationId = medicationId,
        scheduledAt = at,
        status = status,
        actedAt = actedAt,
        source = when (status) {
            IntakeStatus.PENDING -> null
            IntakeStatus.MISSED -> IntakeSource.AUTO
            else -> IntakeSource.PHONE
        },
        actorProfileId = actorProfileId,
    )

    /** Один приём в сутки на весь отрезок дат включительно. */
    fun daily(
        from: LocalDate,
        to: LocalDate,
        time: LocalTime = MORNING,
        status: IntakeStatus = IntakeStatus.TAKEN,
        medicationId: String = MEDICATION_ID,
    ): List<Intake> = generateSequence(from) { it.plusDays(1) }
        .takeWhile { !it.isAfter(to) }
        .map { date -> intake(date.atTime(time), status, medicationId) }
        .toList()

    fun slice(
        intakes: List<Intake>,
        medications: List<Medication> = listOf(medication()),
        historyStartsAt: LocalDate? = intakes.minOfOrNull { it.scheduledAt.toLocalDate() },
        profileId: String = PROFILE_ID,
    ) = HistorySlice(
        profileId = profileId,
        intakes = intakes,
        medications = medications,
        historyStartsAt = historyStartsAt,
    )
}
