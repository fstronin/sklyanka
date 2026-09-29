package ru.stohastika.sklyanka.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.history.DayOutcome
import ru.stohastika.sklyanka.domain.history.DayState
import ru.stohastika.sklyanka.domain.history.MedicationAdherence
import ru.stohastika.sklyanka.domain.history.OnTimeShare
import ru.stohastika.sklyanka.domain.today.PartOfDay
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Перевод посчитанного доменом в тексты экрана истории.
 *
 * Формулировки берутся из ресурсов (НФТ-42), склонения — через `plurals` (НФТ-44), даты —
 * форматтером с локалью конфигурации, а не склейкой (НФТ-43). Собрано в одном файле
 * намеренно: разложенные по компонентам, эти соответствия расходятся, и одно и то же
 * состояние дня начинает называться по-разному в календаре и в подписи для читалки.
 */

/** «Сентябрь 2026» — переключатель месяца. */
@Composable
internal fun YearMonth.label(): String {
    val locale = LocalConfiguration.current.locales[0]
    // Прописная буква — преобразованием, а не двенадцатью строками в ресурсах: названия
    // месяцев уже есть в локали, и вторая их копия однажды разойдётся с первой. Регистр
    // меняется с учётом локали: `titlecase()` без неё ломается на второй локали (НФТ-43).
    return format(formatter(R.string.history_month_pattern)).replaceFirstChar { it.titlecase(locale) }
}

/** «15 сентября» — дата в подписи дня для чтения с экрана. */
@Composable
internal fun LocalDate.dayLabel(): String = format(formatter(R.string.history_date_pattern))

/** «20:00» — время в строке-выводе. */
@Composable
internal fun LocalTime.timeLabel(): String = format(formatter(R.string.history_time_pattern))

/** «86 %» либо прочерк: доля принятого вовремя не определена, пока не согласован порог (ОВ-4). */
@Composable
internal fun OnTimeShare.label(): String = when (this) {
    is OnTimeShare.Value -> stringResource(R.string.history_percent, percent)
    OnTimeShare.Undefined -> stringResource(R.string.history_value_dash)
}

/** Число в плитке либо прочерк, когда за месяц нет ни одной записи. */
@Composable
internal fun tileValue(value: Int, empty: Boolean): String =
    if (empty) stringResource(R.string.history_value_dash) else value.toString()

/** «97 %» — доля принятого по лекарству. */
@Composable
internal fun MedicationAdherence.percentLabel(): String = stringResource(R.string.history_percent, takenPercent)

/**
 * Подпись дня для чтения с экрана: «15 сентября, всё принято».
 *
 * Состояние названо словами, а не только цветом (НФТ-33): легенда из трёх цветов сама
 * по себе недоступна, и клетка календаря без подписи для человека, не различающего эти
 * три цвета, не значит ничего.
 */
@Composable
internal fun DayOutcome.description(): String {
    val state = stringResource(
        when (this.state) {
            DayState.ALL_TAKEN -> R.string.history_legend_all_taken
            DayState.PARTIAL -> R.string.history_legend_partial
            DayState.MISSED -> R.string.history_legend_missed
            DayState.NO_INTAKES -> R.string.history_state_no_intakes
            DayState.AHEAD -> R.string.history_state_ahead
            DayState.BEFORE_HISTORY -> R.string.history_state_before_history
        },
    )
    val template = if (isToday) R.string.cd_history_day_today else R.string.cd_history_day
    return stringResource(template, date.dayLabel(), state)
}

/** «Принято 29 из 30 приёмов» — подпись строки лекарства для чтения с экрана. */
@Composable
internal fun MedicationAdherence.description(): String = stringResource(
    R.string.cd_history_medication,
    name,
    pluralStringResource(UiR.plurals.intakes_taken_of_total, planned, taken, planned),
)

/** «9 дней без пропусков» — подпись плитки для чтения с экрана. */
@Composable
internal fun daysWithoutMissesDescription(days: Int): String =
    pluralStringResource(R.plurals.cd_history_days_without_misses, days, days)

/** «12 пропусков за месяц» — подпись плитки для чтения с экрана. */
@Composable
internal fun missesDescription(misses: Int): String = pluralStringResource(R.plurals.cd_history_misses, misses, misses)

/**
 * Строка-вывод: «Чаще всего пропускается вечерний приём в 20:00.»
 *
 * Шаблон выбирается по времени суток, а прилагательное не склеивается с фразой:
 * «утренний приём» и «дневной приём» согласуются по-разному, а собранная из кусков
 * фраза ломается на первом же переводе (НФТ-43). Свободной генерации текста здесь
 * нет вовсе — только подстановка времени в готовую формулировку.
 */
@Composable
internal fun insightText(time: LocalTime): String = stringResource(
    when (PartOfDay.of(time)) {
        PartOfDay.MORNING -> R.string.history_insight_morning
        PartOfDay.DAY -> R.string.history_insight_day
        PartOfDay.EVENING -> R.string.history_insight_evening
        PartOfDay.NIGHT -> R.string.history_insight_night
    },
    time.timeLabel(),
)

/** Подписи дней недели в шапке календаря: «Пн» … «Вс» (`History.dc.html`). */
@Composable
internal fun weekdayLabels(): List<String> = listOf(
    stringResource(R.string.weekday_monday),
    stringResource(R.string.weekday_tuesday),
    stringResource(R.string.weekday_wednesday),
    stringResource(R.string.weekday_thursday),
    stringResource(R.string.weekday_friday),
    stringResource(R.string.weekday_saturday),
    stringResource(R.string.weekday_sunday),
)

/**
 * Форматтер по шаблону из ресурсов.
 *
 * Локаль берётся из конфигурации, а не из системного значения по умолчанию: тест и
 * снимок обязаны видеть тот же текст, что человек (НФТ-43).
 */
@Composable
private fun formatter(pattern: Int): DateTimeFormatter {
    val text = stringResource(pattern)
    val configuration = LocalConfiguration.current
    return remember(text, configuration) { DateTimeFormatter.ofPattern(text, configuration.locales[0]) }
}
