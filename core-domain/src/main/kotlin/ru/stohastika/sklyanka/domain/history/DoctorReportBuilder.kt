package ru.stohastika.sklyanka.domain.history

import ru.stohastika.sklyanka.domain.medication.CourseOutcome
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import java.time.Instant
import java.time.ZoneId

/**
 * Сборка состава отчёта врачу из тех же данных, по которым нарисован экран
 * (`TDD.md §5.6`).
 *
 * Отчёт обязан совпадать с экраном до числа: врач и человек смотрят на одну и ту же
 * картину, и второй расчёт «для отчёта» однажды разойдётся с первым. Поэтому сводка
 * приходит готовой ([AdherencePeriod]), а здесь добавляется только то, чего на экране
 * нет: состав курса, разделение пропусков по природе и перечень дат.
 */
object DoctorReportBuilder {

    fun build(
        period: AdherencePeriod,
        slice: HistorySlice,
        profileName: String,
        generatedAt: Instant,
        zone: ZoneId,
    ): DoctorReport {
        val medications = slice.medications.associateBy { it.id }
        val rows = period.byMedication.mapNotNull { adherence ->
            val medication = medications[adherence.medicationId] ?: return@mapNotNull null
            row(medication, adherence, ofMedication(slice, medication, zone))
        }
        return DoctorReport(
            period = period,
            profileName = profileName,
            generatedAt = generatedAt,
            medications = rows,
        )
    }

    /**
     * Приёмы лекарства, вошедшие в картину периода.
     *
     * Границы те же, что у расчёта: приёмы, назначенные после архивации курса, в отчёт
     * не попадают (ОВ-44). Иначе отчёт показал бы пропуски у курса, который человек
     * прекратил, — то есть ровно то, от чего отказались в расчёте.
     */
    private fun ofMedication(slice: HistorySlice, medication: Medication, zone: ZoneId): List<Intake> {
        val archivedOn = medication.archivedAt?.let { CourseOutcome.archivedDate(medication, zone) }
        return slice.intakes.filter { intake ->
            intake.medicationId == medication.id &&
                (archivedOn == null || !intake.scheduledAt.toLocalDate().isAfter(archivedOn))
        }
    }

    private fun row(
        medication: Medication,
        adherence: MedicationAdherence,
        intakes: List<Intake>,
    ): ReportMedicationRow = ReportMedicationRow(
        medicationId = medication.id,
        name = medication.name,
        form = medication.form,
        doseAmount = medication.doseAmount,
        doseUnit = medication.doseUnit,
        strength = medication.strength,
        strengthUnit = medication.strengthUnit,
        foodRelation = medication.foodRelation,
        times = medication.times.sorted(),
        courseStart = medication.startDate,
        courseEnd = medication.endDate,
        taken = adherence.taken,
        planned = adherence.planned,
        skippedByPerson = intakes.count { it.status == IntakeStatus.SKIPPED },
        missedWithoutMark = intakes.count { it.status == IntakeStatus.MISSED },
        archived = medication.isArchived,
        missedDates = intakes.filter { it.isMiss }
            .map { it.scheduledAt.toLocalDate() }
            .distinct()
            .sorted(),
    )
}
