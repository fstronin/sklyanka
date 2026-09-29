package ru.stohastika.sklyanka.feature.history

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.stohastika.sklyanka.domain.history.DoctorReport
import ru.stohastika.sklyanka.domain.history.OnTimeShare
import ru.stohastika.sklyanka.domain.history.ReportDocument
import ru.stohastika.sklyanka.domain.history.ReportMedicationRow
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.doseUnit
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Отрисовка отчёта врачу.
 *
 * Интерфейс, а не класс, потому что формат артефакта не подтверждён (ОВ-15): предложение
 * исполнителя — PDF плюс показ с экрана, и подстановка подтверждённого формата обязана
 * быть заменой реализации, а не правкой экрана. Побочная польза — состояние экрана
 * проверяется на JVM без ресурсов Android.
 */
interface DoctorReportRenderer {

    fun render(report: DoctorReport, zone: ZoneId): ReportDocument
}

/**
 * Документ отчёта врачу текстом (`TDD.md §5.6`).
 *
 * Живёт в экранном модуле, а не в домене, по двум причинам. Первая: формулировки лежат
 * в ресурсах, а домен ресурсов не видит и русского текста не содержит (НФТ-42). Вторая:
 * формат артефакта не подтверждён (ОВ-15), и отделённая отрисовка позволяет подставить
 * подтверждённый формат, не трогая расчёт.
 *
 * **Почему пока текст, а не PDF.** Предложение исполнителя — PDF плюс показ с экрана
 * (`task.md`), и оно требует подтверждения заказчиком: PDF тянет вёрстку документа и
 * шрифты с кириллицей в сборку, а проверить его вид без устройства нечем. Пока формат
 * не подтверждён, документ собирается текстом: состав отчёта от этого не зависит,
 * и переход на PDF — замена этого класса, а не расчёта.
 *
 * **Выводов и рекомендаций в документе нет** — по той же причине, что и в строке-выводе:
 * приложение не медицинское изделие (`CLAUDE.md`). Документ содержит числа, даты и факты
 * и ни одного глагола побуждения; ограничение проверяется тестом, а не памятью ревьюера.
 */
