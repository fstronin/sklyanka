// Соответствия «предметное значение — текст интерфейса» собраны в одном файле намеренно:
// разложенные по экранам, они расходятся, и форма выпуска начинает называться по-разному
// в списке и в форме. Порог detekt по числу функций здесь поэтому поднят, а не раздроблен.
@file:Suppress("TooManyFunctions")

package ru.stohastika.sklyanka.feature.meds

import android.icu.text.ListFormatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.medication.CourseEndPreset
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.DraftViolation
import ru.stohastika.sklyanka.domain.medication.TrailingLine
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.ui.locale.interfaceLocale
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Перевод предметных значений в тексты интерфейса.
 *
 * Все формулировки берутся из ресурсов (НФТ-42), склонения — через `plurals` (НФТ-44),
 * даты — форматтером с локалью конфигурации, а не конкатенацией (НФТ-43). Собрано
 * в одном файле намеренно: разложенные по экранам, эти соответствия расходятся, и одна
 * и та же форма выпуска начинает называться по-разному в списке и в форме.
 */

@Composable
internal fun MedicationForm.label(): String = stringResource(
    when (this) {
        MedicationForm.TABLET -> R.string.med_form_tablet
        MedicationForm.CAPSULE -> R.string.med_form_capsule
        MedicationForm.SPRAY -> R.string.med_form_spray
        MedicationForm.SYRUP -> R.string.med_form_syrup
        MedicationForm.DROPS -> R.string.med_form_drops
        MedicationForm.INJECTION -> R.string.med_form_injection
    },
)

@Composable
internal fun TabletShape.label(): String = stringResource(
    when (this) {
        TabletShape.ROUND -> R.string.tablet_shape_round
        TabletShape.ROUND_SCORED -> R.string.tablet_shape_round_scored
        TabletShape.OVAL -> R.string.tablet_shape_oval
        TabletShape.OBLONG -> R.string.tablet_shape_oblong
        TabletShape.TRIANGULAR -> R.string.tablet_shape_triangular
    },
)

/**
 * «Таблетка круглая с риской» — форма и вид одной фразой (ЭКР-7, §4.16).
 *
 * Вид идёт со строчной: в макете и спеке он часть фразы, а не подпись кнопки. Общий
 * помощник, потому что это же правило собирает заголовок превью и подпись карточки,
 * и разойтись они не должны.
 */
@Composable
internal fun formAndShapeLabel(form: MedicationForm, shape: TabletShape?): String {
    val locale = LocalConfiguration.current.locales[0]
    return listOfNotNull(form.label(), shape?.label()?.replaceFirstChar { it.lowercase(locale) })
        .joinToString(" ")
}

/**
 * Заголовок превью выбора: форма плюс вид (§4.16).
 *
 * Вид применяется только к таблетке (ОВ-88), поэтому у остальных форм заголовок —
 * название формы.
 */
@Composable
internal fun medicationPreviewTitle(form: MedicationForm, shape: TabletShape?): String = formAndShapeLabel(form, shape)

/**
 * Подпись превью выбора (§4.16, ОВ-94).
 *
 * У форм, где доза описывается силуэтом (таблетка и капсула), — фраза про узнавание;
 * у форм, где доза описывается словами, — фраза с введённым количеством. Пустое поле
 * дозы называет общую фразу без числа: до ввода назвать дозу нечем.
 */
@Composable
internal fun medicationPreviewCaption(form: MedicationForm, dose: String?): String = when {
    form == MedicationForm.TABLET || form == MedicationForm.CAPSULE -> stringResource(R.string.form_preview_reusable)
    dose.isNullOrBlank() -> stringResource(R.string.form_preview_dose_words_empty)
    else -> stringResource(R.string.form_preview_dose_words, dose)
}

/**
 * Подпись пресета срока окончания (ПМ-12, SKL-75).
 *
 * Названия фиксированные, а не склоняемые, поэтому `plurals` не нужен: «2 недели»
 * стоит в списке неизменно, и подставлять число в шаблон негде.
 */
