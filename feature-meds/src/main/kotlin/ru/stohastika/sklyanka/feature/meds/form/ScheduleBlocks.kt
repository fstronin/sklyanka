package ru.stohastika.sklyanka.feature.meds.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import ru.stohastika.sklyanka.domain.medication.DraftField
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.feature.meds.ChoiceTile
import ru.stohastika.sklyanka.feature.meds.ClockGlyph
import ru.stohastika.sklyanka.feature.meds.CounterCard
import ru.stohastika.sklyanka.feature.meds.CounterLabels
import ru.stohastika.sklyanka.feature.meds.CrossGlyph
import ru.stohastika.sklyanka.feature.meds.GlyphButton
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.Segment
import ru.stohastika.sklyanka.feature.meds.SegmentedControl
import ru.stohastika.sklyanka.feature.meds.formatted
import ru.stohastika.sklyanka.feature.meds.label
import ru.stohastika.sklyanka.feature.meds.message
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import java.time.LocalTime

/**
 * Режим приёма, расписание либо ограничение, связь с едой.
 *
 * Переключатель режима стоит **выше** блоков расписания — отступление от макета по Д-2,
 * зафиксированное в `TDD.md §5.6`: именно режим решает, нужны ли время и счётчик.
 */

/** «Режим приёма» — сегментный переключатель и пояснение к «по потребности». */
@Composable
internal fun ModeBlock(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit, anchors: FormAnchors) {
    FormBlock(stringResource(R.string.form_label_mode), null, anchors) {
        SegmentedControl(
            segments = listOf(
                Segment(
                    label = stringResource(R.string.form_mode_scheduled),
                    selected = state.draft.scheduleMode == ScheduleMode.SCHEDULED,
                    onClick = { onIntent(MedFormIntent.ModeChanged(ScheduleMode.SCHEDULED)) },
                ),
                Segment(
                    label = stringResource(R.string.form_mode_as_needed),
                    selected = state.draft.scheduleMode == ScheduleMode.PRN,
                    onClick = { onIntent(MedFormIntent.ModeChanged(ScheduleMode.PRN)) },
                ),
            ),
            modifier = Modifier.testTag(TAG_MODE_SWITCH),
        )
        BlockHint(stringResource(R.string.form_mode_as_needed_hint))
    }
}

/**
 * «Приёмов в сутки» и «Время приёма» либо ограничение «не чаще N раз в сутки».
 *
 * Дефект постановки Д-1: поля ограничения в макете нет вовсе, хотя ограничение
 * показывается и в списке лекарств, и на экране «Сегодня». Решение задачи: блок
 * «Приёмов в сутки» при режиме «по потребности» не скрывается, а меняет смысл и подпись.
 * Это сохраняет вёрстку макета и не добавляет седьмого блока в и без того длинную форму.
 */
@Composable
internal fun ScheduleBlock(
    state: MedFormUiState,
    onIntent: (MedFormIntent) -> Unit,
    anchors: FormAnchors,
    onPickTime: (Int, LocalTime) -> Unit,
) {
    if (state.applicability.maxPerDayVisible) {
        MaxPerDayBlock(state, onIntent, anchors)
    } else {
        IntakesPerDayBlock(state, onIntent, anchors)
        TimesBlock(state, onIntent, anchors, onPickTime)
    }
}

/** Счётчик приёмов: производная длины списка времён, а не хранимое значение (`TDD.md §9.2`). */
@Composable
private fun IntakesPerDayBlock(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit, anchors: FormAnchors) {
    FormBlock(null, null, anchors) {
        CounterCard(
            labels = CounterLabels(
                title = stringResource(R.string.form_label_intakes_per_day),
                caption = stringResource(R.string.form_caption_times_below),
                decrease = stringResource(R.string.cd_decrease),
                increase = stringResource(R.string.cd_increase),
            ),
            value = state.draft.intakesPerDay.toString(),
            onDecrease = { onIntent(MedFormIntent.TimeRemovedFromEnd) },
            onIncrease = { onIntent(MedFormIntent.TimeAdded) },
        )
    }
}

