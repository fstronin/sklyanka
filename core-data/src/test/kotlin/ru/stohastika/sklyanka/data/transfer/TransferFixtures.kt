package ru.stohastika.sklyanka.data.transfer

import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.Profile
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.domain.transfer.AppStamp
import ru.stohastika.sklyanka.domain.transfer.QuietHours
import ru.stohastika.sklyanka.domain.transfer.ReminderSound
import ru.stohastika.sklyanka.domain.transfer.TransferSettings
import ru.stohastika.sklyanka.domain.transfer.TransferSnapshot
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Снимок со всеми заполненными полями — для кругового цикла «модель → JSON → модель».
 *
 * Заполнены именно все: круговой цикл на снимке с половиной пустых полей проверяет
 * половину формата и молчит о второй.
 *
 * Названия латиницей: кириллица в строковом литерале Kotlin запрещена (НФТ-42).
 */
internal object TransferFixtures {

    val EXPORTED_AT: Instant = Instant.parse("2026-10-05T18:14:03Z")
    val ARCHIVED_AT: Instant = Instant.parse("2026-08-30T07:02:00Z")
    val ACTED_AT: Instant = Instant.parse("2026-10-05T05:05:12Z")
    val SCHEDULED_AT: LocalDateTime = LocalDateTime.of(2026, 10, 5, 8, 0)

    const val SELF_ID = "p1"
    const val MOTHER_ID = "p2"
    const val ACTIVE_ID = "m1"
    const val ARCHIVED_ID = "m2"

    private const val SELF_COLOR = -0x4BAED1
    private const val OTHER_COLOR = -0x70C1DD
    private const val PRN_LIMIT = 3
    private const val THRESHOLD_DAYS = 5
    private const val SNOOZE_MINUTES = 20

    fun self() = Profile(
        id = SELF_ID,
        name = "Owner",
        color = SELF_COLOR,
        isSelf = true,
        mirrorReminders = false,
    )

    fun mother() = Profile(
        id = MOTHER_ID,
        name = "Mother",
        color = OTHER_COLOR,
        isSelf = false,
        mirrorReminders = true,
    )

    fun active() = Medication(
        id = ACTIVE_ID,
        profileId = SELF_ID,
        name = "Magnesium B6",
        form = MedicationForm.TABLET,
        shape = TabletShape.ROUND_SCORED,
        doseAmount = BigDecimal("1.5"),
        doseUnit = "tablet",
        strength = BigDecimal("48"),
        strengthUnit = "mg",
        foodRelation = FoodRelation.BEFORE_MEAL,
        scheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay = null,
        times = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
        startDate = LocalDate.of(2026, 2, 12),
        endDate = LocalDate.of(2026, 10, 12),
        stockLeft = BigDecimal("9.5"),
        packageSize = BigDecimal("60"),
        stockThresholdDays = THRESHOLD_DAYS,
        archivedAt = null,
        archiveReason = null,
    )

    fun archived() = Medication(
        id = ARCHIVED_ID,
        profileId = MOTHER_ID,
        name = "Nurofen",
        form = MedicationForm.SYRUP,
        shape = null,
        doseAmount = BigDecimal("5"),
        doseUnit = "millilitre",
        strength = BigDecimal("200"),
        strengthUnit = "mg",
        foodRelation = FoodRelation.ANY,
        scheduleMode = ScheduleMode.PRN,
        maxPerDay = PRN_LIMIT,
        times = emptyList(),
        startDate = LocalDate.of(2026, 5, 1),
        endDate = null,
        stockLeft = null,
        stockThresholdDays = null,
        archivedAt = ARCHIVED_AT,
        archiveReason = ArchiveReason.COURSE_FINISHED,
    )

    fun taken() = Intake(
        id = "i1",
        medicationId = ACTIVE_ID,
        scheduledAt = SCHEDULED_AT,
        status = IntakeStatus.TAKEN,
        actedAt = ACTED_AT,
        source = IntakeSource.PHONE,
        actorProfileId = null,
    )

    fun pending() = Intake(
        id = "i2",
        medicationId = ARCHIVED_ID,
        scheduledAt = SCHEDULED_AT.plusHours(12),
        status = IntakeStatus.PENDING,
        actedAt = null,
        source = null,
        actorProfileId = null,
    )

    /** Отметка чужого приёма владельцем телефона (ПР-12): поле actor_profile_id заполнено. */
    fun markedByOwner() = Intake(
        id = "i3",
        medicationId = ARCHIVED_ID,
        scheduledAt = SCHEDULED_AT.plusDays(1),
        status = IntakeStatus.SKIPPED,
        actedAt = ACTED_AT,
        source = IntakeSource.PHONE,
        actorProfileId = SELF_ID,
    )

    fun settings() = TransferSettings(
        reminderSound = ReminderSound.HEATHER,
        persistentReminder = true,
        snoozeMinutes = SNOOZE_MINUTES,
        quietHours = QuietHours(from = LocalTime.of(23, 30), to = LocalTime.of(7, 0)),
    )

    fun snapshot(): TransferSnapshot = TransferSnapshot.of(
        exportedAt = EXPORTED_AT,
        app = AppStamp(versionName = "1.0", versionCode = 10),
        profiles = listOf(self(), mother()),
        medications = listOf(active(), archived()),
        intakes = listOf(taken(), pending(), markedByOwner()),
        settings = settings(),
    )
}