@Singleton
class TextDoctorReportRenderer @Inject constructor(@ApplicationContext private val context: Context) :
    DoctorReportRenderer {

    override fun render(report: DoctorReport, zone: ZoneId): ReportDocument = ReportDocument(
        fileName = fileName(report.period.from),
        mediaType = context.getString(R.string.report_media_type),
        content = content(report, zone),
    )

    private fun content(report: DoctorReport, zone: ZoneId): String = buildString {
        appendLine(context.getString(R.string.report_document_title))
        appendLine(context.getString(R.string.report_document_profile, report.profileName))
        appendLine(
            context.getString(
                R.string.report_document_period,
                date(report.period.from),
                date(report.period.to),
            ),
        )
        appendLine(context.getString(R.string.report_document_generated, moment(report.generatedAt, zone)))
        appendLine()
        appendSummary(report)
        if (report.medications.isEmpty()) {
            appendLine()
            appendLine(context.getString(R.string.report_document_empty))
        } else {
            appendLine()
            appendLine(context.getString(R.string.report_document_medications))
            report.medications.forEach { row ->
                appendLine()
                appendMedication(row)
            }
        }
    }

    private fun StringBuilder.appendSummary(report: DoctorReport) {
        appendLine(context.getString(R.string.report_document_summary))
        // Прочерк вместо доли принятого вовремя: порог не согласован (ОВ-4), и выдуманное
        // число в отчёте врачу — это неверные данные с подписью приложения.
        val onTime = when (val share = report.period.onTimeShare) {
            is OnTimeShare.Value -> context.getString(R.string.history_percent, share.percent)
            OnTimeShare.Undefined -> context.getString(R.string.history_value_dash)
        }
        appendLine(context.getString(R.string.report_document_on_time, onTime))
        appendLine(
            context.getString(R.string.report_document_days_without_misses, report.period.daysWithoutMisses),
        )
        appendLine(context.getString(R.string.report_document_misses, report.period.missesInPeriod))
    }

    private fun StringBuilder.appendMedication(row: ReportMedicationRow) {
        appendLine(
            context.getString(
                R.string.report_document_medication,
                row.name,
                row.taken,
                row.planned,
                context.getString(R.string.history_percent, row.takenPercent),
            ),
        )
        appendLine(dose(row))
        val strength = row.strength
        val strengthUnit = row.strengthUnit
        // Дозировка печатается только целиком: число без единицы врачу не говорит ничего
        // и по модели без единицы не хранится (ИНВ-5).
        if (strength != null && strengthUnit != null) {
            appendLine(
                context.getString(
                    R.string.report_document_strength,
                    amount(strength),
                    strengthUnit(strengthUnit),
                ),
            )
        }
        appendLine(schedule(row))
        appendLine(course(row))
        // Признак архива словом, а не пропуском строки: «курс в архиве» объясняет врачу,
        // почему приёмы кончились посреди месяца (ПР-11).
        if (row.archived) appendLine(context.getString(R.string.report_document_archived))
        appendLine(misses(row))
        if (row.missedDates.isNotEmpty()) {
            appendLine(
                context.getString(
                    R.string.report_document_missed_dates,
                    row.missedDates.joinToString(separator()) { date(it) },
                ),
            )
        }
    }

    private fun schedule(row: ReportMedicationRow): String = if (row.times.isEmpty()) {
        context.getString(R.string.report_document_as_needed)
    } else {
        context.getString(
            R.string.report_document_schedule,
            row.times.joinToString(separator()) { time -> time.format(pattern(R.string.history_time_pattern)) },
        )
    }

    private fun course(row: ReportMedicationRow): String {
        val end = row.courseEnd
        return if (end == null) {
            context.getString(R.string.report_document_course_open, date(row.courseStart))
        } else {
            context.getString(R.string.report_document_course, date(row.courseStart), date(end))
        }
    }

    /** Пропуски с разделением по природе: отметка человека и автоматический перевод (ПР-1). */
    private fun misses(row: ReportMedicationRow): String = if (row.misses == 0) {
        context.getString(R.string.report_document_no_misses)
    } else {
        context.resources.getQuantityString(
            R.plurals.report_document_medication_misses,
            row.misses,
            row.misses,
            row.skippedByPerson,
            row.missedWithoutMark,
        )
    }

    /**
     * «Доза: по 1 таблетке».
     *
     * Склонение — через `plurals`, а не склейкой числа с единицей (НФТ-44), и
     * формулировки те же, что в карточке лекарства: одна доза не должна называться
     * по-разному на экране и в отчёте врачу.
     */
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
     * Единица дозировки.
     *
     * Код хранится латиницей, а показывается по-русски — так же, как в карточке
     * лекарства. Незнакомый код показывается как есть: испортить отчёт врачу отказом
     * хуже, чем показать код (МГ-6).
     */
    private fun strengthUnit(code: String): String =
        if (code == MILLIGRAM_CODE) context.getString(R.string.strength_unit_milligram) else code

    /** «0,5» и «2»: лишние нули дозы в отчёте только мешают. */
    private fun amount(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

    private fun fileName(from: LocalDate): String =
        context.getString(R.string.report_file_name, from.format(pattern(R.string.report_file_pattern)))

    private fun date(value: LocalDate): String = value.format(pattern(R.string.history_date_pattern_full))

    private fun moment(value: Instant, zone: ZoneId): String =
        value.atZone(zone).format(pattern(R.string.history_datetime_pattern))

    private fun separator(): String = context.getString(R.string.report_document_separator)

    /**
     * Форматтер по шаблону из ресурсов и локали настроек приложения.
     *
     * Локаль берётся у ресурсов, а не у системы: документ обязан читаться так же, как
     * экран, с которого его сформировали (НФТ-43).
     */
    private fun pattern(id: Int): DateTimeFormatter =
        DateTimeFormatter.ofPattern(context.getString(id), context.resources.configuration.locales[0])

    private companion object {
        const val MILLIGRAM_CODE = "mg"
    }
}
