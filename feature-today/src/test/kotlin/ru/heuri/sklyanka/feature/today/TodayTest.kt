package ru.heuri.sklyanka.feature.today

import ru.heuri.sklyanka.domain.model.FoodRelation
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.IntakeSource
import ru.heuri.sklyanka.domain.model.IntakeStatus
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.model.MedicationForm
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.model.TabletShape
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Сутки сценариев приёмки SKL-08 — пятница, 25 сентября 2026.
 *
 * Дата взята из `acceptance.feature` и макетов: сверять поведение с документом проще,
 * когда числа в нём и в тесте одни и те же.
 */
internal object TodayTest {

    val DATE: LocalDate = LocalDate.of(2026, 9, 25)
    val ZONE: ZoneId = ZoneId.of("Europe/Moscow")
    const val PROFILE = "profile-self"

    fun at(hour: Int, minute: Int = 0): LocalDateTime = DATE.atTime(hour, minute)

    fun moment(local: LocalDateTime): Instant = local.atZone(ZONE).toInstant()

    @Suppress("LongParameterList")
    fun medication(
        id: String,
        times: List<LocalTime> = listOf(LocalTime.of(8, 0)),
        mode: ScheduleMode = ScheduleMode.SCHEDULED,
        dose: String = "1",
        stockLeft: String? = null,
        thresholdDays: Int? = null,
        maxPerDay: Int? = null,
    ) = Medication(
        id = id,
        profileId = PROFILE,
        name = id,
        form = MedicationForm.TABLET,
        shape = TabletShape.ROUND,
        doseAmount = BigDecimal(dose),
        doseUnit = "tablet",
        strength = null,
        strengthUnit = null,
        foodRelation = FoodRelation.ANY,
        scheduleMode = mode,
        maxPerDay = maxPerDay,
        times = times,
        startDate = DATE.minusMonths(1),
        endDate = null,
        stockLeft = stockLeft?.let { BigDecimal(it) },
        stockThresholdDays = thresholdDays,
        archivedAt = null,
        archiveReason = null,
    )

    fun intake(id: String, medicationId: String, at: LocalDateTime, status: IntakeStatus = IntakeStatus.PENDING) =
        Intake(
            id = id,
            medicationId = medicationId,
            scheduledAt = at,
            status = status,
            actedAt = if (status == IntakeStatus.PENDING) null else moment(at),
            source = if (status == IntakeStatus.PENDING) null else IntakeSource.PHONE,
            actorProfileId = null,
        )
}
