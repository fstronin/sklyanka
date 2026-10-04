package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.modifier.withCappedFontScale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.LiterataTime
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import java.time.LocalDate
import java.time.LocalTime

/**
 * Выбор времени и даты дизайн-системы (SKL-69, §4.14, ОВ-89).
 *
 * Системные `TimePickerDialog`/`DatePickerDialog` на EMUI рисует прошивка, и вид
 * диалога приложению не принадлежит. Здесь оболочка — токены «Склянки», механизм
 * выбора — Material3 (`TimePicker`/`TimeInput`/`DatePicker`). Тень M3 не берётся:
 * поверхность с рамкой и радиусом 16 ведёт себя как карточка, а не как Material-диалог.
 *
 * Верхнеуровневый `Dialog` с `usePlatformDefaultWidth = false`: системная ширина
 * диалога не совпадает с полем макета, и оболочку пришлось бы перебивать отступами.
 * Ширину задаёт собственная вёрстка.
 */
@Composable
internal fun DateTimePickerShell(
    title: String,
    confirmEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SklyankaTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(SklyankaSpacing.Screen)
                .testTag(TAG_DATE_TIME_PICKER),
            shape = SklyankaShapes.Large,
            color = colors.surface,
            border = BorderStroke(SklyankaSizes.BorderThin, colors.lineOnSurface),
        ) {
            Column(
                modifier = Modifier.padding(SklyankaSpacing.Block),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
            ) {
                Text(
                    text = title,
                    style = SklyankaType.BodyDefaultStrong,
                    color = colors.text,
                    modifier = Modifier.fillMaxWidth(),
                )
                content()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuietButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onDismiss,
                        kind = QuietButtonKind.Muted,
                        form = QuietButtonForm.Header,
                    )
                    QuietButton(
                        text = stringResource(R.string.action_done),
                        onClick = onConfirm,
                        form = QuietButtonForm.Header,
                        state = if (confirmEnabled) ButtonState.Normal else ButtonState.Disabled,
                    )
                }
            }
        }
    }
}

/**
 * Диалог времени (SKL-69, §5.2).
 *
 * Два режима в одном диалоге: циферблат `TimePicker` и клавиатурный `TimeInput`.
 * Переключение — тихая кнопка; второй диалог ради клавиатуры не заводится (§4.14).
 *
 * [is24Hour] приходит параметром, а не читается из Android: `core-ui` не знает про
 * `android.text.format`, и оба режима проверяемы на JVM. Платформенную настройку читает
 * маршрут формы в момент открытия (TDD §10.3).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    title: String,
    initial: LocalTime,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = is24Hour,
    )
    var keyboardInput by remember { mutableStateOf(false) }
    val baseDensity = LocalDensity.current
    val pickerTypography = timePickerTypography()
    val pickerColors = TimePickerDefaults.colors(
        clockDialColor = colors.surface,
        clockDialSelectedContentColor = colors.surface,
        clockDialUnselectedContentColor = colors.text,
        selectorColor = colors.accent,
        containerColor = colors.surface,
        timeSelectorSelectedContainerColor = colors.accentSoft,
        timeSelectorSelectedContentColor = colors.accent,
        timeSelectorUnselectedContainerColor = colors.surface,
        timeSelectorUnselectedContentColor = colors.text,
        periodSelectorBorderColor = colors.lineOnSurface,
        periodSelectorSelectedContainerColor = colors.accentSoft,
        periodSelectorSelectedContentColor = colors.accent,
        periodSelectorUnselectedContainerColor = colors.surface,
        periodSelectorUnselectedContentColor = colors.textMuted,
    )
    DateTimePickerShell(
        title = title,
        confirmEnabled = true,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(LocalTime.of(state.hour, state.minute)) },
        modifier = modifier,
    ) {
        MaterialTheme(typography = pickerTypography) {
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = PICKER_FONT_SCALE),
            ) {
                if (keyboardInput) {
                    TimeInput(state = state, colors = pickerColors, modifier = Modifier.testTag(TAG_TIME_INPUT))
                } else {
                    TimePicker(state = state, colors = pickerColors, modifier = Modifier.testTag(TAG_TIME_PICKER))
                }
            }
        }
        QuietButton(
            text = stringResource(
                if (keyboardInput) R.string.cd_switch_time_dial else R.string.cd_switch_time_input,
            ),
            onClick = { keyboardInput = !keyboardInput },
        )
    }
}

/**
 * Типографика механизма пикера (ОВ-118/ОВ-119).
 *
 * Цифры обоих режимов — `LiterataTime`: метрики шрифта дают строку 72 dp с цифрой
 * по центру, и подложка слота 96×80 dp центрирует её, а поле ввода 96×72 dp совпадает
 * со строкой. Высота строки — токен поля: намерение читается и не зависит от метрик
 * файла. `displaySmall`/`headlineLarge` остаются прежними — цифрами эти роли не набраны.
 */