/** Ограничение «не чаще N раз в сутки» (Д-1): тот же ряд счётчика, другая подпись. */
@Composable
private fun MaxPerDayBlock(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit, anchors: FormAnchors) {
    FormBlock(null, DraftField.MAX_PER_DAY, anchors) {
        CounterCard(
            labels = CounterLabels(
                title = stringResource(R.string.form_label_max_per_day),
                caption = stringResource(R.string.form_caption_max_per_day),
                decrease = stringResource(R.string.cd_decrease),
                increase = stringResource(R.string.cd_increase),
            ),
            value = (state.draft.maxPerDay ?: 0).toString(),
            onDecrease = { onIntent(MedFormIntent.MaxPerDayDecreased) },
            onIncrease = { onIntent(MedFormIntent.MaxPerDayIncreased) },
            modifier = Modifier.testTag(TAG_MAX_PER_DAY),
        )
        state.violationOf(DraftField.MAX_PER_DAY)?.let { ViolationText(it.message()) }
    }
}

/** «Время приёма» — строки времени и пунктирная кнопка «Добавить время». */
@Composable
private fun TimesBlock(
    state: MedFormUiState,
    onIntent: (MedFormIntent) -> Unit,
    anchors: FormAnchors,
    onPickTime: (Int, LocalTime) -> Unit,
) {
    FormBlock(stringResource(R.string.form_label_times), DraftField.TIMES, anchors) {
        Column(
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
            modifier = Modifier.testTag(TAG_TIMES_BLOCK),
        ) {
            state.draft.times.forEachIndexed { index, time ->
                TimeRow(
                    time = time,
                    onPick = { onPickTime(index, time) },
                    onDelete = { onIntent(MedFormIntent.TimeRemoved(index)) },
                )
            }
            AddTimeButton(onClick = { onIntent(MedFormIntent.TimeAdded) })
            state.violationOf(DraftField.TIMES)?.let { ViolationText(it.message()) }
        }
    }
}

@Composable
private fun TimeRow(time: LocalTime, onPick: () -> Unit, onDelete: () -> Unit) {
    val colors = SklyankaTheme.colors
    val formatted = time.formatted()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Medium)
            .border(SklyankaSizes.BorderThin, colors.line, SklyankaShapes.Medium)
            .clickable(role = Role.Button, onClick = onPick)
            .padding(horizontal = SklyankaSpacing.Large, vertical = SklyankaSpacing.Small)
            .testTag(TAG_TIME_ROW),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        ClockGlyph(color = colors.iconFaint)
        Text(
            text = formatted,
            style = SklyankaType.BodyLarge,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        GlyphButton(
            contentDescription = stringResource(R.string.cd_delete_time, formatted),
            onClick = onDelete,
            bordered = false,
        ) {
            CrossGlyph(color = colors.iconFaint)
        }
    }
}

/** «Добавить время» — пунктирная кнопка макета; удлиняет список и счётчик разом. */
@Composable
private fun AddTimeButton(onClick: () -> Unit) {
    val colors = SklyankaTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SklyankaSizes.SecondaryButtonHeight)
            .border(SklyankaSizes.BorderThin, colors.lineInteractive, SklyankaShapes.Medium)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(TAG_ADD_TIME),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = stringResource(R.string.form_add_time), style = SklyankaType.Label, color = colors.accent)
    }
}

/**
 * «Связь с едой» — ряд из четырёх кнопок.
 *
 * Высота — минимум цели нажатия, а не жёсткий размер: переведённая подпись («Before food»,
 * «Тамаққа дейін») длиннее русской и переносится на вторую строку. Ряд растёт по самой
 * высокой плитке (IntrinsicSize.Min), плитки выравниваются по ней — §6.3 дизайн-системы
 * требует роста блока, а не обрезки текста.
 */
@Composable
internal fun FoodRelationBlock(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit, anchors: FormAnchors) {
    FormBlock(stringResource(R.string.form_label_food_relation), null, anchors) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
        ) {
            FoodRelation.entries.forEach { relation ->
                ChoiceTile(
                    label = relation.label(),
                    selected = state.draft.foodRelation == relation,
                    onClick = { onIntent(MedFormIntent.FoodRelationChanged(relation)) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .defaultMinSize(minHeight = SklyankaSizes.MinTouchTarget)
                        .testTag(TAG_FOOD_RELATION),
                )
            }
        }
    }
}

/** Сообщение об отказе под блоком, у которого нет собственного поля ввода. */
@Composable
internal fun ViolationText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = SklyankaType.CaptionSmall,
        color = SklyankaTheme.colors.warning,
        modifier = modifier.testTag(TAG_VIOLATION),
    )
}

const val TAG_MODE_SWITCH = "form_mode"
const val TAG_MAX_PER_DAY = "form_max_per_day"
const val TAG_TIMES_BLOCK = "form_times"
const val TAG_TIME_ROW = "form_time_row"
const val TAG_ADD_TIME = "form_add_time"
const val TAG_FOOD_RELATION = "form_food_relation"
const val TAG_VIOLATION = "form_violation"
