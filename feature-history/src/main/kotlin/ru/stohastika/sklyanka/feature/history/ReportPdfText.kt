package ru.stohastika.sklyanka.feature.history

import android.content.Context
import ru.stohastika.sklyanka.domain.history.DoctorReport
import ru.stohastika.sklyanka.domain.history.OnTimeShare
import ru.stohastika.sklyanka.domain.history.ReportMedicationRow
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.doseUnit
import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Тексты документа отчёта: даты, дозы, расписание, пропуски (`TDD.md §5.3`).
 *
 * Отдельно от [ReportPdfComposer], потому что здесь живут формулировки строки лекарства —
 * самая склоняемая часть документа, — а композер отвечает за порядок разделов. Обе части
 * читают одни ресурсы (НФТ-42), и обе не считают ни одного числа: всё приходит из
 * [DoctorReport], то есть из расчёта экрана истории.
 */
internal class ReportPdfText(private val context: Context) {

    /** Блок одного лекарства по решению дизайна: строка с долей и строка курса и пропусков. */
    fun block(row: ReportMedicationRow, locale: Locale): ReportPdfBlock {
        val first = buildList {
            add(row.name)
            add(dose(row))
            strength(row)?.let(::add)
            add(schedule(row, locale))
        }.joinToString(separator())
        val second = buildList {
            add(course(row, locale))
            add(misses(row, locale))
            if (row.archived) add(context.getString(R.string.report_document_archived))
        }.joinToString(separator())
        return ReportPdfBlock(
            lines = listOf(
                ReportPdfLine(first, ReportPdfRole.BODY),
                ReportPdfLine(second, ReportPdfRole.BODY_MUTED),
            ),
            right = context.getString(
                R.string.report_document_medication_share,
                row.taken,
                row.planned,
                percent(row.takenPercent),
            ),
        )
    }

    /** «Принято: 86 из 100 приёмов (86 %)» — сумма по строкам из домена (`TDD.md §5.2`). */
    fun takenValue(report: DoctorReport): String = context.resources.getQuantityString(
        R.plurals.report_document_intakes_of_total,
        report.plannedTotal,
        report.takenTotal,
        report.plannedTotal,
        percent(report.takenPercent),
    )

    /**
     * «Принято вовремя: 74 %» — или прочерк, пока порог не согласован (ОВ-4).
     *
     * Выдуманное число в отчёте врачу недопустимо ровно так же, как на плитке экрана.
     */
    fun onTimeValue(report: DoctorReport): String = when (val share = report.period.onTimeShare) {
        is OnTimeShare.Value -> percent(share.percent)
        OnTimeShare.Undefined -> context.getString(R.string.history_value_dash)
    }

    /**
     * Период: «1 — 30 сентября 2026» внутри месяца и «1 сентября 2026 — 3 октября 2026»
     * на его границе. Год печатается у конца периода; у начала он опускается только
     * тогда, когда месяц совпадает с концом.
     */
    fun period(from: LocalDate, to: LocalDate, locale: Locale): String {
        val fromText = if (YearMonth.from(from) == YearMonth.from(to)) {
            from.dayOfMonth.toString()
        } else {
            fullDate(from, locale)
        }
        return context.getString(R.string.report_document_period, fromText, fullDate(to, locale))
    }

    fun pattern(string: Int, locale: Locale): DateTimeFormatter =
        DateTimeFormatter.ofPattern(context.getString(string), locale)

    fun fullDate(date: LocalDate, locale: Locale): String =
        date.format(pattern(R.string.history_date_pattern_full, locale))

