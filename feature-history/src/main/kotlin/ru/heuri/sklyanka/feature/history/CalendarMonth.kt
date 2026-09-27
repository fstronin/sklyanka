package ru.heuri.sklyanka.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import ru.heuri.sklyanka.domain.history.DayOutcome
import ru.heuri.sklyanka.domain.history.DayState
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.SklyankaColors
import ru.heuri.sklyanka.ui.token.SklyankaShapes
import ru.heuri.sklyanka.ui.token.SklyankaSizes
import ru.heuri.sklyanka.ui.token.SklyankaSpacing
import ru.heuri.sklyanka.ui.token.SklyankaType
import java.time.DayOfWeek
import java.time.YearMonth

/**
 * Календарь месяца (`design/screens/History.dc.html`).
 *
 * Состав по макету: переключатель «Сентябрь 2026» со стрелками, шапка недели «Пн … Вс»,
 * сетка дней и легенда из трёх подписей.
 *
 * Клетки не нажимаются. Раскрытие дня в список приёмов `docs/spec/03-screens.md` называет
 * решением, но в макете действия нет, и оно вынесено за объём задачи — вместе с ним
 * снимается и вопрос цели нажатия 44 dp для клетки 30 dp (ОВ-17): у ненажимаемого
 * элемента цели нажатия не существует.
 *
 * Состояние дня не передаётся цветом в одиночку (НФТ-33): у каждой клетки есть подпись
 * для чтения с экрана, называющая исход словами, а число дня видно глазами.
 */
@Composable
internal fun CalendarMonth(
    month: YearMonth,
    days: List<DayOutcome>,
    onIntent: (HistoryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Card)
            .border(SklyankaSizes.BorderThin, colors.lineOnSurface, SklyankaShapes.Card)
            .padding(SklyankaSpacing.Block)
            .testTag(TAG_CALENDAR),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
    ) {
        MonthSwitch(month, onIntent)
        WeekdayHeader()
        DayGrid(days)
        Legend(colors)
    }
}

