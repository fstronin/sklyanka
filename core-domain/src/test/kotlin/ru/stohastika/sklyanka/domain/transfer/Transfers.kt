package ru.stohastika.sklyanka.domain.transfer

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
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Заготовки для тестов переноса.
 *
 * Данные «Предыстории» приёмки: свой профиль, профиль подопечного с дублированием
 * напоминаний, «Магний B6» 48 мг в 08:00 и 20:00, «Нурофен» 200 мг по потребности.
 *
 * Названия записаны латиницей, потому что кириллица в строковом литерале Kotlin
 * запрещена правилом RussianTextInCode (НФТ-42) — включая тесты. Там, где проверяется
 * именно кириллическое правило («ё» → «е»), символы задаются escape-последовательностями:
 * в исходнике их нет, а в строке они есть.
 */
internal object Transfers {

    const val SELF_ID = "p1"
    const val MOTHER_ID = "p2"
    const val MAGNESIUM_ID = "m1"
    const val IBUPROFEN_ID = "m2"

    const val MAGNESIUM = "Magnesium B6"
    const val IBUPROFEN = "Nurofen"
    const val THYROXINE = "Eutiroks"
    const val MOTHER = "Mother"

    val EXPORTED_AT: Instant = Instant.parse("2026-10-05T18:14:03Z")
    val START_DATE: LocalDate = LocalDate.of(2026, 2, 12)
    val MORNING: LocalTime = LocalTime.of(8, 0)
    val EVENING: LocalTime = LocalTime.of(20, 0)
    val LATE_MORNING: LocalTime = LocalTime.of(9, 0)
    val LATE_EVENING: LocalTime = LocalTime.of(21, 0)

    val APP = AppStamp(versionName = "1.0", versionCode = 10)

    fun self(name: String = "Owner", id: String = SELF_ID) = Profile(
        id = id,
        name = name,
        color = SELF_COLOR,
        isSelf = true,
        mirrorReminders = false,
    )

    fun mother(id: String = MOTHER_ID, mirrorReminders: Boolean = true) = Profile(
        id = id,
        name = MOTHER,
        color = OTHER_COLOR,
        isSelf = false,
        mirrorReminders = mirrorReminders,
    )

    fun magnesium(
        id: String = MAGNESIUM_ID,
        profileId: String = SELF_ID,
        times: List<LocalTime> = listOf(MORNING, EVENING),
        stockLeft: BigDecimal? = BigDecimal("9"),
    ) = Medication(
        id = id,
        profileId = profileId,
        name = MAGNESIUM,
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
        startDate = START_DATE,
        endDate = null,
        stockLeft = stockLeft,
        stockThresholdDays = null,
        archivedAt = null,
        archiveReason = null,
    )

    fun ibuprofen(id: String = IBUPROFEN_ID, profileId: String = SELF_ID) = magnesium(id, profileId).copy(
        name = IBUPROFEN,
        strength = BigDecimal("200"),
        scheduleMode = ScheduleMode.PRN,
        times = emptyList(),
        maxPerDay = PRN_LIMIT,
        doseAmount = BigDecimal("1"),
    )

    fun archivedCourse(id: String = "m9", profileId: String = SELF_ID) = magnesium(id, profileId).copy(
        name = "Amoksiklav",
        strength = BigDecimal("500"),
        archivedAt = Instant.parse("2026-08-30T07:02:00Z"),
        archiveReason = ArchiveReason.COURSE_FINISHED,
    )

    fun thyroxine(id: String = "m3", profileId: String = SELF_ID) = magnesium(id, profileId).copy(
        name = THYROXINE,
        strength = BigDecimal("50"),
        strengthUnit = "mcg",
        times = listOf(MORNING),
        doseAmount = BigDecimal("1"),
    )

    fun pending(
        id: String,
        medicationId: String = MAGNESIUM_ID,
        at: LocalDateTime = LocalDateTime.of(2026, 10, 5, 8, 0),
    ) = Intake(
        id = id,
        medicationId = medicationId,
        scheduledAt = at,
        status = IntakeStatus.PENDING,
        actedAt = null,
        source = null,
        actorProfileId = null,
    )

    fun taken(
        id: String,
        medicationId: String = MAGNESIUM_ID,
        at: LocalDateTime = LocalDateTime.of(2026, 10, 5, 8, 0),
        actedAt: Instant = Instant.parse("2026-10-05T05:05:00Z"),
    ) = pending(id, medicationId, at).copy(
        status = IntakeStatus.TAKEN,
        actedAt = actedAt,
        source = IntakeSource.PHONE,
    )

    fun missed(
        id: String,
        medicationId: String = MAGNESIUM_ID,
        at: LocalDateTime = LocalDateTime.of(2026, 10, 5, 8, 0),
        actedAt: Instant = Instant.parse("2026-10-05T20:59:00Z"),
    ) = pending(id, medicationId, at).copy(
        status = IntakeStatus.MISSED,
        actedAt = actedAt,
        source = IntakeSource.AUTO,
    )

    fun skipped(
        id: String,
        medicationId: String = MAGNESIUM_ID,
        at: LocalDateTime = LocalDateTime.of(2026, 10, 5, 8, 0),
        actedAt: Instant = Instant.parse("2026-10-05T06:00:00Z"),
    ) = pending(id, medicationId, at).copy(
        status = IntakeStatus.SKIPPED,
        actedAt = actedAt,
        source = IntakeSource.PHONE,
    )

    fun snapshot(
        profiles: List<Profile> = listOf(self(), mother()),
        medications: List<Medication> = listOf(magnesium(), ibuprofen()),
        intakes: List<Intake> = listOf(taken("i1")),
        settings: TransferSettings = TransferSettings.DEFAULT,
    ) = TransferSnapshot.of(
        exportedAt = EXPORTED_AT,
        app = APP,
        profiles = profiles,
        medications = medications,
        intakes = intakes,
        settings = settings,
    )

    fun deviceState(
        profiles: List<Profile> = listOf(self(), mother()),
        medications: List<Medication> = listOf(magnesium(), ibuprofen()),
        intakes: List<Intake> = emptyList(),
    ) = DeviceState(profiles, medications, intakes)

    private const val SELF_COLOR = 0x00B4512F
    private const val OTHER_COLOR = 0x008F3E23
    private const val PRN_LIMIT = 3
}

/**
 * Предсказуемый источник идентификаторов.
 *
 * Тест «идентификатор файла устройству не навязан» со случайным UUID внутри доказывал бы
 * только то, что значения разные, — а проверить нужно, что запись устройства осталась
 * на своём идентификаторе.
 */
internal class FakeIds(private val prefix: String = "new-") : TransferIdSource {
    private var next = 0

    override fun newId(): String = prefix + (++next)
}
