package ru.stohastika.sklyanka.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Заготовки корректных моделей для тестов.
 *
 * Тест инварианта обязан отличаться от соседнего ровно одним полем — иначе непонятно,
 * что именно он проверяет.
 */
internal object Fixtures {

    const val PROFILE_ID = "11111111-1111-4111-8111-111111111111"
    const val OTHER_PROFILE_ID = "22222222-2222-4222-8222-222222222222"
    const val MEDICATION_ID = "33333333-3333-4333-8333-333333333333"
    const val INTAKE_ID = "44444444-4444-4444-8444-444444444444"

    /** Максимум приёмов в сутки у курса «по потребности» — поле применимо только к PRN. */
    const val PRN_MAX_PER_DAY = 3

    val START_DATE: LocalDate = LocalDate.of(2026, 9, 1)

    /** Короткий курс для проверки границ: приём 25 сентября в него уже не попадает. */
    val SHORT_COURSE_END: LocalDate = LocalDate.of(2026, 9, 10)

    val MORNING: LocalTime = LocalTime.of(8, 0)
    val SCHEDULED_AT: LocalDateTime = LocalDateTime.of(2026, 9, 25, 8, 0)

    fun profile(name: String = "self", isSelf: Boolean = true) = Profile(
        id = PROFILE_ID,
        name = name,
        color = 0x00B4512F,
        isSelf = isSelf,
        mirrorReminders = false,
    )

    fun scheduledMedication() = Medication(
        id = MEDICATION_ID,
        profileId = PROFILE_ID,
        name = "magnesium",
        form = MedicationForm.TABLET,
        shape = TabletShape.ROUND,
        doseAmount = BigDecimal("1"),
        doseUnit = "tablet",
        strength = BigDecimal("500"),
        strengthUnit = "mg",
        foodRelation = FoodRelation.ANY,
        scheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay = null,
        times = listOf(MORNING),
        startDate = START_DATE,
        endDate = null,
        stockLeft = BigDecimal("30"),
        stockThresholdDays = null,
        archivedAt = null,
        archiveReason = null,
    )

    fun prnMedication() = scheduledMedication().copy(
        scheduleMode = ScheduleMode.PRN,
        times = emptyList(),
        maxPerDay = PRN_MAX_PER_DAY,
    )

    fun pendingIntake() = Intake(
        id = INTAKE_ID,
        medicationId = MEDICATION_ID,
        scheduledAt = SCHEDULED_AT,
        status = IntakeStatus.PENDING,
        actedAt = null,
        source = null,
        actorProfileId = null,
    )

    fun takenIntake(actedAt: Instant = Instant.parse("2026-09-25T05:05:00Z")) = pendingIntake().copy(
        status = IntakeStatus.TAKEN,
        actedAt = actedAt,
        source = IntakeSource.PHONE,
    )
}