@Composable
private fun MonthSwitch(month: YearMonth, onIntent: (HistoryIntent) -> Unit) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        GlyphButton(
            contentDescription = stringResource(R.string.cd_previous_month),
            onClick = { onIntent(HistoryIntent.PreviousMonth) },
            modifier = Modifier.testTag(TAG_PREVIOUS_MONTH),
        ) {
            ChevronGlyph(color = colors.textTertiary, pointsLeft = true)
        }
        Text(
            text = month.label(),
            style = SklyankaType.BodyStrong,
            color = colors.text,
            modifier = Modifier.testTag(TAG_MONTH_LABEL),
        )
        GlyphButton(
            contentDescription = stringResource(R.string.cd_next_month),
            onClick = { onIntent(HistoryIntent.NextMonth) },
            modifier = Modifier.testTag(TAG_NEXT_MONTH),
        ) {
            // Стрелка «вперёд» бледнее: в макете следующий месяц ещё не наступил.
            ChevronGlyph(color = colors.iconFaint, pointsLeft = false)
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().testTag(TAG_WEEKDAYS),
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        weekdayLabels().forEach { label ->
            Text(
                text = label,
                style = SklyankaType.MicroOverline,
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Сетка дней: недели рядами, первая неделя дополнена пустыми клетками до понедельника.
 *
 * Пустые клетки — это дни соседнего месяца, и они ничего не сообщают: ни цвета,
 * ни подписи у них нет (`docs/spec/03-screens.md`: «день вне месяца»).
 */
@Composable
private fun DayGrid(days: List<DayOutcome>) {
    val first = days.firstOrNull() ?: return
    val leading = first.date.dayOfWeek.value - DayOfWeek.MONDAY.value
    val cells: List<DayOutcome?> = List(leading) { null } + days
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TAG_DAY_GRID),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        cells.chunked(WEEK_LENGTH).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
            ) {
                week.forEach { day -> DayCell(day, Modifier.weight(1f)) }
                // Последняя неделя короче семи дней: добиваем ряд пустотами, иначе
                // клетки растягиваются и календарь перестаёт быть сеткой.
                repeat(WEEK_LENGTH - week.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(day: DayOutcome?, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    if (day == null) {
        Box(modifier = modifier.height(SklyankaSizes.CalendarDay))
        return
    }
    val fill = day.state.fill(colors)
    // Подпись вычисляется до модификатора: @Composable внутри лямбды семантики
    // не вызывается, а состояние дня обязано быть названо словами (НФТ-33).
    val description = day.description()
    Box(
        modifier = modifier
            .height(SklyankaSizes.CalendarDay)
            .background(fill, SklyankaShapes.Small)
            .then(
                if (day.isToday) {
                    Modifier.border(SklyankaSizes.AdherenceRing, colors.missed, SklyankaShapes.Small)
                } else {
                    Modifier
                },
            )
            // Метка ставится ДО очистки семантики: свойства, заданные после
            // `clearAndSetSemantics`, стираются вместе с семантикой потомков, и узел
            // остаётся без метки — то есть проверка перестаёт его находить.
            .testTag(TAG_DAY)
            // Читалке нужен один осмысленный узел «15 сентября, всё принято», а не пара
            // «15» и безымянная фигура (НФТ-30).
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            style = SklyankaType.Micro,
            color = day.state.dayNumberColor(colors),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Цвет клетки.
 *
 * Сегодняшний день закрашен по своему исходу, а обводка добавляется поверх: «сегодня» —
 * это признак, а не исход, и у сегодняшнего дня бывает и принятый приём, и пропуск.
 */
private fun DayState.fill(colors: SklyankaColors): Color = when (this) {
    DayState.ALL_TAKEN -> colors.taken
    DayState.PARTIAL -> colors.takenPartial
    DayState.MISSED -> colors.missed
    // Дни без приёмов, дни до начала истории и дни впереди нейтральны: приёма в них
    // либо не было, либо он ещё не наступил, и пропуском это не является.
    DayState.NO_INTAKES, DayState.BEFORE_HISTORY, DayState.AHEAD -> colors.neutralSoft
}

/** Число дня на тёмной заливке читается светлым, на светлой — обычным текстом. */
private fun DayState.dayNumberColor(colors: SklyankaColors): Color = when (this) {
    DayState.ALL_TAKEN, DayState.PARTIAL -> colors.surface
    DayState.MISSED -> colors.text
    DayState.NO_INTAKES, DayState.BEFORE_HISTORY, DayState.AHEAD -> colors.textTertiary
}

/** Легенда: «всё принято», «частично», «пропуск» (`History.dc.html`). */
@Composable
private fun Legend(colors: SklyankaColors) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(TAG_LEGEND),
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
    ) {
        LegendItem(stringResource(R.string.history_legend_all_taken), colors.taken, colors)
        LegendItem(stringResource(R.string.history_legend_partial), colors.takenPartial, colors)
        LegendItem(stringResource(R.string.history_legend_missed), colors.missed, colors)
    }
}

@Composable
private fun LegendItem(text: String, fill: Color, colors: SklyankaColors) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        Box(
            modifier = Modifier
                .size(SklyankaSizes.AdherenceLegendDot)
                .background(fill, SklyankaShapes.Small),
        )
        Text(text = text, style = SklyankaType.Micro, color = colors.textMuted)
    }
}

private const val WEEK_LENGTH = 7

const val TAG_CALENDAR = "history_calendar"
const val TAG_MONTH_LABEL = "history_month_label"
const val TAG_PREVIOUS_MONTH = "history_previous_month"
const val TAG_NEXT_MONTH = "history_next_month"
const val TAG_WEEKDAYS = "history_weekdays"
const val TAG_DAY_GRID = "history_day_grid"
const val TAG_DAY = "history_day"
const val TAG_LEGEND = "history_legend"