@Composable
internal fun CourseEndPreset.label(): String = stringResource(
    when (this) {
        CourseEndPreset.WEEK_1 -> R.string.period_preset_week_1
        CourseEndPreset.WEEKS_2 -> R.string.period_preset_weeks_2
        CourseEndPreset.MONTH_1 -> R.string.period_preset_month_1
        CourseEndPreset.MONTHS_2 -> R.string.period_preset_months_2
        CourseEndPreset.MONTHS_3 -> R.string.period_preset_months_3
        CourseEndPreset.MONTHS_6 -> R.string.period_preset_months_6
        CourseEndPreset.YEAR_1 -> R.string.period_preset_year_1
    },
)

@Composable
internal fun FoodRelation.label(): String = stringResource(
    when (this) {
        FoodRelation.ANY -> R.string.food_relation_any
        FoodRelation.BEFORE_MEAL -> R.string.food_relation_before
        FoodRelation.WITH_MEAL -> R.string.food_relation_with
        FoodRelation.AFTER_MEAL -> R.string.food_relation_after
    },
)

/**
 * «Ежедневно» при одном времени, иначе «2 раза в сутки» (ОВ-94).
 *
 * Первое слово фразы расписания: частота называется один раз, а не числом в описании.
 */
@Composable
internal fun frequencyLabel(timesPerDay: Int): String = if (timesPerDay <= 1) {
    stringResource(R.string.meds_frequency_daily)
} else {
    pluralStringResource(R.plurals.times_per_day, timesPerDay, timesPerDay)
}

/**
 * «08:00 и 20:00»: времена через запятую, последнее — с «и».
 *
 * Собирается `ListFormatter` с локалью приложения (SKL-62): конкатенация «08:00, 20:00»
 * сломается при переводе, а язык интерфейса принадлежит приложению, а не телефону.
 */
@Composable
internal fun timesPhrase(times: List<LocalTime>): String {
    val formatted = times.map { it.formatted() }
    val locale = interfaceLocale()
    return remember(formatted, locale) { ListFormatter.getInstance(locale).format(formatted) }
}

/**
 * «Ежедневно · 08:00 · с 12 марта · бессрочно» — строка расписания карточки списка (ОВ-94).
 *
 * Хвост — остаток либо период: одновременно они не показываются (ЭКР-4).
 */
@Composable
internal fun schedulePhrase(timesPerDay: Int, times: List<LocalTime>, trailing: TrailingLine): String {
    val separator = stringResource(R.string.meds_separator)
    return listOfNotNull(
        frequencyLabel(timesPerDay),
        timesPhrase(times).takeIf { times.isNotEmpty() },
        trailingTextOf(trailing),
    ).joinToString(separator)
}

/** «осталось 9 таблеток», «с 12 марта · бессрочно» или «до 30 сентября». */
@Composable
internal fun trailingTextOf(line: TrailingLine): String = when (line) {
    is TrailingLine.Stock -> stockLeftLabel(line.left, line.unit)
    is TrailingLine.Period -> {
        val end = line.endDate
        if (end == null) {
            stringResource(R.string.meds_period_open, line.startDate.dayAndMonth())
        } else {
            stringResource(R.string.meds_period_until, end.dayAndMonth())
        }
    }
}

/** Суффикс поля количества дозы: «шт» у штучных форм, «мл» и «доз» у остальных. */
@Composable
internal fun DoseUnit.doseSuffix(): String = stringResource(
    when (this) {
        DoseUnit.TABLET, DoseUnit.CAPSULE -> R.string.dose_suffix_piece
        DoseUnit.MILLILITRE -> R.string.dose_suffix_millilitre
        DoseUnit.DOSE -> R.string.dose_suffix_dose
    },
)

/** Единица остатка в упаковке: «таблеток», «капсул», «мл», «доз» (критерий 13). */
@Composable
internal fun DoseUnit.stockLabel(): String = stringResource(
    when (this) {
        DoseUnit.TABLET -> R.string.stock_unit_tablets
        DoseUnit.CAPSULE -> R.string.stock_unit_capsules
        DoseUnit.MILLILITRE -> R.string.stock_unit_millilitres
        DoseUnit.DOSE -> R.string.stock_unit_doses
    },
)

