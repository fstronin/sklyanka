package ru.stohastika.sklyanka.domain.medication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Порядок списка лекарств «что ближе — то выше» (ПМ-15 п. 1).
 *
 * Ближайший приём — предметное правило, а не разметка: оно проверяется на JVM без
 * Android и без базы. Время приёма — локальное (НФТ-34), «сейчас» подставляется тестом
 * тем же способом, что в домене подставляет `TimeSource` (НФТ-48).
 */
@DisplayName("The medication list orders by the nearest next intake")
class MedicationListOrderTest {

    // --- Ближайший приём ----------------------------------------------------------------

    @Test
    fun `a later time today is the nearest intake`() {
        val medication = scheduled(times = listOf(EVENING))

        assertEquals(TODAY.atTime(EVENING), MedicationListOrder.nextIntakeAt(medication, NOW))
    }

    @Test
    fun `a time already passed today moves to tomorrow`() {
        val medication = scheduled(times = listOf(MORNING))

        assertEquals(TOMORROW.atTime(MORNING), MedicationListOrder.nextIntakeAt(medication, NOW))
    }

    @Test
    fun `among several times the earliest still ahead is chosen`() {
        val medication = scheduled(times = listOf(MORNING, AFTERNOON, EVENING))

        assertEquals(TODAY.atTime(AFTERNOON), MedicationListOrder.nextIntakeAt(medication, NOW))
    }

    @Test
    fun `a time exactly now stays the nearest`() {
        val medication = scheduled(times = listOf(NOW.toLocalTime()))

        assertEquals(NOW, MedicationListOrder.nextIntakeAt(medication, NOW))
    }

    @Test
    fun `a course starting in the future keeps its first day`() {
        val medication = scheduled(
            times = listOf(MORNING),
            startDate = LocalDate.of(2026, 10, 1),
        )

        assertEquals(LocalDate.of(2026, 10, 1).atTime(MORNING), MedicationListOrder.nextIntakeAt(medication, NOW))
    }

    @Test
    fun `an ended course has no next intake`() {
        val medication = scheduled(
            times = listOf(MORNING),
            startDate = LocalDate.of(2026, 9, 1),
            endDate = LocalDate.of(2026, 9, 20),
        )

        assertNull(MedicationListOrder.nextIntakeAt(medication, NOW))
    }

    @Test
    fun `the last day of a course still has its later intake`() {
        val medication = scheduled(times = listOf(EVENING), endDate = TODAY)

        assertEquals(TODAY.atTime(EVENING), MedicationListOrder.nextIntakeAt(medication, NOW))
    }

    @Test
    fun `a course ending today with only passed times has no next intake`() {
        val medication = scheduled(times = listOf(MORNING), endDate = TODAY)

        assertNull(MedicationListOrder.nextIntakeAt(medication, NOW))
    }

    @Test
    fun `an open ended course rolls over to tomorrow`() {
        val medication = scheduled(times = listOf(MORNING), endDate = null)

        assertEquals(TOMORROW.atTime(MORNING), MedicationListOrder.nextIntakeAt(medication, NOW))
    }

    @Test
    fun `an on demand medication has no next intake`() {
        val medication = scheduled(times = emptyList()).copy(
            scheduleMode = ScheduleMode.PRN,
            maxPerDay = 3,
        )

        assertNull(MedicationListOrder.nextIntakeAt(medication, NOW))
    }

    @Test
    fun `an archived medication has no next intake`() {
        val medication = scheduled().copy(
            archivedAt = Instant.parse("2026-09-01T00:00:00Z"),
            archiveReason = ArchiveReason.MANUAL,
        )

        assertNull(MedicationListOrder.nextIntakeAt(medication, NOW))
    }

    @Test
    fun `a schedule without times has no next intake`() {
        assertNull(MedicationListOrder.nextIntakeAt(scheduled(times = emptyList()), NOW))
    }

    // --- Порядок ------------------------------------------------------------------------

