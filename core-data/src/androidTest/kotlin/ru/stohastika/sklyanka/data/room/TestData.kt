package ru.stohastika.sklyanka.data.room

import ru.stohastika.sklyanka.data.room.entity.IntakeEntity
import ru.stohastika.sklyanka.data.room.entity.MedicationEntity
import ru.stohastika.sklyanka.data.room.entity.ProfileEntity
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Заготовки для инструментальных тестов.
 *
 * Названий настоящих лекарств и имён людей здесь нет намеренно: тестовые данные
 * попадают в отчёты и в снимки экрана, а медицинским данным там не место (НФТ-19).
 */
object TestData {

    const val OWNER_ID = "11111111-1111-4111-8111-111111111111"
    const val RELATIVE_ID = "22222222-2222-4222-8222-222222222222"
    const val OWNER_MEDICATION_ID = "33333333-3333-4333-8333-333333333333"
    const val RELATIVE_MEDICATION_ID = "44444444-4444-4444-8444-444444444444"
    const val PRN_MEDICATION_ID = "55555555-5555-4555-8555-555555555555"

    const val OWNER_COLOR = 0x00B4512F
    const val RELATIVE_COLOR = 0x008F3E23

    val TODAY: LocalDate = LocalDate.of(2026, 9, 25)
    val COURSE_START: LocalDate = LocalDate.of(2026, 9, 1)
    val MORNING: LocalDateTime = LocalDateTime.of(2026, 9, 25, 8, 0)
    val LATE_EVENING: LocalDateTime = LocalDateTime.of(2026, 9, 25, 23, 30)

    fun owner() = ProfileEntity(
        id = OWNER_ID,
        name = "owner",
        color = OWNER_COLOR,
        isSelf = true,
        mirrorReminders = false,
        selfSlot = ProfileEntity.SELF_SLOT,
    )

    fun relative() = ProfileEntity(
        id = RELATIVE_ID,
        name = "relative",
        color = RELATIVE_COLOR,
        isSelf = false,
        mirrorReminders = true,
        selfSlot = null,
    )

    fun medication(
        id: String = OWNER_MEDICATION_ID,
        profileId: String = OWNER_ID,
        name: String = "med-a",
        scheduleMode: ScheduleMode = ScheduleMode.SCHEDULED,
        stockLeft: String? = "30",
        packageSize: String? = null,
        endDate: String? = null,
        archivedAt: Long? = null,
        archiveReason: ArchiveReason? = null,
    ) = MedicationEntity(
        id = id,
        profileId = profileId,
        name = name,
        form = MedicationForm.TABLET.name,
        shape = null,
        doseAmount = "1",
        doseUnit = "unit",
        strength = null,
        strengthUnit = null,
        foodRelation = FoodRelation.ANY.name,
        scheduleMode = scheduleMode.name,
        maxPerDay = null,
        times = if (scheduleMode == ScheduleMode.SCHEDULED) "08:00" else "",
        startDate = SklyankaConverters.dateToText(COURSE_START),
        endDate = endDate,
        stockLeft = stockLeft,
        packageSize = packageSize,
        stockThresholdDays = null,
        archivedAt = archivedAt,
        archiveReason = archiveReason?.name,
    )

    fun scheduledIntake(
        id: String,
        medicationId: String = OWNER_MEDICATION_ID,
        scheduledAt: LocalDateTime = MORNING,
        status: IntakeStatus = IntakeStatus.PENDING,
        actedAt: Long? = null,
        source: String? = null,
        revokedAt: Long? = null,
    ) = IntakeEntity(
        id = id,
        medicationId = medicationId,
        scheduledAt = SklyankaConverters.dateTimeToText(scheduledAt),
        scheduleSlot = IntakeEntity.SCHEDULE_SLOT,
        status = status.name,
        actedAt = actedAt,
        source = source,
        actorProfileId = null,
        revokedAt = revokedAt,
    )

    /**
     * Приём по потребности: слота не занимает, поэтому два таких приёма законны
     * в одну и ту же минуту (INV-14).
     */
    fun prnIntake(
        id: String,
        medicationId: String = PRN_MEDICATION_ID,
        scheduledAt: LocalDateTime = MORNING,
        actedAt: Long = 0L,
        revokedAt: Long? = null,
    ) = IntakeEntity(
        id = id,
        medicationId = medicationId,
        scheduledAt = SklyankaConverters.dateTimeToText(scheduledAt),
        scheduleSlot = null,
        status = IntakeStatus.TAKEN.name,
        actedAt = actedAt,
        source = "PHONE",
        actorProfileId = null,
        revokedAt = revokedAt,
    )
}