@Composable
private fun timePickerTypography(): Typography {
    val base = MaterialTheme.typography
    val digits = SklyankaType.Display.copy(
        fontFamily = LiterataTime,
        lineHeight = SklyankaType.TimePickerLineHeight,
    )
    return base.copy(
        displayLarge = digits,
        displayMedium = digits,
        displaySmall = SklyankaType.Display,
        headlineLarge = SklyankaType.Display,
    )
}

/**
 * Диалог даты (SKL-69, §5.3).
 *
 * Пересчёт выбранного дня идёт строго в UTC: `DatePickerState` хранит миллисекунды
 * полуночи UTC, и локальный пояс к ним не применяется (TDD §10.1). «Готово» доступно
 * только при выбранной дате.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongParameterList")
@Composable
fun DateTimePickerDialog(
    title: String,
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Границы выбора (ОВ-125): недоступные дни приглушены и не нажимаются
     * (механизм M3). Режима «диапазон» у диалога нет — даты выбираются по одной.
     *
     * Границы приходят парой дат, а не M3-типом `SelectableDates`: тот объявлен
     * экспериментальным, и его тип в сигнатуре заставлял бы каждый экран писать
     * `@OptIn` — платформенное требование протекло бы в `feature-*`. Домен и экраны
     * говорят на языке «раньше этой даты нельзя, позже этой нельзя».
     */
    notBefore: LocalDate? = null,
    notAfter: LocalDate? = null,
) {
    val colors = SklyankaTheme.colors
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toUtcMillis(),
        selectableDates = remember(notBefore, notAfter) { BoundedDates(notBefore, notAfter) },
    )
    val selected = state.selectedDateMillis
    DateTimePickerShell(
        title = title,
        confirmEnabled = selected != null,
        onDismiss = onDismiss,
        onConfirm = { if (selected != null) onConfirm(selected.toLocalDateUtc()) },
        modifier = modifier,
    ) {
        // Заголовок выбранной даты M3 набирает ролью `headlineLarge`, а мост темы
        // отдаёт её `Display` (64 sp): дата не помещалась и год обрезался
        // (SKL-101, дефект заказчика). Локальная типографика берёт `Headline`
        // с потолком масштаба — как цифры тайм-пикера (ОВ-119).
        MaterialTheme(typography = datePickerTypography()) {
            DatePicker(
                state = state,
                modifier = Modifier.testTag(TAG_DATE_PICKER),
                colors = DatePickerDefaults.colors(
                    containerColor = colors.surface,
                    titleContentColor = colors.text,
                    headlineContentColor = colors.accent,
                    weekdayContentColor = colors.textMuted,
                    subheadContentColor = colors.text,
                    navigationContentColor = colors.text,
                    yearContentColor = colors.text,
                    currentYearContentColor = colors.accent,
                    selectedYearContentColor = colors.surface,
                    selectedYearContainerColor = colors.accent,
                    dayContentColor = colors.text,
                    selectedDayContentColor = colors.surface,
                    selectedDayContainerColor = colors.accent,
                    todayContentColor = colors.accent,
                    todayDateBorderColor = colors.accent,
                    dividerColor = colors.lineOnSurface,
                ),
            )
        }
    }
}

/**
 * Типографика механизма даты (SKL-101).
 *
 * Выбранную дату M3 набирает ролью `headlineLarge`, а мост темы отдаёт её `Display`
 * (64 sp) — двухстрочная дата не помещалась в заголовок и год обрезался. Здесь та же
 * роль получает `Headline` с потолком масштаба: дата читается заголовком, но влезает
 * целиком и не раздувается при системном шрифте 200 % (как цифры тайм-пикера, ОВ-119).
 */
@Composable
private fun datePickerTypography(): Typography {
    val headline = SklyankaType.Headline.withCappedFontScale()
    return MaterialTheme.typography.copy(
        displayLarge = headline,
        displayMedium = headline,
        displaySmall = headline,
        headlineLarge = headline,
    )
}

/** Тег оболочки: экранная проверка отличает диалог приложения от системного (SKL-69). */
const val TAG_DATE_TIME_PICKER = "date_time_picker"
const val TAG_TIME_PICKER = "date_time_picker_time"
const val TAG_TIME_INPUT = "date_time_picker_time_input"
const val TAG_DATE_PICKER = "date_time_picker_date"

/**
 * Границы выбора дня: недоступные дни приглушены и не нажимаются (§4.14, ОВ-125).
 *
 * Адаптер из пары дат в экспериментальный M3-интерфейс — единственное место, где
 * платформенный тип касается выбора даты. Сравнение идёт в UTC: `DatePickerState`
 * хранит миллисекунды полуночи UTC, и локальный пояс к ним не применяется.
 */
@OptIn(ExperimentalMaterial3Api::class)
private class BoundedDates(private val notBefore: LocalDate?, private val notAfter: LocalDate?) : SelectableDates {

    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
        val date = utcTimeMillis.toLocalDateUtc()
        return (notBefore == null || !date.isBefore(notBefore)) &&
            (notAfter == null || !date.isAfter(notAfter))
    }
}

private const val PICKER_FONT_SCALE = 1f
