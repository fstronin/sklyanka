package ru.heuri.sklyanka.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import ru.heuri.sklyanka.ui.R
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.SklyankaColors
import ru.heuri.sklyanka.ui.token.SklyankaShapes
import ru.heuri.sklyanka.ui.token.SklyankaSizes
import ru.heuri.sklyanka.ui.token.SklyankaSpacing
import ru.heuri.sklyanka.ui.token.SklyankaType

/**
 * Состояние дня в недельной полосе приёмов.
 *
 * Перечисление вида, а не доменный статус приёма: `core-ui` не знает ни про `Intake`, ни
 * про `IntakeStatus`, иначе появление нового статуса (ОВ-12) стало бы правкой
 * дизайн-системы. Правило «какой день каким выглядит» живёт в домене (ЭКР-6).
 */
enum class AdherenceDayState {
    /** Все приёмы дня отмечены принятыми. */
    Taken,

    /** Часть приёмов дня принята. Роль `takenPartial` семантику ОВ-12 ещё ждёт. */
    Partial,

    /** Ни один приём дня не принят. */
    Missed,

    /** Сегодняшний день: полый кружок, в счёт не идёт (ЭКР-6). */
    Today,

    /** Приёмов в этот день не было: он раньше начала курса либо расписание его не задело. */
    Empty,
}

/**
 * Один столбик недельной полосы.
 *
 * [label] — буква дня («Сб»), [description] — то, что читает программа чтения с экрана.
 * Оба текста приходят готовыми: русского текста в `core-ui` нет (НФТ-42), а состояние
 * дня обязано быть различимо без восприятия цвета (НФТ-33) — цвет кружка сам по себе
 * этого не даёт, и подпись для читалки здесь не украшение, а второй носитель смысла.
 */
@Immutable
data class AdherenceDay(val label: String, val state: AdherenceDayState, val description: String)

/**
 * Блок «Последние 7 дней»: столбики дней и легенда (`design/screens/MedCard.dc.html`).
 *
 * Живёт в дизайн-системе, а не в `feature-meds`: тот же блок нужен экрану истории
 * (SKL-18), а `feature-*` друг от друга не зависят.
 */
@Composable
fun AdherenceWeek(days: List<AdherenceDay>, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().testTag(TAG_ADHERENCE_WEEK),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
        ) {
            days.forEach { day ->
                DayColumn(day, colors, Modifier.weight(1f))
            }
        }
        Legend(colors)
    }
}

@Composable
private fun DayColumn(day: AdherenceDay, colors: SklyankaColors, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.testTag(TAG_ADHERENCE_DAY),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        Text(
            text = day.label,
            style = if (day.state == AdherenceDayState.Today) SklyankaType.MicroStrong else SklyankaType.Micro,
            color = if (day.state == AdherenceDayState.Today) colors.text else colors.textTertiary,
            textAlign = TextAlign.Center,
        )
        DayMark(day, colors)
    }
}

/**
 * Кружок дня.
 *
 * Подпись для чтения с экрана ставится на кружок и затирает всё, что под ним:
 * читалке нужен один осмысленный узел «Пятница, сегодня», а не пара «Пт» и безымянная
 * фигура (НФТ-30).
 */
@Composable
private fun DayMark(day: AdherenceDay, colors: SklyankaColors) {
    val fill = when (day.state) {
        AdherenceDayState.Taken -> colors.taken
        AdherenceDayState.Partial -> colors.takenPartial
        AdherenceDayState.Missed -> colors.missed
        AdherenceDayState.Today, AdherenceDayState.Empty -> null
    }
    Box(
        modifier = Modifier
            .size(SklyankaSizes.AdherenceDay)
            .then(if (fill == null) Modifier else Modifier.background(fill, SklyankaShapes.Pill))
            .then(
                if (day.state == AdherenceDayState.Today) {
                    Modifier.border(SklyankaSizes.AdherenceRing, colors.missed, SklyankaShapes.Pill)
                } else {
                    Modifier
                },
            )
            .clearAndSetSemantics { contentDescription = day.description },
    )
}

@Composable
private fun Legend(colors: SklyankaColors) {
    Row(
        modifier = Modifier.testTag(TAG_ADHERENCE_LEGEND),
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Block),
    ) {
        LegendItem(
            text = stringResource(R.string.adherence_legend_taken),
            fill = colors.taken,
            outlined = false,
            colors = colors,
        )
        LegendItem(
            text = stringResource(R.string.adherence_legend_missed),
            fill = colors.missed,
            outlined = false,
            colors = colors,
        )
        LegendItem(
            text = stringResource(R.string.adherence_legend_today),
            fill = null,
            outlined = true,
            colors = colors,
        )
    }
}

@Composable
private fun LegendItem(text: String, fill: Color?, outlined: Boolean, colors: SklyankaColors) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        Box(
            modifier = Modifier
                .size(SklyankaSizes.AdherenceLegendDot)
                .then(if (fill == null) Modifier else Modifier.background(fill, SklyankaShapes.Pill))
                .then(
                    if (outlined) {
                        Modifier.border(SklyankaSizes.AdherenceRing, colors.missed, SklyankaShapes.Pill)
                    } else {
                        Modifier
                    },
                ),
        )
        Text(text = text, style = SklyankaType.Micro, color = colors.textMuted)
    }
}

const val TAG_ADHERENCE_WEEK = "adherence_week"
const val TAG_ADHERENCE_DAY = "adherence_day"
const val TAG_ADHERENCE_LEGEND = "adherence_legend"
