package ru.stohastika.sklyanka.feature.history

import android.content.Context
import ru.stohastika.sklyanka.domain.history.DoctorReport
import ru.stohastika.sklyanka.domain.history.ExtendedReportSections
import ru.stohastika.sklyanka.domain.history.MissedPatternBlock
import ru.stohastika.sklyanka.domain.history.MonthDynamics
import ru.stohastika.sklyanka.domain.history.PeriodComparison
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
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
internal class ReportPdfComposer(applicationContext: Context) {

    /**
     * Контекст с закреплённым языком интерфейса (НФТ-43а, `ADR-0008`).
     *
     * Композер собирает слова и шторки, и документа, и закрепляет язык сам, а не полагается
     * на вызывающего. Рендер отчёта — синглтон с контекстом приложения, а тот, в отличие от
     * контекста активностей, закрепление языка не проходит: на телефоне с английской
     * системой период печатался «1 — 30 September 2026», и локаль телефона уходила в PDF
     * (находка 44-A SKL-44). Здесь закрепление стоит на единственной точке, где отчёт
     * получает слова, — значит, собрать его на языке телефона не может ни один вызов.
     */
    private val context: Context = applicationContext.withInterfaceLocale()

    private val text = ReportPdfText(context)

    fun compose(
        report: DoctorReport,
        zone: ZoneId,
        extended: ExtendedReportSections = ExtendedReportSections.NONE,
    ): ReportPdfContent {
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
            // Пустой период не даёт ни одного «Про»-раздела: документ остаётся базовым
            // и несёт одну фразу (§5.15). Раздела без данных не бывает и в остальных случаях:
            // `null` в составе — это «печатать нечего» (Р-33).
            extendedSections = if (empty) emptyList() else extendedSections(extended, report, locale),
            disclaimer = context.getString(R.string.report_document_disclaimer),
            brandPage = { number -> context.getString(R.string.report_document_page, number) },
        )
    }

    /**
     * Короткий состав для шторки: профиль, период, число принятых приёмов и формат.
     *
     * Тем же композером, что и документ: текстовой сводки в шторке больше нет (решение
     * дизайна), но человек обязан видеть, что именно отдаёт, до нажатия «Передать».
     * Пустой период называет себя той же фразой, что стоит в документе, — человек
     * перед отправкой видит то же, что получит врач (решение дизайна, ревизия 33-B).
     */
    fun composition(report: DoctorReport): ReportComposition {
        val locale = context.resources.configuration.locales[0]
        return ReportComposition(
            profile = report.profileName,
            period = text.periodRange(report.period.from, report.period.to, locale),
            from = text.fullDate(report.period.from, locale),
            to = text.fullDate(report.period.to, locale),
            // Пустой период называет себя той же фразой, что стоит в документе: человек
            // перед отправкой видит то же, что получит врач (решение дизайна, ревизия 33-B).
            taken = if (report.period.isEmpty) null else text.takenValue(report),
            format = context.getString(R.string.history_report_format_value),
        )
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

    /**
     * Сводка: те же числа, что на экране.
     *
     * Строка «Принято вовремя» печатается, только когда ей есть что сообщить (Р-33):
     * пустого значения в документе врачу не бывает — ни числа, ни прочерка.
     */
    private fun summary(report: DoctorReport): List<ReportPdfSummaryLine> = buildList {
        add(ReportPdfSummaryLine(context.getString(R.string.report_document_taken_label), text.takenValue(report)))
        text.onTimeValue(report)?.let { share ->
            add(ReportPdfSummaryLine(context.getString(R.string.report_document_on_time_label), share))
        }
        add(
            ReportPdfSummaryLine(
                context.getString(R.string.report_document_days_without_misses_label),
                report.period.daysWithoutMisses.toString(),
            ),
        )
        add(
            ReportPdfSummaryLine(
                context.getString(R.string.report_document_misses_label),
                report.period.missesInPeriod.toString(),
            ),
        )
    }

    private fun moment(value: Instant, zone: ZoneId, locale: Locale): String =
        value.atZone(zone).format(text.pattern(R.string.history_datetime_pattern, locale))

    private fun section(string: Int): ReportPdfLine = ReportPdfLine(context.getString(string), ReportPdfRole.SECTION)

    /**
     * Разделы «Про» по порядку §5.15: сравнение периодов → динамика по месяцам → паттерн.
     *
     * Порядок — часть решения, а не следствие порядка полей: врач читает документ сверху вниз,
     * и разделы стоят так же, как названы в норме и в пояснении состава шторки.
     */
    private fun extendedSections(
        extended: ExtendedReportSections,
        report: DoctorReport,
        locale: Locale,
    ): List<ReportPdfSection> = listOfNotNull(
        extended.comparison?.let { comparisonSection(it, locale) },
        extended.dynamics?.let { dynamicsSection(it, report, locale) },
        extended.pattern?.let { patternSection(it, report, locale) },
    )

    /**
     * Сравнение периодов: те же четыре подписи, что в сводке, и число предыдущего периода
     * в правом поле.
     *
     * Строка показателя печатается, только когда её есть чем заполнить **в обоих** периодах:
     * у «принято вовремя» числа может не быть вовсе (мерить нечего — Р-33), и тогда строки
     * нет, а прочерка на её месте нет. Колонку правых чисел называет одна строка меты —
     * заголовка столбца таблица не заводит.
     */
    private fun comparisonSection(comparison: PeriodComparison, locale: Locale): ReportPdfSection {
        val current = comparison.current
        val previous = comparison.previous
        val rows = buildList {
            add(
                ReportPdfSectionRow(
                    label = context.getString(R.string.report_document_taken_label),
                    value = text.takenValue(current.taken, current.planned),
                    right = text.takenValue(previous.taken, previous.planned),
                ),
            )
            val currentOnTime = text.onTimeValue(current.onTimeShare)
            val previousOnTime = text.onTimeValue(previous.onTimeShare)
            if (currentOnTime != null && previousOnTime != null) {
                add(
                    ReportPdfSectionRow(
                        label = context.getString(R.string.report_document_on_time_label),
                        value = currentOnTime,
                        right = previousOnTime,
                    ),
                )
            }
            add(
                ReportPdfSectionRow(
                    label = context.getString(R.string.report_document_days_without_misses_label),
                    value = current.daysWithoutMisses.toString(),
                    right = previous.daysWithoutMisses.toString(),
                ),
            )
            add(
                ReportPdfSectionRow(
                    label = context.getString(R.string.report_document_misses_label),
                    value = current.misses.toString(),
                    right = previous.misses.toString(),
                ),
            )
        }
        return ReportPdfSection(
            title = context.getString(R.string.report_document_compare_title),
            // Первой строкой — период отчёта: правый столбец называет предыдущий период,
            // и без первой строки левую колонку чисел (текущий период) не называло бы ничего.
            metas = listOf(
                text.period(current.from, current.to, locale),
                text.previousPeriod(previous.from, previous.to, locale),
            ),
            rows = rows,
        )
    }

    /**
     * Динамика по месяцам: строка на месяц периода в хронологическом порядке.
     *
     * Месяцев без наступивших приёмов в составе нет вовсе — их не присылает домен (Р-33),
     * и прочерка в документе не бывает.
     */
    private fun dynamicsSection(dynamics: MonthDynamics, report: DoctorReport, locale: Locale): ReportPdfSection =
        ReportPdfSection(
            title = context.getString(R.string.report_document_dynamics_title),
            metas = listOf(text.period(report.period.from, report.period.to, locale)),
            rows = dynamics.months.map { month ->
                ReportPdfSectionRow(
                    label = text.monthLabel(month.month, locale),
                    value = text.monthPercent(month),
                    right = text.dynamicsCount(month.taken, month.planned),
                )
            },
        )

    /**
     * Паттерн пропусков: предложение правила, строка чисел и даты этого времени.
     *
     * Разрез здесь по времени, а не по лекарству, — ради него раздел и печатается; язык дат
     * и их природы тот же, что в базовом документе.
     */
    private fun patternSection(pattern: MissedPatternBlock, report: DoctorReport, locale: Locale): ReportPdfSection =
        ReportPdfSection(
            title = context.getString(R.string.report_document_pattern_title),
            metas = listOf(text.period(report.period.from, report.period.to, locale)),
            lines = listOf(
                ReportPdfLine(text.insightSentence(pattern.time, locale), ReportPdfRole.BODY),
                ReportPdfLine(
                    text.patternNumbers(pattern.misses, report.period.missesInPeriod),
                    ReportPdfRole.BODY_MUTED,
                ),
                ReportPdfLine(
                    text.missesLine(
                        dates = pattern.missedDates,
                        skippedByPerson = pattern.skippedByPerson,
                        withoutMark = pattern.missedWithoutMark,
                        locale = locale,
                    ),
                    ReportPdfRole.BODY_MUTED,
                ),
            ),
        )
}
