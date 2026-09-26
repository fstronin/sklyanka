package ru.heuri.sklyanka.domain.intake

import ru.heuri.sklyanka.domain.model.Fixtures
import ru.heuri.sklyanka.domain.model.Intake
import ru.heuri.sklyanka.domain.model.Medication
import ru.heuri.sklyanka.domain.model.Profile
import ru.heuri.sklyanka.domain.schedule.FakeIntakeRepository
import ru.heuri.sklyanka.domain.schedule.FakeMedicationRepository
import ru.heuri.sklyanka.domain.schedule.FakeProfileRepository
import ru.heuri.sklyanka.domain.schedule.RecordingOperationLog
import ru.heuri.sklyanka.domain.time.FixedTimeSource
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Обвязка тестов жизненного цикла приёма: хранилище в памяти и управляемые часы.
 *
 * Хранилище — не Room: здесь проверяются правила, а неразрывность транзакции проверяется
 * инструментальным тестом `core-data` на настоящей базе. Часы управляемые, иначе граница
 * окна и суточный лимит непроверяемы (НФТ-48).
 */
internal class IntakeTestWorld {

    val zone: ZoneId = ZoneId.of("Europe/Moscow")
    val clock = FixedTimeSource(momentOf(LocalDateTime.of(2026, 9, 25, 7, 55)), zone)

    val profiles = FakeProfileRepository()
    val medications = FakeMedicationRepository(clock)
    val intakes = FakeIntakeRepository(clock, medications)
    val log = RecordingOperationLog()
    val window: IntakeWindow = EndOfDayIntakeWindow()

    val mark = MarkIntakeUseCase(intakes, medications, profiles, clock)
    val createPrn = CreatePrnIntakeUseCase(intakes, medications, profiles, clock)
    val sweep = SweepMissedIntakesUseCase(intakes, window, clock, log)

    init {
        profiles.put(Fixtures.profile())
        medications.put(Fixtures.scheduledMedication())
    }

    /** Местный момент времени сценария в UTC: сравнение отметок идёт моментами (ПР-3). */
    fun momentOf(local: LocalDateTime): Instant = local.atZone(zone).toInstant()

    fun moveTo(local: LocalDateTime) {
        clock.advanceTo(momentOf(local))
    }

    fun pending(at: LocalDateTime = Fixtures.SCHEDULED_AT, id: String = Fixtures.INTAKE_ID): Intake {
        val intake = Fixtures.pendingIntake().copy(id = id, scheduledAt = at)
        intakes.put(intake)
        return intake
    }

    fun stored(id: String = Fixtures.INTAKE_ID): Intake = requireNotNull(intakes.stored[id]) { "no intake $id" }

    fun medication(): Medication = requireNotNull(medications.stored[Fixtures.MEDICATION_ID])

    fun stock(): BigDecimal? = medication().stockLeft

    fun useOnDemand(maxPerDay: Int? = Fixtures.PRN_MAX_PER_DAY) {
        medications.put(Fixtures.prnMedication().copy(maxPerDay = maxPerDay))
    }

    /** Второй профиль: приём чужого профиля подписывается владельцем телефона (ПР-12). */
    fun foreignProfile(): Profile {
        val foreign = Fixtures.profile(name = "mother", isSelf = false).copy(id = Fixtures.OTHER_PROFILE_ID)
        profiles.put(foreign)
        medications.put(medication().copy(profileId = foreign.id))
        return foreign
    }
}
