package ru.stohastika.sklyanka.domain.history

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import java.time.LocalDate

/**
 * Состав отчёта врачу: период, профиль, разбивка с признаком архива и даты пропусков.
 *
 * Отчёт обязан совпадать с экраном до числа, поэтому сводка приходит готовой, а здесь
 * проверяется только то, чего на экране нет.
 */
class DoctorReportBuilderTest {

    @Test
    fun `the report names the period and the profile`() {
        val report = build()

        assertEquals(Months.WINDOW.from, report.period.from)
        assertEquals(Months.WINDOW.to, report.period.to)
        assertEquals(PROFILE_NAME, report.profileName)
        assertEquals(Months.instant(Months.TODAY.atTime(10, 0)), report.generatedAt)
    }

    @Test
    fun `the report carries the same numbers as the screen`() {
        val report = build()

        val row = report.medications.single { it.medicationId == Months.MEDICATION_ID }
        val onScreen = report.period.byMedication.single { it.medicationId == Months.MEDICATION_ID }
        assertEquals(onScreen.taken, row.taken)
        assertEquals(onScreen.planned, row.planned)
        assertEquals(onScreen.takenPercent, row.takenPercent)
    }

    /**
     * В отчёте пропуск человека и автоматический пропуск различаются.
     *
     * В календаре они одинаковы (приём не состоялся), но врачу разница говорит разное:
     * первое — о поведении человека, второе может говорить о выключенном телефоне
     * (`TDD.md §5.3`).
     */
    @Test
    fun `the report keeps the marks of the person apart from the automatic ones`() {
        val report = build()

        val row = report.medications.single { it.medicationId == Months.MEDICATION_ID }
        assertEquals(1, row.skippedByPerson)
        assertEquals(2, row.missedWithoutMark)
        assertEquals(3, row.misses)
    }

    @Test
    fun `the report lists the dates of the misses`() {
        val report = build()

        val row = report.medications.single { it.medicationId == Months.MEDICATION_ID }
        assertEquals(
            listOf(LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 5)),
            row.missedDates,
        )
    }

    /** ПР-11: архивированный курс входит в отчёт и помечен как архивный. */
    @Test
    fun `an archived course is in the report and is marked as archived`() {
        val report = build()

        val archived = report.medications.single { it.medicationId == Months.ARCHIVED_MEDICATION_ID }
        assertTrue(archived.archived)
        assertFalse(report.medications.single { it.medicationId == Months.MEDICATION_ID }.archived)
    }

    /** Число записей уходит в журнал вместо содержимого (НФТ-19). */
    @Test
    fun `the report knows how many records it was built from`() {
        val report = build()

        assertEquals(report.medications.sumOf { it.planned }, report.intakeCount)
    }

    private fun build(): DoctorReport {
        val slice = Months.slice(
            intakes = intakes(),
            medications = listOf(
                Months.medication(),
                Months.medication(
                    id = Months.ARCHIVED_MEDICATION_ID,
                    name = "med-c",
                    archivedAt = Months.instant(LocalDate.of(2026, 9, 10).atTime(18, 0)),
                ),
            ),
        )
        val period = AdherenceCalculator().of(slice, Months.WINDOW, Months.TODAY, Months.ZONE)
        return DoctorReportBuilder.build(
            period = period,
            slice = slice,
            profileName = PROFILE_NAME,
            generatedAt = Months.instant(Months.TODAY.atTime(10, 0)),
            zone = Months.ZONE,
        )
    }

    private fun intakes() = Months.daily(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2)) +
        Months.intake(LocalDate.of(2026, 9, 3).atTime(Months.MORNING), IntakeStatus.SKIPPED) +
        Months.daily(LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 5), status = IntakeStatus.MISSED) +
        Months.daily(
            from = LocalDate.of(2026, 9, 1),
            to = LocalDate.of(2026, 9, 10),
            medicationId = Months.ARCHIVED_MEDICATION_ID,
        )

    private companion object {
        const val PROFILE_NAME = "profile-a"
    }
}