/** «осталось 9 таблеток» — склонение зависит и от числа, и от единицы. */
@Composable
internal fun stockLeftLabel(left: BigDecimal, unit: DoseUnit): String {
    val amount = left.toInt()
    return pluralStringResource(
        when (unit) {
            DoseUnit.TABLET -> R.plurals.stock_left_tablets
            DoseUnit.CAPSULE -> R.plurals.stock_left_capsules
            DoseUnit.MILLILITRE -> R.plurals.stock_left_millilitres
            DoseUnit.DOSE -> R.plurals.stock_left_doses
        },
        amount,
        amount,
    )
}

/**
 * «9 таблеток из 60» — остаток с размером упаковки (ОВ-45).
 *
 * Склонение единицы — по N ([stockLeftLabel]), размер — числом: «из 60 таблеток»
 * не говорят, а единица уже названа при остатке.
 */
@Composable
internal fun stockPackageLabel(left: BigDecimal, packageSize: BigDecimal, unit: DoseUnit): String = stringResource(
    R.string.card_stock_of_package,
    stockLeftLabel(left, unit),
    packageSize.stripTrailingZeros().toPlainString(),
)

/**
 * Единица дозировки препарата.
 *
 * Собственная форма пишет код `mg` и показывает его как «мг»; любое другое значение —
 * это запись, пришедшая из файла переноса данных (SKL-19) или из более старой версии,
 * и показывается как есть: подменять чужую единицу своей нельзя, это медицинские данные.
 */
@Composable
internal fun strengthUnitLabel(unit: String): String =
    if (unit == MILLIGRAM_CODE) stringResource(R.string.strength_unit_milligram) else unit

/** Текст отказа: каждое правило говорит, что именно не так, и про какое поле. */
@Composable
internal fun DraftViolation.message(): String = when (this) {
    DraftViolation.NameEmpty -> stringResource(R.string.error_name_empty)
    DraftViolation.DoseNotPositive -> stringResource(R.string.error_dose_not_positive)
    DraftViolation.StrengthWithoutUnit -> stringResource(R.string.error_strength_without_unit)
    DraftViolation.UnitWithoutStrength -> stringResource(R.string.error_unit_without_strength)
    DraftViolation.NoIntakeTimes -> stringResource(R.string.error_no_times)
    is DraftViolation.DuplicateTime -> stringResource(R.string.error_duplicate_time, time.formatted())
    DraftViolation.EndDateBeforeStart -> stringResource(R.string.error_end_before_start)
    DraftViolation.MaxPerDayMissing -> stringResource(R.string.error_max_per_day_missing)
    DraftViolation.MaxPerDayNotPositive -> stringResource(R.string.error_max_per_day_not_positive)
    DraftViolation.StockNegative -> stringResource(R.string.error_stock_negative)
    DraftViolation.PackageSizeNotPositive -> stringResource(R.string.error_package_size_not_positive)
}

/** «08:00». */
@Composable
internal fun LocalTime.formatted(): String = format(formatter(R.string.time_pattern))

/** «12 марта» — месяц в родительном падеже даёт сам форматтер локали. */
@Composable
internal fun LocalDate.dayAndMonth(): String = format(formatter(R.string.date_pattern_day_month))

/** «25 сентября 2026». */
@Composable
internal fun LocalDate.fullDate(): String = format(formatter(R.string.date_pattern_full))

/**
 * Форматтер с локалью конфигурации.
 *
 * Локаль берётся из конфигурации, а не из `Locale.getDefault()`: при смене языка
 * системы конфигурация обновляется, а статическая локаль процесса — не всегда,
 * и дата остаётся на прежнем языке до перезапуска.
 */
@Composable
private fun formatter(pattern: Int): DateTimeFormatter {
    val text = stringResource(pattern)
    val configuration = LocalConfiguration.current
    return remember(text, configuration) {
        DateTimeFormatter.ofPattern(text, configuration.locales[0])
    }
}

/** Код единицы дозировки, который пишет форма; показывается как «мг» (ОВ-16). */
private const val MILLIGRAM_CODE = "mg"
