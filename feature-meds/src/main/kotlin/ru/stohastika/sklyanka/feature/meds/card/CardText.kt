package ru.stohastika.sklyanka.feature.meds.card

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.medication.DayCell
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.MedicationSubtitle
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.dayAndMonth
import ru.stohastika.sklyanka.feature.meds.label
import ru.stohastika.sklyanka.feature.meds.strengthUnitLabel
import ru.stohastika.sklyanka.ui.component.AdherenceDay
import ru.stohastika.sklyanka.ui.component.AdherenceDayState
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Тексты карточки лекарства.
 *
 * Все формулировки — из ресурсов (НФТ-42), числительные — через `plurals` (НФТ-44),
 * даты — форматтером с локалью конфигурации (НФТ-43). Собраны в одном файле, чтобы
 * подпись титула и подписи недельной полосы не разошлись между блоками.
 */

/** «Таблетка круглая с риской · 48 мг · до еды» (ЭКР-7). */
@Composable
internal fun MedicationSubtitle.text(): String {
    val separator = stringResource(R.string.meds_separator)
    // Вид таблетки — часть фразы, а не подпись кнопки: в макете `MedCard.dc.html`
    // и спеке он со строчной («Таблетка круглая с риской»). Найдено экранной приёмкой.
    val head = listOfNotNull(form.label(), shape?.label()?.replaceFirstChar { it.lowercase(locale()) })
        .joinToString(" ")
    val dose = strength?.let { value -> strengthUnit?.let { "${value.plain()} ${strengthUnitLabel(it)}" } }
    return listOfNotNull(head, dose, foodRelation?.cardLabel()).joinToString(separator)
}

/**
 * Связь с едой в подписи титула: «до еды», «во время еды», «после еды».
 *
 * Свои формулировки, а не подписи кнопок формы («До еды», «Во время», «После»): в форме
 * это выбор, а здесь — часть фразы, и «Таблетка · 48 мг · После» по-русски не читается.
 * «Неважно» в подпись не попадает вовсе и здесь не переводится (см. [MedicationSubtitle]).
 */
@Composable
private fun FoodRelation.cardLabel(): String? = when (this) {
    FoodRelation.ANY -> null
    FoodRelation.BEFORE_MEAL -> stringResource(R.string.card_food_before)
    FoodRelation.WITH_MEAL -> stringResource(R.string.card_food_with)
    FoodRelation.AFTER_MEAL -> stringResource(R.string.card_food_after)
}

/** «2 приёма в сутки · по 2 таблетки». */
@Composable
internal fun ScheduleBlockUi.Scheduled.summary(): String {
    val separator = stringResource(R.string.meds_separator)
    // Склонение карточки («2 приёма в сутки»), а не строки списка («2 раза в сутки»):
    // в макете `MedCard.dc.html` и спеке частота названа приёмами. Найдено экранной приёмкой.
    val frequency = pluralStringResource(R.plurals.card_intakes_per_day, timesPerDay, timesPerDay)
    return listOf(frequency, doseText(doseAmount, doseUnit)).joinToString(separator)
}

/** «по 2 таблетки»: склонение зависит и от числа, и от единицы дозы. */
@Composable
internal fun doseText(amount: BigDecimal, unit: DoseUnit): String {
    val whole = amount.toInt()
    return pluralStringResource(
        when (unit) {
            DoseUnit.TABLET -> R.plurals.card_dose_tablets
            DoseUnit.CAPSULE -> R.plurals.card_dose_capsules
            DoseUnit.MILLILITRE -> R.plurals.card_dose_millilitres
            DoseUnit.DOSE -> R.plurals.card_dose_doses
        },
        whole,
        amount.plain(),
    )
}

/** «12 сентября — 12 октября». */
@Composable
internal fun coursePeriod(start: LocalDate, end: LocalDate): String =
    stringResource(R.string.card_course_period, start.dayAndMonth(), end.dayAndMonth())

/** «осталось 17 дней». */
@Composable
internal fun daysLeftText(days: Int): String = pluralStringResource(R.plurals.card_course_days_left, days, days)

/** «хватит на 4 дня». */
@Composable
internal fun stockDaysText(days: Int): String = pluralStringResource(R.plurals.card_stock_days_left, days, days)

/** «12 из 14 приёмов». */
@Composable
internal fun weekCounter(taken: Int, planned: Int): String =
    pluralStringResource(R.plurals.card_week_counter, planned, taken, planned)

/**
 * Столбики недельной полосы для компонента дизайн-системы.
 *
 * Подпись для чтения с экрана собирается здесь, а не в компоненте: состояние дня обязано
 * быть различимо без восприятия цвета (НФТ-33), и носителем смысла становится текст, а
 * не оттенок кружка.
 */
@Composable
internal fun DayCell.toAdherenceDay(): AdherenceDay {
    val weekday = date.weekdayShort()
    val full = date.weekdayFull()
    return when (this) {
        is DayCell.Today -> AdherenceDay(
            label = weekday,
            state = AdherenceDayState.Today,
            description = stringResource(R.string.cd_adherence_today, full),
        )

        is DayCell.BeforeCourse -> AdherenceDay(
            label = weekday,
            state = AdherenceDayState.Empty,
            description = stringResource(R.string.cd_adherence_before_course, full),
        )

        is DayCell.Completed -> AdherenceDay(
            label = weekday,
            state = when {
                planned == 0 -> AdherenceDayState.Empty
                taken == planned -> AdherenceDayState.Taken
                taken == 0 -> AdherenceDayState.Missed
                else -> AdherenceDayState.Partial
            },
            description = if (planned == 0) {
                stringResource(R.string.cd_adherence_no_intakes, full)
            } else {
                stringResource(R.string.cd_adherence_completed, full, taken, planned)
            },
        )
    }
}

/** «Сб» — буква дня недели с заглавной; регистр задаётся отрисовкой, а не ресурсом. */
@Composable
private fun LocalDate.weekdayShort(): String =
    format(cardFormatter(R.string.date_pattern_weekday_short)).replaceFirstChar { it.titlecase(locale()) }

/** «пятница» — полное название дня для программы чтения с экрана. */
@Composable
private fun LocalDate.weekdayFull(): String = format(cardFormatter(R.string.date_pattern_weekday_full))

/** Дробное число без хвостовых нулей: «2», а не «2.0». */
private fun BigDecimal.plain(): String = stripTrailingZeros().toPlainString()

@Composable
private fun locale(): Locale = LocalConfiguration.current.locales[0]

/**
 * Форматтер с локалью конфигурации.
 *
 * Локаль берётся из конфигурации, а не из `Locale.getDefault()`: при смене языка системы
 * конфигурация обновляется, а статическая локаль процесса — не всегда.
 */
@Composable
private fun cardFormatter(pattern: Int): DateTimeFormatter {
    val text = stringResource(pattern)
    val locale = locale()
    return remember(text, locale) { DateTimeFormatter.ofPattern(text, locale) }
}
