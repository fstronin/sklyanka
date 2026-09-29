package ru.stohastika.sklyanka.feature.today

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.intake.MarkResult
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.doseUnit
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.today.DayProgress
import ru.stohastika.sklyanka.domain.today.GroupLabelForm
import ru.stohastika.sklyanka.domain.today.IntakeItem
import ru.stohastika.sklyanka.domain.today.PartOfDay
import ru.stohastika.sklyanka.domain.today.StockNotice
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Перевод предметных значений экрана «Сегодня» в тексты интерфейса.
 *
 * Формулировки берутся из ресурсов (НФТ-42), склонения — через `plurals` (НФТ-44),
 * даты — форматтером с локалью конфигурации, а не конкатенацией (НФТ-43). Собрано
 * в одном файле намеренно: разложенные по компонентам, эти соответствия расходятся,
 * и одна и та же доза начинает называться по-разному в ленте и в групповой карточке.
 */

/** «Пятница, 25 сентября» — шапка экрана. Прописные делает сама шапка. */
@Composable
internal fun LocalDate.headerDate(): String = format(formatter(R.string.today_date_pattern))

/** «08:00» — заголовок ленты и время в строке плотного дня. */
@Composable
internal fun LocalTime.timeLabel(): String = format(formatter(R.string.today_time_pattern))

/** «Утро», «День», «Вечер», «Ночь» — метка справа от ленты. */
@Composable
internal fun PartOfDay.label(): String = stringResource(
    when (this) {
        PartOfDay.NIGHT -> R.string.part_of_day_night
        PartOfDay.MORNING -> R.string.part_of_day_morning
        PartOfDay.DAY -> R.string.part_of_day_day
        PartOfDay.EVENING -> R.string.part_of_day_evening
    },
)

/**
 * «Принято 2 из 5 приёмов» и «Принято 11 из 30 приёмов · 12 лекарств».
 *
 * Два числительных в одной строке склоняются независимо, и одним `plurals` они не
 * выражаются: счётчик приёмов согласован со знаменателем, счётчик лекарств — со
 * своим числом. Поэтому строка собирается шаблоном из ресурсов, а не склейкой.
 */
@Composable
internal fun DayProgress.caption(dense: Boolean): String {
    val intakes = pluralStringResource(
        ru.stohastika.sklyanka.ui.R.plurals.intakes_taken_of_total,
        planned,
        taken,
        planned,
    )
    if (!dense) return intakes
    val medications = pluralStringResource(R.plurals.today_medications, medicationCount, medicationCount)
    return stringResource(R.string.today_progress_dense, intakes, medications)
}

/**
 * Текст снэкбара отмены: «Магний B6 — приём отмечен как принятый» (SKL-30).
 *
 * При одном приёме с известным именем — строковая форма с именем; иначе счётная
 * по числу приёмов действия (нижние кнопки карточки отмечают весь слот). Шаблон всегда
 * ресурс, имя — аргумент подстановки, а не склейка (НФТ-42).
 */
@Composable
internal fun UndoNotice.text(): String = if (intakeIds.size == 1 && medicationNames.size == 1) {
    stringResource(
        when (result) {
            MarkResult.TAKEN -> R.string.today_undo_taken_single
            MarkResult.SKIPPED -> R.string.today_undo_skipped_single
        },
        medicationNames.single(),
    )
} else {
    pluralStringResource(
        when (result) {
            MarkResult.TAKEN -> R.plurals.today_undo_taken
            MarkResult.SKIPPED -> R.plurals.today_undo_skipped
        },
        intakeIds.size,
        intakeIds.size,
    )
}

/** «Магний B6 — осталось 9 таблеток, хватит на 4 дня». */
@Composable
internal fun StockNotice.text(): String = stringResource(
    R.string.today_stock_warning,
    medicationName,
    stockLeftLabel(unitsLeft, doseUnit),
    pluralStringResource(R.plurals.today_days_left, daysLeft, daysLeft),
)

/**
 * Подпись приёма под названием лекарства: «1 таблетка · 50 мкг · во время еды».
 *
 * Части собираются списком и соединяются разделителем из ресурсов: порядок задан
 * макетом, а необязательные части (дозировка, связь с едой) выпадают, не оставляя
 * висящей точки.
 */
