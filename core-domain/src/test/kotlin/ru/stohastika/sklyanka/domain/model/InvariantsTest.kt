package ru.stohastika.sklyanka.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

@DisplayName("Conditional field rules of the data model hold")
class InvariantsTest {

    private val moscow: ZoneId = ZoneId.of("Europe/Moscow")

    @Test
    fun `a well formed scheduled medication has no violations`() {
        assertTrue(Invariants.violations(Fixtures.scheduledMedication()).isEmpty())
    }

    @Test
    fun `a well formed prn medication has no violations`() {
        assertTrue(Invariants.violations(Fixtures.prnMedication()).isEmpty())
    }

    @Test
    fun `dose amount must be positive`() {
        val medication = Fixtures.scheduledMedication().copy(doseAmount = BigDecimal.ZERO)

        assertEquals(listOf("INV-4"), Invariants.violations(medication))
    }

    @Test
    fun `strength and its unit are filled together or not at all`() {
        val medication = Fixtures.scheduledMedication().copy(strengthUnit = null)

        assertEquals(listOf("INV-5"), Invariants.violations(medication))
    }

    @Test
    fun `a scheduled course without times is rejected`() {
        val medication = Fixtures.scheduledMedication().copy(times = emptyList())

        assertEquals(listOf("INV-6"), Invariants.violations(medication))
    }

    @Test
    fun `repeated times in a scheduled course are rejected`() {
        val morning = Fixtures.MORNING
        val medication = Fixtures.scheduledMedication().copy(times = listOf(morning, morning))

        assertEquals(listOf("INV-6"), Invariants.violations(medication))
    }

    @Test
    fun `a prn course must not carry times`() {
        val medication = Fixtures.prnMedication().copy(times = listOf(Fixtures.MORNING))

        assertEquals(listOf("INV-6"), Invariants.violations(medication))
    }

    @Test
    fun `an open ended course is valid and is not an incomplete record`() {
        val medication = Fixtures.scheduledMedication().copy(endDate = null)

        assertTrue(Invariants.violations(medication).isEmpty())
    }

    @Test
    fun `an end date before the start date is rejected`() {
        val medication = Fixtures.scheduledMedication().copy(endDate = Fixtures.START_DATE.minusDays(1))

        assertEquals(listOf("INV-7"), Invariants.violations(medication))
    }

    @Test
    fun `a negative stock is rejected`() {
        val medication = Fixtures.scheduledMedication().copy(stockLeft = BigDecimal("-0.5"))

        assertEquals(listOf("INV-8"), Invariants.violations(medication))
    }

    @Test
    fun `a non-positive package size is rejected`() {
        val zero = Fixtures.scheduledMedication().copy(packageSize = BigDecimal.ZERO)
        assertEquals(listOf("INV-20"), Invariants.violations(zero))

        val negative = Fixtures.scheduledMedication().copy(packageSize = BigDecimal("-0.5"))
        assertEquals(listOf("INV-20"), Invariants.violations(negative))
    }

    @Test
    fun `an unknown package size is a valid state`() {
        assertTrue(Invariants.violations(Fixtures.scheduledMedication()).isEmpty())
        assertTrue(Invariants.violations(Fixtures.scheduledMedication().copy(packageSize = null)).isEmpty())
    }

    @Test
    fun `a fractional package size passes`() {
        val medication = Fixtures.scheduledMedication().copy(packageSize = BigDecimal("0.5"))

        assertTrue(Invariants.violations(medication).isEmpty())
    }

    @Test
    fun `archive moment and archive reason are filled together`() {
        val medication = Fixtures.scheduledMedication().copy(archivedAt = Instant.EPOCH)

        assertEquals(listOf("INV-9"), Invariants.violations(medication))
    }

    @Test
    fun `a deleted medication must not carry archive fields`() {
        val deleted = Fixtures.scheduledMedication().copy(deletedAt = Instant.EPOCH)
        assertTrue(Invariants.violations(deleted).isEmpty())

        val archivedToo = Fixtures.scheduledMedication().copy(
            deletedAt = Instant.EPOCH,
            archivedAt = Instant.EPOCH,
            archiveReason = ArchiveReason.MANUAL,
        )
        assertEquals(listOf("INV-21"), Invariants.violations(archivedToo))
    }