    @Test
    fun `scheduled medications grow by the nearest intake`() {
        val later = scheduled(id = "later", name = "med-c", times = listOf(MORNING))
        val nearer = scheduled(id = "nearer", name = "med-a", times = listOf(EVENING))
        val evening = scheduled(id = "evening", name = "med-b", times = listOf(MORNING, EVENING))

        val ordered = MedicationListOrder.sorted(listOf(later, nearer, evening), NOW)

        assertEquals(listOf("nearer", "evening", "later"), ordered.map { it.id })
    }

    @Test
    fun `an equal intake breaks the tie by name`() {
        val beta = scheduled(id = "beta", name = "beta", times = listOf(EVENING))
        val alpha = scheduled(id = "alpha", name = "alpha", times = listOf(EVENING))

        val ordered = MedicationListOrder.sorted(listOf(beta, alpha), NOW)

        assertEquals(listOf("alpha", "beta"), ordered.map { it.id })
    }

    @Test
    fun `on demand medications fall below scheduled ones`() {
        val scheduledMedication = scheduled(id = "scheduled", name = "med-a", times = listOf(EVENING))
        val onDemand = scheduled(id = "prn", name = "med-b", times = emptyList()).copy(
            scheduleMode = ScheduleMode.PRN,
            maxPerDay = 3,
        )

        val ordered = MedicationListOrder.sorted(listOf(onDemand, scheduledMedication), NOW)

        assertEquals(listOf("scheduled", "prn"), ordered.map { it.id })
    }

    @Test
    fun `a course without a next intake falls to the on demand group`() {
        val ended = scheduled(
            id = "ended",
            name = "med-a",
            times = listOf(MORNING),
            endDate = LocalDate.of(2026, 9, 20),
        )
        val stillGoing = scheduled(id = "going", name = "med-b", times = listOf(EVENING))
        val onDemand = scheduled(id = "prn", name = "med-c", times = emptyList()).copy(
            scheduleMode = ScheduleMode.PRN,
            maxPerDay = 3,
        )

        val ordered = MedicationListOrder.sorted(listOf(ended, onDemand, stillGoing), NOW)

        assertEquals(listOf("going", "ended", "prn"), ordered.map { it.id })
    }

    @Test
    fun `the lower group is ordered by name`() {
        val zeta = scheduled(id = "zeta", name = "zeta", times = emptyList()).copy(
            scheduleMode = ScheduleMode.PRN,
            maxPerDay = 3,
        )
        val alpha = scheduled(id = "alpha", name = "alpha", times = emptyList()).copy(
            scheduleMode = ScheduleMode.PRN,
            maxPerDay = 3,
        )

        val ordered = MedicationListOrder.sorted(listOf(zeta, alpha), NOW)

        assertEquals(listOf("alpha", "zeta"), ordered.map { it.id })
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.of(2026, 9, 25)
        val TOMORROW: LocalDate = LocalDate.of(2026, 9, 26)
        val NOW: LocalDateTime = TODAY.atTime(14, 0)

        val MORNING: LocalTime = LocalTime.of(8, 0)
        val AFTERNOON: LocalTime = LocalTime.of(15, 0)
        val EVENING: LocalTime = LocalTime.of(20, 0)

        @Suppress("LongParameterList")
        fun scheduled(
            id: String = "med",
            name: String = "med-a",
            times: List<LocalTime> = listOf(MORNING),
            startDate: LocalDate = LocalDate.of(2026, 3, 12),
            endDate: LocalDate? = null,
        ) = Medication(
            id = id,
            profileId = Drafts.PROFILE_ID,
            name = name,
            form = MedicationForm.TABLET,
            shape = null,
            doseAmount = BigDecimal.ONE,
            doseUnit = DoseUnit.TABLET.code,
            strength = null,
            strengthUnit = null,
            foodRelation = FoodRelation.ANY,
            scheduleMode = ScheduleMode.SCHEDULED,
            maxPerDay = null,
            times = times,
            startDate = startDate,
            endDate = endDate,
            stockLeft = null,
            stockThresholdDays = null,
            archivedAt = null,
            archiveReason = null,
        )
    }
}
