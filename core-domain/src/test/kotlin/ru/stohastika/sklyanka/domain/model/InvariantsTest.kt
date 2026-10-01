package ru.stohastika.sklyanka.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal
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
}