    @Test
    fun `a deleted medication is recognised by state`() {
        assertTrue(Fixtures.scheduledMedication().copy(deletedAt = Instant.EPOCH).isDeleted)
        assertEquals(null, Fixtures.scheduledMedication().deletedAt)
    }

    @Test
    fun `shape belongs to tablets only`() {
        val medication = Fixtures.scheduledMedication().copy(form = MedicationForm.SYRUP)

        assertEquals(listOf("INV-16"), Invariants.violations(medication))
    }

    @Test
    fun `a capsule with a shape breaks the invariant`() {
        // ОВ-88: силуэт капсулы задан формой, вид к ней неприменим.
        val medication = Fixtures.scheduledMedication().copy(form = MedicationForm.CAPSULE)

        assertEquals(listOf("INV-16"), Invariants.violations(medication))
    }

    @Test
    fun `max per day belongs to prn only`() {
        val limit = Fixtures.PRN_MAX_PER_DAY
        val medication = Fixtures.scheduledMedication().copy(maxPerDay = limit)

        assertEquals(listOf("INV-17"), Invariants.violations(medication))
    }

    @Test
    fun `a pending intake carries no marking fields`() {
        val intake = Fixtures.pendingIntake().copy(source = IntakeSource.PHONE)

        assertEquals(listOf("INV-10"), Invariants.violations(intake))
    }

    @Test
    fun `a marked intake carries both the moment and the source`() {
        val intake = Fixtures.takenIntake().copy(source = null)

        assertEquals(listOf("INV-10"), Invariants.violations(intake))
    }

    @Test
    fun `a pending intake has no actor`() {
        val intake = Fixtures.pendingIntake().copy(actorProfileId = Fixtures.OTHER_PROFILE_ID)

        assertEquals(listOf("INV-11"), Invariants.violations(intake))
    }

    @Test
    fun `a prn intake is marked at its scheduled moment`() {
        val medication = Fixtures.prnMedication()
        val actedAt = Instant.parse("2026-09-25T11:30:00Z")
        val intake = Fixtures.takenIntake(actedAt).copy(
            scheduledAt = actedAt.atZone(moscow).toLocalDateTime(),
        )

        assertTrue(Invariants.violations(intake, medication, moscow).isEmpty())
    }

    @Test
    fun `a prn intake whose scheduled time differs from the marking moment is rejected`() {
        val medication = Fixtures.prnMedication()
        val intake = Fixtures.takenIntake()

        assertEquals(listOf("INV-14"), Invariants.violations(intake, medication, moscow))
    }

    @Test
    fun `a scheduled intake outside the course is rejected`() {
        val medication = Fixtures.scheduledMedication().copy(endDate = Fixtures.SHORT_COURSE_END)
        val intake = Fixtures.pendingIntake()

        assertEquals(listOf("INV-15"), Invariants.violations(intake, medication, moscow))
    }

    @Test
    fun `the actor of a marking is never the owner of the medication`() {
        val medication = Fixtures.scheduledMedication()
        val intake = Fixtures.takenIntake().copy(actorProfileId = medication.profileId)

        assertEquals(listOf("INV-18"), Invariants.violations(intake, medication, moscow))
    }

    @Test
    fun `requireValid throws on a non empty violation list`() {
        val failure = assertThrows(InvariantViolationException::class.java) {
            Invariants.requireValid(listOf("INV-4"))
        }

        assertEquals(listOf("INV-4"), failure.violations)
    }

    @Test
    fun `a blank profile name is rejected`() {
        assertTrue(Invariants.violations(Fixtures.profile(name = " ")).isNotEmpty())
    }

    // --- SKL-107: границы инвариантов на путях записи ------------------------------------------