    /**
     * «Пропуски: 6, 9, 14, 21 сентября; из них 1 отмечен человеком, 3 — без отметки».
     *
     * Даты группируются по месяцу: месяц печатается один раз, а не у каждой даты.
     * Природа пропуска сохраняется — врач вправе видеть разницу между отметкой человека
     * и автоматическим переводом (ПР-1).
     */
    private fun misses(row: ReportMedicationRow, locale: Locale): String {
        if (row.misses == 0) return context.getString(R.string.report_document_no_misses)
        val dates = context.getString(R.string.report_document_missed_dates, missedDates(row.missedDates, locale))
        val natures = buildList {
            if (row.skippedByPerson > 0) {
                add(
                    context.resources.getQuantityString(
                        R.plurals.report_document_missed_by_person,
                        row.skippedByPerson,
                        row.skippedByPerson,
                    ),
                )
            }
            if (row.missedWithoutMark > 0) {
                add(context.getString(R.string.report_document_missed_without_mark, row.missedWithoutMark))
            }
        }
        return if (natures.size == 1) {
            context.getString(R.string.report_document_missed_single, dates, natures.single())
        } else {
            context.getString(R.string.report_document_missed_both, dates, natures[0], natures[1])
        }
    }

    /** «6, 9, 14, 21 сентября»: день без месяца в перечислении, месяц — один раз на группу. */
    private fun missedDates(dates: List<LocalDate>, locale: Locale): String = dates
        .groupBy { YearMonth.from(it) }
        .entries
        .joinToString(context.getString(R.string.report_document_list_separator)) { (month, days) ->
            val monthName = month.atDay(1).format(pattern(R.string.history_month_name_pattern, locale))
            days.joinToString(context.getString(R.string.report_document_list_separator)) { it.dayOfMonth.toString() } +
                " " + monthName
        }

    private fun course(row: ReportMedicationRow, locale: Locale): String {
        val end = row.courseEnd
        return if (end == null) {
            context.getString(R.string.report_document_course_open, fullDate(row.courseStart, locale))
        } else {
            context.getString(
                R.string.report_document_course,
                fullDate(row.courseStart, locale),
                fullDate(end, locale),
            )
        }
    }

    private fun schedule(row: ReportMedicationRow, locale: Locale): String = if (row.times.isEmpty()) {
        context.getString(R.string.report_document_as_needed)
    } else {
        row.times.joinToString(context.getString(R.string.report_document_list_separator)) { time ->
            time.format(pattern(R.string.history_time_pattern, locale))
        }
    }

    /** «1 таблетка», «2 таблетки»: склонение по количеству, формулировки карточки лекарства. */
    private fun dose(row: ReportMedicationRow): String {
        val unit = DoseUnit.ofCode(row.doseUnit) ?: row.form.doseUnit()
        val template = when (unit) {
            DoseUnit.TABLET -> R.plurals.report_document_dose_tablets
            DoseUnit.CAPSULE -> R.plurals.report_document_dose_capsules
            DoseUnit.MILLILITRE -> R.plurals.report_document_dose_millilitres
            DoseUnit.DOSE -> R.plurals.report_document_dose_doses
        }
        return context.resources.getQuantityString(template, row.doseAmount.toInt(), amount(row.doseAmount))
    }

    /**
     * «50 мкг». Дозировка печатается только целиком: число без единицы врачу не говорит
     * ничего, а по модели без единицы и не хранится (ИНВ-5). Незнакомый код показывается
     * как есть — отказать врачу в строке хуже, чем показать код (МГ-6).
     */
    private fun strength(row: ReportMedicationRow): String? {
        val strength = row.strength
        val unit = row.strengthUnit
        return if (strength != null && unit != null) amount(strength) + " " + unitText(unit) else null
    }

    private fun unitText(code: String): String =
        if (code == MILLIGRAM_CODE) context.getString(R.string.strength_unit_milligram) else code

    /** «0,5» и «2»: лишние нули дозы в отчёте только мешают. */
    private fun amount(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

    private fun percent(value: Int): String = context.getString(R.string.history_percent, value)

    private fun separator(): String = context.getString(R.string.report_document_part_separator)

    private companion object {
        const val MILLIGRAM_CODE = "mg"
    }
}
