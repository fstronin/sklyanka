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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.medication.CourseEndPreset
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.DraftViolation
import ru.stohastika.sklyanka.domain.medication.StrengthUnit
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
 * Числа превью выбора: «2 шт · 48 мг» (§5.3, ЭКР-7).
 *
 * Порядок — как в подписи лекарства: сначала доза на один приём, затем дозировка
 * препарата. Пустая часть в строку не попадает — пустая дозировка законна (ИНВ-5),
 * и «·» без числа называл бы отсутствие данных числом. Пусто и то и другое — строка
 * пуста: форму и вид называет заголовок превью, и он остаётся всегда.
 */
@Composable
internal fun medicationPreviewNumbers(dose: String?, strength: String?): String =
    listOfNotNull(dose?.takeIf { it.isNotBlank() }, strength?.takeIf { it.isNotBlank() })
        .joinToString(PREVIEW_NUMBER_SEPARATOR)

/** Разделитель чисел превью — точка-пауза из макета (`AddMed.dc.html`). */
private const val PREVIEW_NUMBER_SEPARATOR = " · "

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
            stringResource(R.string.meds_period_open, line.startDate.sinceDayAndMonth())
        } else {
            stringResource(R.string.meds_period_until, end.dayAndMonth())
        }
    }
}

/** Суффикс поля количества дозы: слово единицы по форме выпуска (Р-45, §5.3). */
@Composable
internal fun DoseUnit.doseSuffix(): String = stringResource(
    when (this) {
        DoseUnit.TABLET -> R.string.dose_suffix_tablet
        DoseUnit.CAPSULE -> R.string.dose_suffix_capsule
        DoseUnit.MILLILITRE -> R.string.dose_suffix_millilitre
        DoseUnit.DROP -> R.string.dose_suffix_drop
        DoseUnit.SPRAY -> R.string.dose_suffix_spray
        DoseUnit.INJECTION -> R.string.dose_suffix_injection
    },
)

/** Единица остатка в упаковке: «таблеток», «капсул», «мл», «капель» (критерий 13). */
@Composable
internal fun DoseUnit.stockLabel(): String = stringResource(
    when (this) {
        DoseUnit.TABLET -> R.string.stock_unit_tablets
        DoseUnit.CAPSULE -> R.string.stock_unit_capsules
        DoseUnit.MILLILITRE -> R.string.stock_unit_millilitres
        DoseUnit.DROP -> R.string.stock_unit_drops
        DoseUnit.SPRAY -> R.string.stock_unit_sprays
        DoseUnit.INJECTION -> R.string.stock_unit_injections
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
            DoseUnit.DROP -> R.plurals.stock_left_drops
            DoseUnit.SPRAY -> R.plurals.stock_left_sprays
            DoseUnit.INJECTION -> R.plurals.stock_left_injections
        },
        amount,
        amount,
    )
}

/**
 * «2 таблетки», «3 капли» — доза одного приёма словами языка (§5.3, ЭКР-7).
 *
 * Число для склонения — целая часть: «1,5 таблетки» склоняется по «1», как и в остальном
 * интерфейсе. Единица словами, а не знаком «шт»: знак отвечает на вопрос «сколько штук»,
 * которого у капель, впрыска и инъекции не существует.
 */
@Composable
internal fun previewDoseLabel(amount: BigDecimal, unit: DoseUnit): String = pluralStringResource(
    when (unit) {
        DoseUnit.TABLET -> R.plurals.preview_dose_tablets
        DoseUnit.CAPSULE -> R.plurals.preview_dose_capsules
        DoseUnit.MILLILITRE -> R.plurals.preview_dose_millilitres
        DoseUnit.DROP -> R.plurals.preview_dose_drops
        DoseUnit.SPRAY -> R.plurals.preview_dose_sprays
        DoseUnit.INJECTION -> R.plurals.preview_dose_injections
    },
    amount.toInt(),
    amount.stripTrailingZeros().toPlainString(),
)

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
 * Известный код печатается словом языка (Р-45); незнакомый — как есть. Незнакомая единица
 * приходит только из файла переноса данных или из более старой версии, и подменять её своей
 * нельзя: это медицинские данные (МГ-6).
 */
@Composable
internal fun strengthUnitLabel(code: String): String = StrengthUnit.ofCode(code)?.label() ?: code

/** Слово единицы дозировки из закрытого списка (Р-45). */
@Composable
internal fun StrengthUnit.label(): String = stringResource(
    when (this) {
        StrengthUnit.MILLIGRAM -> R.string.strength_unit_milligram
        StrengthUnit.MICROGRAM -> R.string.strength_unit_microgram
        StrengthUnit.GRAM -> R.string.strength_unit_gram
        StrengthUnit.MILLILITRE -> R.string.strength_unit_millilitre
        StrengthUnit.INTERNATIONAL_UNIT -> R.string.strength_unit_international_unit
        StrengthUnit.PERCENT -> R.string.strength_unit_percent
    },
)

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

/**
 * «12 қыркүйектен» — дата перед послелогом «бастап» («с …»).
 *
 * Послелог требует исходного падежа, которого ICU для казахских месяцев не даёт:
 * язык добавляет суффикс из массива, остальные языки оставляют пустые значения.
 */
@Composable
internal fun LocalDate.sinceDayAndMonth(): String {
    val suffixes = stringArrayResource(R.array.month_since_suffixes)
    return dayAndMonth() + suffixes.getOrElse(monthValue - 1) { "" }
}

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
