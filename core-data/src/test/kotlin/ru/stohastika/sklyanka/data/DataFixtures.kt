package ru.stohastika.sklyanka.data

import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.TabletShape
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Заготовки для тестов слоя данных.
 *
 * Названий настоящих лекарств и имён людей здесь нет намеренно: тестовые данные
 * попадают в отчёты и в снимки экрана, а медицинским данным там не место (НФТ-19).
 */
object DataFixtures {

    const val OWNER_PROFILE_ID = "11111111-1111-4111-8111-111111111111"
    const val OTHER_PROFILE_ID = "22222222-2222-4222-8222-222222222222"
    const val MEDICATION_ID = "33333333-3333-4333-8333-333333333333"
    const val OTHER_MEDICATION_ID = "55555555-5555-4555-8555-555555555555"
    const val INTAKE_ID = "44444444-4444-4444-8444-444444444444"

    const val OWNER_COLOR = 0x005F6F2E
    const val OTHER_COLOR = 0x008F3E23

    val START_DATE: LocalDate = LocalDate.of(2026, 9, 1)
    val TODAY: LocalDate = LocalDate.of(2026, 9, 25)
    val MORNING: LocalTime = LocalTime.of(8, 0)
    val SCHEDULED_AT: LocalDateTime = LocalDateTime.of(2026, 9, 25, 8, 0)
    val ACTED_AT: Instant = Instant.parse("2026-09-25T05:05:00Z")

    fun ownerProfile() = Profile(
        id = OWNER_PROFILE_ID,
        name = "owner",
        color = OWNER_COLOR,
        isSelf = true,
        mirrorReminders = false,
    )

    fun otherProfile() = Profile(
        id = OTHER_PROFILE_ID,
        name = "relative",
        color = OTHER_COLOR,
        isSelf = false,
        mirrorReminders = true,
    )

    fun scheduledMedication(id: String = MEDICATION_ID, profileId: String = OWNER_PROFILE_ID) = Medication(
        id = id,
        profileId = profileId,
        name = "med-a",
        form = MedicationForm.TABLET,
        shape = TabletShape.ROUND,
        doseAmount = BigDecimal("1"),
        doseUnit = "unit",
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

    fun capsuleMedication() = scheduledMedication().copy(form = MedicationForm.CAPSULE)

    fun prnMedication(id: String = OTHER_MEDICATION_ID) = scheduledMedication(id = id).copy(
        scheduleMode = ScheduleMode.PRN,
        times = emptyList(),
        maxPerDay = MAX_PER_DAY,
    )

    fun pendingIntake(
        id: String = INTAKE_ID,
        medicationId: String = MEDICATION_ID,
        scheduledAt: LocalDateTime = SCHEDULED_AT,
    ) = Intake(
        id = id,
        medicationId = medicationId,
        scheduledAt = scheduledAt,
        status = IntakeStatus.PENDING,
        actedAt = null,
        source = null,
        actorProfileId = null,
    )

    fun takenIntake(id: String = INTAKE_ID, medicationId: String = MEDICATION_ID) = pendingIntake(
        id = id,
        medicationId = medicationId,
    ).copy(
        status = IntakeStatus.TAKEN,
        actedAt = ACTED_AT,
        source = IntakeSource.PHONE,
    )

    private const val MAX_PER_DAY = 3
}