    @Test
    fun `a medication cannot change its owner`() {
        val stored = Fixtures.scheduledMedication()
        val reassigned = stored.copy(profileId = Fixtures.OTHER_PROFILE_ID)

        assertEquals(listOf("INV-3"), Invariants.ownershipViolations(stored.profileId, reassigned))
        assertTrue(Invariants.ownershipViolations(stored.profileId, stored).isEmpty())
    }

    @Test
    fun `an actor equal to the owner of the medication breaks INV-18`() {
        val owner = Fixtures.PROFILE_ID

        assertEquals(
            listOf("INV-18"),
            Invariants.actorOwnershipViolations(actorProfileId = owner, medicationProfileId = owner),
        )
        assertTrue(Invariants.actorOwnershipViolations(null, owner).isEmpty())
        assertTrue(Invariants.actorOwnershipViolations(Fixtures.OTHER_PROFILE_ID, owner).isEmpty())
    }

    @Test
    fun `the marking boundary keeps foreign and actor but not the course or the prn pairing`() {
        val medication = Fixtures.scheduledMedication().copy(endDate = Fixtures.SHORT_COURSE_END)
        val foreignActor = Fixtures.takenIntake().copy(actorProfileId = medication.profileId)

        // Живущий приём вне курса — законная отметка: даты курса правятся после приёмов.
        assertTrue(Invariants.markingViolations(Fixtures.pendingIntake(), medication).isEmpty())
        assertEquals(listOf("INV-18"), Invariants.markingViolations(foreignActor, medication))
    }

    @Test
    fun `the marking boundary still rejects fields that contradict the status`() {
        val intake = Fixtures.takenIntake().copy(source = null)

        assertEquals(listOf("INV-10"), Invariants.markingViolations(intake, Fixtures.scheduledMedication()))
    }

    @Test
    fun `prn birth requires the marking moment and the scheduled minute to agree`() {
        val medication = Fixtures.prnMedication()

        assertEquals(listOf("INV-14"), Invariants.prnBirthViolations(Fixtures.takenIntake(), medication, moscow))

        val actedAt = Instant.parse("2026-09-25T05:00:00Z")
        val born = Fixtures.takenIntake(actedAt).copy(
            scheduledAt = actedAt.atZone(moscow).toLocalDateTime(),
        )
        assertTrue(Invariants.prnBirthViolations(born, medication, moscow).isEmpty())
    }

    @Test
    fun `the marking boundary ignores prn pairing of leftover scheduled rows`() {
        // Режим поменяли после рождения приёма: отметка живущего приёма законна,
        // а назначенное время она не переписывает.
        assertTrue(Invariants.markingViolations(Fixtures.takenIntake(), Fixtures.prnMedication()).isEmpty())
    }

    @Test
    fun `the full boundary adds the course window to the prn birth set`() {
        val medication = Fixtures.prnMedication().copy(endDate = Fixtures.SHORT_COURSE_END)

        assertEquals(
            listOf("INV-14", "INV-15"),
            Invariants.violations(Fixtures.takenIntake(), medication, moscow),
        )
    }

    @Test
    fun `the arbitrary-model boundary refuses an expected row of a prn medication`() {
        val intake = Fixtures.pendingIntake()

        assertEquals(listOf("INV-14"), Invariants.writeViolations(intake, ScheduleMode.PRN))
        assertTrue(Invariants.writeViolations(intake, ScheduleMode.SCHEDULED).isEmpty())
        assertEquals(
            listOf("INV-10"),
            Invariants.writeViolations(Fixtures.takenIntake().copy(source = null), ScheduleMode.SCHEDULED),
        )
    }

    // --- SKL-117: вид расписания (ИНВ-23…ИНВ-25) -------------------------------------------

    @Test
    fun `a weekdays schedule without days breaks INV-23`() {
        val medication = Fixtures.scheduledMedication().copy(schedule = ScheduleRule.Weekdays(emptySet()))

        assertEquals(listOf("INV-23"), Invariants.violations(medication))
    }