@Composable
internal fun IntakeItem.doseCaption(): String = medication.doseCaption()

@Composable
internal fun Medication.doseCaption(): String {
    val separator = stringResource(R.string.today_separator)
    val parts = buildList {
        add(doseLabel(doseAmount, DoseUnit.ofCode(doseUnit) ?: form.doseUnit()))
        strength?.let { value ->
            add(stringResource(R.string.today_strength, value.plain(), strengthUnit.orEmpty()))
        }
        foodRelation.label()?.let(::add)
    }
    return parts.joinToString(separator)
}

/** «1 таблетка», «2 таблетки», «1 капсула». */
@Composable
private fun doseLabel(amount: BigDecimal, unit: DoseUnit): String = pluralStringResource(
    when (unit) {
        DoseUnit.TABLET -> R.plurals.today_dose_tablets
        DoseUnit.CAPSULE -> R.plurals.today_dose_capsules
        DoseUnit.MILLILITRE -> R.plurals.today_dose_millilitres
        DoseUnit.DOSE -> R.plurals.today_dose_doses
    },
    amount.toInt(),
    amount.plain(),
)

/** «осталось 9 таблеток» — склонение зависит и от числа, и от единицы. */
@Composable
private fun stockLeftLabel(left: BigDecimal, unit: DoseUnit): String = pluralStringResource(
    when (unit) {
        DoseUnit.TABLET -> R.plurals.today_stock_left_tablets
        DoseUnit.CAPSULE -> R.plurals.today_stock_left_capsules
        DoseUnit.MILLILITRE -> R.plurals.today_stock_left_millilitres
        DoseUnit.DOSE -> R.plurals.today_stock_left_doses
    },
    left.toInt(),
    left.plain(),
)

/** «до еды», «во время еды», «после еды»; у «безразлично» подписи нет вовсе. */
@Composable
private fun FoodRelation.label(): String? = when (this) {
    FoodRelation.ANY -> null
    FoodRelation.BEFORE_MEAL -> stringResource(R.string.food_relation_before)
    FoodRelation.WITH_MEAL -> stringResource(R.string.food_relation_with)
    FoodRelation.AFTER_MEAL -> stringResource(R.string.food_relation_after)
}

/** Подпись кнопки групповой отметки: «Отметить оба», «Отметить все три», «Отметить все N». */
@Composable
internal fun GroupLabelForm.label(count: Int): String = when (this) {
    GroupLabelForm.BOTH -> stringResource(R.string.group_mark_both)
    GroupLabelForm.ALL_THREE -> stringResource(R.string.group_mark_all_three)
    GroupLabelForm.ALL_N -> stringResource(R.string.group_mark_all_n, count)
}

/** «3 приёма · отметьте по одному» — по числу приёмов, ожидающих отметки (НФТ-44). */
@Composable
internal fun groupCaption(count: Int): String = pluralStringResource(R.plurals.group_caption_by_one, count, count)

/** Подпись подтверждения: «Подтвердить отметку всех трёх?» (НФТ-42). */
@Composable
internal fun GroupLabelForm.confirmLabel(count: Int): String = when (this) {
    GroupLabelForm.BOTH -> stringResource(R.string.group_confirm_both)
    GroupLabelForm.ALL_THREE -> stringResource(R.string.group_confirm_all_three)
    GroupLabelForm.ALL_N -> stringResource(R.string.group_confirm_all_n, count)
}

/** «1», а не «1.00»: доза введена человеком и показывается так, как он её ввёл. */
private fun BigDecimal.plain(): String = stripTrailingZeros().toPlainString()

/**
 * Форматтер с локалью конфигурации.
 *
 * Локаль берётся из конфигурации, а не из статической локали процесса: при смене
 * языка системы конфигурация обновляется, а статическая — не всегда, и дата
 * остаётся на прежнем языке до перезапуска.
 */
@Composable
private fun formatter(pattern: Int): DateTimeFormatter {
    val text = stringResource(pattern)
    val configuration = LocalConfiguration.current
    return remember(text, configuration) { DateTimeFormatter.ofPattern(text, configuration.locales[0]) }
}
