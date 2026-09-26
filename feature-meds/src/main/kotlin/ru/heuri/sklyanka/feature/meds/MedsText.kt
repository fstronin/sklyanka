package ru.heuri.sklyanka.feature.meds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ru.heuri.sklyanka.domain.medication.DoseUnit
import ru.heuri.sklyanka.domain.medication.DraftViolation
import ru.heuri.sklyanka.domain.model.FoodRelation
import ru.heuri.sklyanka.domain.model.MedicationForm
import ru.heuri.sklyanka.domain.model.TabletShape
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

@Composable
internal fun FoodRelation.label(): String = stringResource(
    when (this) {
        FoodRelation.ANY -> R.string.food_relation_any
        FoodRelation.BEFORE_MEAL -> R.string.food_relation_before
        FoodRelation.WITH_MEAL -> R.string.food_relation_with
        FoodRelation.AFTER_MEAL -> R.string.food_relation_after
    },
)

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
