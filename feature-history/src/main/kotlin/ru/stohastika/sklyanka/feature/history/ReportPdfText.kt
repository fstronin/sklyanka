package ru.stohastika.sklyanka.feature.history

import android.content.Context
import ru.stohastika.sklyanka.domain.history.DoctorReport
import ru.stohastika.sklyanka.domain.history.MonthAdherence
import ru.stohastika.sklyanka.domain.history.OnTimeShare
import ru.stohastika.sklyanka.domain.history.ReportMedicationRow
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.StrengthUnit
import ru.stohastika.sklyanka.domain.medication.doseUnit
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.today.PartOfDay
import ru.stohastika.sklyanka.ui.text.pluralQuantityOf
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
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
@Suppress("TooManyFunctions")
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
    fun takenValue(report: DoctorReport): String = takenValue(report.takenTotal, report.plannedTotal)

    /**
     * То же число по паре «принято — запланировано».
     *
     * Отдельной формулировки у сравнения периодов не заводится: «принято» в сводке и «принято»
     * в сравнении — один показатель одного документа, и разойтись они не имеют права (§5.15).
     */
    fun takenValue(taken: Int, planned: Int): String = context.resources.getQuantityString(
        R.plurals.report_document_intakes_of_total,
        planned,
        taken,
        planned,
        percent(if (planned == 0) 0 else Math.round(taken.toDouble() / planned * PERCENT).toInt()),
    )

    /**
     * «Принято вовремя: 74 %» — или `null`, когда мерить нечего (Р-33).
     *
     * Выдуманное число в отчёте врачу недопустимо ровно так же, как на плитке экрана,
     * а прочерк в документе читался бы как измерение, которого не было: строке нечего
     * сообщить, и она не печатается — тем же правилом, что и плитка на экране.
     */
    fun onTimeValue(report: DoctorReport): String? = onTimeValue(report.period.onTimeShare)

    /** Та же доля, посчитанная для периода сравнения: строка сравнения берёт её этим же вызовом. */
    fun onTimeValue(share: OnTimeShare): String? = when (share) {
        is OnTimeShare.Value -> if (share.lessThanOnePercent) {
            context.getString(R.string.history_percent_less_than_one)
        } else {
            percent(share.percent)
        }
        OnTimeShare.Absent -> null
    }

    /**
     * Период с подписью для документа: «Период: 1 — 30 сентября 2026».
     *
     * Год печатается у конца периода; у начала он опускается только тогда, когда месяц
     * совпадает с концом.
     */
    fun period(from: LocalDate, to: LocalDate, locale: Locale): String = if (from == to) {
        context.getString(R.string.report_document_period_single, fullDate(from, locale))
    } else {
        val (start, end) = periodEnds(from, to, locale)
        context.getString(R.string.report_document_period, start, end)
    }

    /**
     * Период без подписи: «1 — 30 сентября 2026», «4 октября 2026» для одного дня.
     *
     * Подпись «Период» в шторке — отдельная строка карточки, и значение не должно
     * нести её второй раз (ОВ-125). Начало внутри месяца — ресурс языка (решение
     * 02.10.2026): в zh-CN голое «1» рядом с датой «2026年9月30日» не читается,
     * и там печатается «9月1日».
     */
    fun periodRange(from: LocalDate, to: LocalDate, locale: Locale): String = when {
        from == to -> fullDate(from, locale)
        else -> {
            val (start, end) = periodEnds(from, to, locale)
            context.getString(R.string.report_document_period_range, start, end)
        }
    }

    private fun periodEnds(from: LocalDate, to: LocalDate, locale: Locale): Pair<String, String> =
        if (YearMonth.from(from) == YearMonth.from(to)) {
            startOfMonth(from, locale) to fullDate(to, locale)
        } else {
            fullDate(from, locale) to fullDate(to, locale)
        }

    private fun startOfMonth(date: LocalDate, locale: Locale): String =
        date.format(pattern(R.string.report_document_period_start, locale))

    /**
     * «Предыдущий период: 2 — 31 августа 2026» — строка меты раздела сравнения (§5.15).
     *
     * Колонку правых чисел называет она одна: заголовка столбца таблица не заводит, потому
     * что других чисел в разделе нет. Для периода из одного дня — своя формулировка,
     * как у периода документа.
     */
    fun previousPeriod(from: LocalDate, to: LocalDate, locale: Locale): String = if (from == to) {
        context.getString(R.string.report_compare_previous_period_single, fullDate(from, locale))
    } else {
        val (start, end) = periodEnds(from, to, locale)
        context.getString(R.string.report_compare_previous_period, start, end)
    }

    /**
     * «Сентябрь 2026» — имя месяца динамики.
     *
     * Платформенный форматтер с локалью интерфейса по шаблону календаря (НФТ-43а): в zh-CN
     * месяц и год стоят в другом порядке, и склейка «месяц + год» в коде назвала бы дату
     * неверно на двух языках из шести.
     */
    fun monthLabel(month: YearMonth, locale: Locale): String =
        month.atDay(1).format(pattern(R.string.history_month_pattern, locale))

    /**
     * Предложение паттерна пропусков — теми же словами, что строка-вывод «Истории» (Р-85 п. 4).
     *
     * Второго словаря правило не заводит: слово то же, место второе.
     */
    fun insightSentence(time: LocalTime, locale: Locale): String = context.getString(
        when (PartOfDay.of(time)) {
            PartOfDay.MORNING -> R.string.history_insight_morning
            PartOfDay.DAY -> R.string.history_insight_day
            PartOfDay.EVENING -> R.string.history_insight_evening
            PartOfDay.NIGHT -> R.string.history_insight_night
        },
        time.format(pattern(R.string.history_time_pattern, locale)),
    )

    /** «Пропусков в это время: 4 из 6 за период» — строка чисел паттерна (§5.15). */
    fun patternNumbers(misses: Int, total: Int): String =
        context.getString(R.string.report_pattern_numbers, misses, total)

    /** «44 из 48» — правое поле строки месяца динамики. */
    fun dynamicsCount(taken: Int, planned: Int): String =
        context.getString(R.string.report_dynamics_count, taken, planned)

    /** Доля принятого месяца: «92 %», при доле меньше процента — словами (Р-27). */
    fun monthPercent(month: MonthAdherence): String = if (month.lessThanOnePercent) {
        context.getString(R.string.history_percent_less_than_one)
    } else {
        percent(month.takenPercent)
    }

    /**
     * Строка пропусков по датам и их природе — одной сборкой для лекарства и для паттерна.
     *
     * Разрез у них разный (по лекарству и по времени), а язык один: врач читает один документ,
     * и «отмечено человеком» обязано звучать одинаково в обоих разделах.
     */
    fun missesLine(dates: List<LocalDate>, skippedByPerson: Int, withoutMark: Int, locale: Locale): String {
        val listed = context.getString(R.string.report_document_missed_dates, missedDates(dates, locale))
        val natures = buildList {
            if (skippedByPerson > 0) {
                add(
                    context.resources.getQuantityString(
                        R.plurals.report_document_missed_by_person,
                        skippedByPerson,
                        skippedByPerson,
                    ),
                )
            }
            if (withoutMark > 0) {
                add(context.getString(R.string.report_document_missed_without_mark, withoutMark))
            }
        }
        return when (natures.size) {
            0 -> listed
            1 -> context.getString(R.string.report_document_missed_single, listed, natures.single())
            else -> context.getString(R.string.report_document_missed_both, listed, natures[0], natures[1])
        }
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
        return missesLine(
            dates = row.missedDates,
            skippedByPerson = row.skippedByPerson,
            withoutMark = row.missedWithoutMark,
            locale = locale,
        )
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

    /**
     * «1 таблетка», «2 таблетки», «0,5 таблетки»: склонение по количеству,
     * формулировки карточки лекарства.
     *
     * Доз за период может быть несколько — ступенчатый курс меняет дозу от этапа
     * к этапу (ПР-19), и отчёт называет их все по возрастанию: одно число курса сказало бы
     * врачу неправду о принятом. Список идёт разделителем перечисления, как времена и даты
     * в этом же документе: слова для него уже есть в шести каталогах (НФТ-42).
     */
    private fun dose(row: ReportMedicationRow): String = row.doses.joinToString(separator()) {
        doseText(it, doseUnitOf(row.doseUnit, row.form))
    }

    /**
     * «1 таблетка» — формулировка дозы одного приёма.
     *
     * Одной сборкой с документом отчёта: «доза за приём» в выгрузке истории и доза в отчёте —
     * одна и та же запись о принятом (ПР-19), и разойтись в словах они не имеют права.
     */
    @Suppress("LongParameterList")
    fun doseText(amount: BigDecimal, unit: DoseUnit): String {
        val template = when (unit) {
            DoseUnit.TABLET -> R.plurals.report_document_dose_tablets
            DoseUnit.CAPSULE -> R.plurals.report_document_dose_capsules
            DoseUnit.MILLILITRE -> R.plurals.report_document_dose_millilitres
            DoseUnit.DROP -> R.plurals.report_document_dose_drops
            DoseUnit.SPRAY -> R.plurals.report_document_dose_sprays
            DoseUnit.INJECTION -> R.plurals.report_document_dose_injections
        }
        return context.resources.getQuantityString(template, pluralQuantityOf(amount), amount(amount))
    }

    /** Единица дозы записи: код, а при пустом или незнакомом коде — форма лекарства (МГ-6). */
    fun doseUnitOf(code: String, form: MedicationForm): DoseUnit = DoseUnit.ofCode(code) ?: form.doseUnit()

    /** «50 мкг»; `null` — дозировка не задана. Одной сборкой с документом отчёта и выгрузкой. */
    fun strengthText(strength: BigDecimal?, unit: String?): String? =
        if (strength != null && unit != null) amount(strength) + " " + unitText(unit) else null

    /**
     * «50 мкг». Дозировка печатается только целиком: число без единицы врачу не говорит
     * ничего, а по модели без единицы и не хранится (ИНВ-5). Единица из закрытого списка
     * печатается словом языка; незнакомый код показывается как есть — отказать врачу
     * в строке хуже, чем показать код (МГ-6).
     */
    private fun strength(row: ReportMedicationRow): String? = strengthText(row.strength, row.strengthUnit)

    private fun unitText(code: String): String = when (StrengthUnit.ofCode(code)) {
        StrengthUnit.MILLIGRAM -> context.getString(R.string.strength_unit_milligram)
        StrengthUnit.MICROGRAM -> context.getString(R.string.strength_unit_microgram)
        StrengthUnit.GRAM -> context.getString(R.string.strength_unit_gram)
        StrengthUnit.MILLILITRE -> context.getString(R.string.strength_unit_millilitre)
        StrengthUnit.INTERNATIONAL_UNIT -> context.getString(R.string.strength_unit_international_unit)
        StrengthUnit.PERCENT -> context.getString(R.string.strength_unit_percent)
        null -> code
    }

    /** «0,5» и «2»: лишние нули дозы в отчёте только мешают. */
    private fun amount(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()

    private fun percent(value: Int): String = context.getString(R.string.history_percent, value)

    private fun separator(): String = context.getString(R.string.report_document_part_separator)

    private companion object {
        const val PERCENT = 100
    }
}