    @Test
    fun `a weekdays schedule with one day or with several is valid`() {
        val one = Fixtures.scheduledMedication().copy(schedule = ScheduleRule.Weekdays(setOf(DayOfWeek.MONDAY)))
        val several = Fixtures.scheduledMedication()
            .copy(schedule = ScheduleRule.Weekdays(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)))

        assertTrue(Invariants.violations(one).isEmpty())
        assertTrue(Invariants.violations(several).isEmpty())
    }

    @Test
    fun `an interval shorter than two days breaks INV-23`() {
        val medication = Fixtures.scheduledMedication().copy(schedule = ScheduleRule.EveryNDays(1))

        assertEquals(listOf("INV-23"), Invariants.violations(medication))
        assertTrue(
            Invariants.violations(Fixtures.scheduledMedication().copy(schedule = ScheduleRule.EveryNDays(3))).isEmpty(),
        )
    }

    @Test
    fun `a cycle without intake or without rest days breaks INV-23`() {
        val noIntake = Fixtures.scheduledMedication().copy(schedule = ScheduleRule.Cycles(onDays = 0, offDays = 7))
        val noRest = Fixtures.scheduledMedication().copy(schedule = ScheduleRule.Cycles(onDays = 21, offDays = 0))

        assertEquals(listOf("INV-23"), Invariants.violations(noIntake))
        assertEquals(listOf("INV-23"), Invariants.violations(noRest))
        assertTrue(
            Invariants.violations(Fixtures.scheduledMedication().copy(schedule = ScheduleRule.Cycles(21, 7))).isEmpty(),
        )
    }

    @Test
    fun `a staged schedule without stages breaks INV-23`() {
        val medication = Fixtures.scheduledMedication().copy(schedule = ScheduleRule.Staged(emptyList()))

        assertEquals(listOf("INV-23"), Invariants.violations(medication))
    }

    @Test
    fun `a stage without a dose or without days breaks INV-24`() {
        val withoutDose = Fixtures.scheduledMedication()
            .copy(schedule = ScheduleRule.Staged(listOf(Stage(BigDecimal.ZERO, days = 7))))
        val withoutDays = Fixtures.scheduledMedication()
            .copy(schedule = ScheduleRule.Staged(listOf(Stage(BigDecimal.ONE, days = 0))))

        assertEquals(listOf("INV-24"), Invariants.violations(withoutDose))
        assertEquals(listOf("INV-24"), Invariants.violations(withoutDays))
    }

    @Test
    fun `stages that end after the course break INV-24`() {
        val stages = listOf(Stage(BigDecimal("0.5"), days = 7), Stage(BigDecimal.ONE, days = 24))
        // Курс на тридцать дней, схема на тридцать один: последний день схемы за концом курса.
        val medication = Fixtures.scheduledMedication().copy(
            schedule = ScheduleRule.Staged(stages),
            endDate = Fixtures.START_DATE.plusDays(29),
        )

        assertEquals(listOf("INV-24"), Invariants.violations(medication))
    }

    @Test
    fun `stages that end exactly on the course end are valid`() {
        val stages = listOf(Stage(BigDecimal("0.5"), days = 7), Stage(BigDecimal.ONE, days = 24))
        val medication = Fixtures.scheduledMedication().copy(
            schedule = ScheduleRule.Staged(stages),
            endDate = Fixtures.START_DATE.plusDays(30),
        )

        assertTrue(Invariants.violations(medication).isEmpty())
    }

    @Test
    fun `stages of an open ended course are valid`() {
        val medication = Fixtures.scheduledMedication()
            .copy(schedule = ScheduleRule.Staged(listOf(Stage(BigDecimal.ONE, days = 365))), endDate = null)

        assertTrue(Invariants.violations(medication).isEmpty())
    }

    @Test
    fun `a prn course with a schedule rule breaks INV-25`() {
        val medication = Fixtures.prnMedication().copy(schedule = ScheduleRule.Weekdays(setOf(DayOfWeek.MONDAY)))

        assertEquals(listOf("INV-25"), Invariants.violations(medication))
        assertTrue(Invariants.violations(Fixtures.prnMedication()).isEmpty())
    }
}
