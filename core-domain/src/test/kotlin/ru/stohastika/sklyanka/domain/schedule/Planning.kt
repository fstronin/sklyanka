package ru.stohastika.sklyanka.domain.schedule

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
import java.time.ZoneId

/**
 * Заготовки для тестов генерации приёмов.
 *
 * Даты и времена совпадают с `acceptance/src/main/resources/features/SKL-06.feature`: «сейчас 25 сентября
 * 2026, 07:00», окно — семь суток, то есть 25 сентября … 1 октября. Названий настоящих
 * лекарств и имён людей здесь нет: тестовые данные попадают в отчёты (НФТ-19).
 */
internal object Planning {

    const val OWNER_ID = "11111111-1111-4111-8111-111111111111"
    const val RELATIVE_ID = "22222222-2222-4222-8222-222222222222"
    const val MEDICATION_ID = "33333333-3333-4333-8333-333333333333"
    const val OTHER_MEDICATION_ID = "44444444-4444-4444-8444-444444444444"

    /** Рабочее значение глубины окна (ОВ-8): семь суток, считая текущие. */
    const val DEPTH_DAYS = 7

    val ZONE: ZoneId = ZoneId.of("Europe/Moscow")

    val TODAY: LocalDate = LocalDate.of(2026, 9, 25)
    val WINDOW_END: LocalDate = LocalDate.of(2026, 10, 1)
    val MORNING: LocalTime = LocalTime.of(8, 0)
    val NOON: LocalTime = LocalTime.of(14, 0)
    val EVENING: LocalTime = LocalTime.of(20, 0)

    val POLICY = IntakeGenerationPolicy(windowDepthDays = DEPTH_DAYS)

    fun clock(at: LocalDateTime = TODAY.atTime(7, 0), zone: ZoneId = ZONE): FixedTimeSource =
        FixedTimeSource(at.atZone(zone).toInstant(), zone)

    fun window(today: LocalDate = TODAY): GenerationWindow = GenerationWindow.of(today, DEPTH_DAYS)

    @Suppress("LongParameterList")
    fun medication(
        id: String = MEDICATION_ID,
        profileId: String = OWNER_ID,
        times: List<LocalTime> = listOf(MORNING),
        startDate: LocalDate = TODAY,
        endDate: LocalDate? = null,
        mode: ScheduleMode = ScheduleMode.SCHEDULED,
        archivedAt: Instant? = null,
        /** Мягко удалённое лекарство (ПР-14): с `archivedAt` не сочетается (ИНВ-21). */
        deletedAt: Instant? = null,
    ) = Medication(
        id = id,
        profileId = profileId,
        name = if (id == MEDICATION_ID) "med-a" else "med-b",
        form = MedicationForm.TABLET,
        shape = null,
        doseAmount = BigDecimal.ONE,
        doseUnit = "unit",
        strength = null,
        strengthUnit = null,
        foodRelation = FoodRelation.ANY,
        scheduleMode = mode,
        maxPerDay = if (mode == ScheduleMode.PRN) PRN_MAX_PER_DAY else null,
        times = if (mode == ScheduleMode.SCHEDULED) times else emptyList(),
        startDate = startDate,
        endDate = endDate,
        stockLeft = null,
        stockThresholdDays = null,
        archivedAt = archivedAt,
        archiveReason = archivedAt?.let { ArchiveReason.MANUAL },
        deletedAt = deletedAt,
    )

    fun pending(at: LocalDateTime, id: String = "pending-$at", medicationId: String = MEDICATION_ID) = Intake(
        id = id,
        medicationId = medicationId,
        scheduledAt = at,
        status = IntakeStatus.PENDING,
        actedAt = null,
        source = null,
        actorProfileId = null,
    )

    fun taken(at: LocalDateTime, id: String = "taken-$at", medicationId: String = MEDICATION_ID) = Intake(
        id = id,
        medicationId = medicationId,
        scheduledAt = at,
        status = IntakeStatus.TAKEN,
        actedAt = at.atZone(ZONE).toInstant(),
        source = IntakeSource.PHONE,
        actorProfileId = null,
    )

    private const val PRN_MAX_PER_DAY = 3
}

/** Перевести часы на заданное местное время, не трогая пояс. */
internal fun FixedTimeSource.setLocal(at: LocalDateTime) {
    advanceTo(at.atZone(zone()).toInstant())
}
