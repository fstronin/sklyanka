package ru.heuri.sklyanka.feature.settings.transfer

import ru.heuri.sklyanka.domain.model.FoodRelation
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.model.MedicationForm
import ru.heuri.sklyanka.domain.model.ScheduleMode
import ru.heuri.sklyanka.domain.model.TabletShape
import ru.heuri.sklyanka.domain.transfer.MatchDecision
import ru.heuri.sklyanka.domain.transfer.MatchDifference
import ru.heuri.sklyanka.domain.transfer.MedicationMatch
import ru.heuri.sklyanka.domain.transfer.TransferCounts
import ru.heuri.sklyanka.domain.transfer.TransferMode
import ru.heuri.sklyanka.domain.transfer.TransferNotice
import ru.heuri.sklyanka.domain.transfer.TransferReport
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Заготовки состояний экрана переноса.
 *
 * Числа те же, что в «Предыстории» приёмки: пять лекарств, двенадцать в архиве, история
 * с 12 февраля 2026, два профиля. Названия латиницей — кириллица в строковом литерале
 * Kotlin запрещена (НФТ-42).
 */
internal object TransferScreenFixtures {

    const val SAFETY_COPY = "sklyanka-before-replace.json"
    const val FILE_MEDICATION_ID = "file-med"
    const val DEVICE_MEDICATION_ID = "device-med"
    const val EXPORT_FILE_NAME = "sklyanka-2026-10-12.json"
    const val HISTORY_MONTHS = 8

    val ZONE: ZoneId = ZoneId.of("Europe/Moscow")
    val EXPORTED_AT: Instant = Instant.parse("2026-10-05T18:14:03Z")
    val LAST_EXPORT: LocalDate = LocalDate.of(2026, 9, 2)

    val DEVICE_COUNTS = TransferCounts(
        profiles = 2,
        medications = 5,
        archived = 12,
        intakes = 3184,
        historyFrom = LocalDate.of(2026, 2, 12),
    )

    val FILE_COUNTS = TransferCounts(
        profiles = 3,
        medications = 7,
        archived = 4,
        intakes = 2100,
        historyFrom = LocalDate.of(2026, 1, 1),
    )

    fun overview() = TransferUiState(
        deviceCounts = DEVICE_COUNTS,
        deviceHistoryMonths = HISTORY_MONTHS,
        exportFileName = EXPORT_FILE_NAME,
        zone = ZONE,
    )

    /** Совпадение «Магний B6, 48 мг» с различием во времени приёма — случай из приёмки. */
    fun match(): MedicationMatch = MedicationMatch(
        device = magnesium(id = DEVICE_MEDICATION_ID),
        file = magnesium(id = FILE_MEDICATION_ID, times = listOf(LocalTime.of(9, 0), LocalTime.of(21, 0))),
        differences = listOf(MatchDifference.TIMES),
    )

    fun report() = TransferReport(
        mode = TransferMode.MERGE,
        profilesAdded = 1,
        medicationsAdded = 2,
        medicationsUpdated = 1,
        intakesAdded = 40,
        intakesUpdated = 2,
        intakesSkipped = 3,
        decisions = mapOf(MatchDecision.KEEP_MINE to 1, MatchDecision.TAKE_FROM_FILE to 1),
        notices = listOf(
            TransferNotice.UnknownEnum("form", FILE_MEDICATION_ID),
            TransferNotice.SoundNotFound,
        ),
    )

    private fun magnesium(id: String, times: List<LocalTime> = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))) =
        Medication(
            id = id,
            profileId = "p1",
            name = "Magnesium B6",
            form = MedicationForm.TABLET,
            shape = TabletShape.ROUND_SCORED,
            doseAmount = BigDecimal("2"),
            doseUnit = "tablet",
            strength = BigDecimal("48"),
            strengthUnit = "mg",
            foodRelation = FoodRelation.BEFORE_MEAL,
            scheduleMode = ScheduleMode.SCHEDULED,
            maxPerDay = null,
            times = times,
            startDate = LocalDate.of(2026, 2, 12),
            endDate = null,
            stockLeft = BigDecimal("9"),
            stockThresholdDays = null,
            archivedAt = null,
            archiveReason = null,
        )
}
