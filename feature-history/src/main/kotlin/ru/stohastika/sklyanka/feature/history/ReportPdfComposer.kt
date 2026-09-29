package ru.stohastika.sklyanka.feature.history

import android.content.Context
import ru.stohastika.sklyanka.domain.history.DoctorReport
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Сборка состава PDF из готового отчёта (`TDD.md §5.3`).
 *
 * Композер отвечает за порядок разделов и ничего не считает: все числа, даты и формулировки
 * строк приходят из [DoctorReport] и [ReportPdfText], то есть из того же расчёта, что экран
 * истории. Второй расчёт «для документа» однажды разошёлся бы с экраном.
 *
 * `Context` здесь — ресурсы, а не платформа: состав собирается и проверяется под
 * Robolectric, и только [PdfPagePainter] касается `PdfDocument`.
 */
internal class ReportPdfComposer(private val context: Context) {

    private val text = ReportPdfText(context)

    fun compose(report: DoctorReport, zone: ZoneId): ReportPdfContent {
        val locale = context.resources.configuration.locales[0]
        // Условие экранного `isEmptyMonth` (`AdherencePeriod.isEmpty`): при пустом периоде
        // сводка и блок лекарств не печатаются вместе с заголовками, вместо всей сводки —
        // одна фраза (решение дизайна, ревизия 33-B SKL-33).
        val empty = report.period.isEmpty
        return ReportPdfContent(
            fileName = context.getString(
                R.string.report_file_name,
                report.period.from.format(text.pattern(R.string.report_file_pattern, locale)),
            ),
            mediaType = context.getString(R.string.report_media_type),
            header = header(report, zone, locale),
            summaryTitle = if (empty) null else section(R.string.report_document_summary_title),
            summary = if (empty) emptyList() else summary(report),
            medicationsTitle = if (empty) null else section(R.string.report_document_medications_title),
            medicationBlocks = if (empty) emptyList() else report.medications.map { row -> text.block(row, locale) },
            emptyNote = if (empty) {
                ReportPdfLine(context.getString(R.string.report_document_empty), ReportPdfRole.BODY_MUTED)
            } else {
                null
            },
            disclaimer = context.getString(R.string.report_document_disclaimer),
            brandPage = { number -> context.getString(R.string.report_document_page, number) },
        )
    }

    /**
     * Короткий состав для шторки: профиль, период и число принятых приёмов.
     *
     * Тем же композером, что и документ: текстовой сводки в шторке больше нет (решение
     * дизайна), но человек обязан видеть, что именно отдаёт, до нажатия «Передать».
     * Пустой период называет себя той же фразой, что стоит в документе, — человек
     * перед отправкой видит то же, что получит врач (решение дизайна, ревизия 33-B).
     */
    fun composition(report: DoctorReport): String {
        val locale = context.resources.configuration.locales[0]
        return listOf(
            context.getString(R.string.report_document_composition_profile, report.profileName),
            text.period(report.period.from, report.period.to, locale),
            if (report.period.isEmpty) {
                context.getString(R.string.report_document_empty)
            } else {
                context.getString(R.string.report_document_taken_label) + " " + text.takenValue(report)
            },
        ).joinToString("\n")
    }

    private fun header(report: DoctorReport, zone: ZoneId, locale: Locale): List<ReportPdfLine> = listOf(
        ReportPdfLine(context.getString(R.string.report_document_title), ReportPdfRole.TITLE),
        ReportPdfLine(
            context.getString(R.string.report_document_profile, report.profileName),
            ReportPdfRole.SUBTITLE,
        ),
        ReportPdfLine(text.period(report.period.from, report.period.to, locale), ReportPdfRole.META),
        ReportPdfLine(
            context.getString(R.string.report_document_generated, moment(report.generatedAt, zone, locale)),
            ReportPdfRole.META,
        ),
    )

    /** Сводка: те же числа, что на экране. */
    private fun summary(report: DoctorReport): List<ReportPdfSummaryLine> = listOf(
        ReportPdfSummaryLine(context.getString(R.string.report_document_taken_label), text.takenValue(report)),
        ReportPdfSummaryLine(context.getString(R.string.report_document_on_time_label), text.onTimeValue(report)),
        ReportPdfSummaryLine(
            context.getString(R.string.report_document_days_without_misses_label),
            report.period.daysWithoutMisses.toString(),
        ),
        ReportPdfSummaryLine(
            context.getString(R.string.report_document_misses_label),
            report.period.missesInPeriod.toString(),
        ),
    )

    private fun moment(value: Instant, zone: ZoneId, locale: Locale): String =
        value.atZone(zone).format(text.pattern(R.string.history_datetime_pattern, locale))

    private fun section(string: Int): ReportPdfLine = ReportPdfLine(context.getString(string), ReportPdfRole.SECTION)
}
