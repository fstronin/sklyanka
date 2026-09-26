package ru.heuri.sklyanka.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import ru.heuri.sklyanka.ui.R
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.SklyankaShapes
import ru.heuri.sklyanka.ui.token.SklyankaSizes
import ru.heuri.sklyanka.ui.token.SklyankaSpacing
import ru.heuri.sklyanka.ui.token.SklyankaType

/**
 * Карточка одного запланированного приёма.
 *
 * Компонент принимает готовые подписи, а не доменные модели: `core-ui` не знает ни
 * про `Intake`, ни про `IntakeStatus`, иначе появление нового статуса (ОВ-12) стало
 * бы правкой дизайн-системы, а не экрана.
 *
 * Статус никогда не передаётся одним лишь цветом (НФТ-33): у принятого приёма есть
 * галочка и время отметки, у пропущенного — подпись.
 */
@Composable
fun IntakeCard(
    state: IntakeCardState,
    labels: IntakeCardLabels,
    modifier: Modifier = Modifier,
    markedByYou: Boolean = false,
    actions: IntakeCardActions = IntakeCardActions(),
) {
    val colors = SklyankaTheme.colors
    val shape = SklyankaShapes.Card
    val background = if (state == IntakeCardState.AsNeeded) colors.background else colors.surface
    val border = when (state) {
        IntakeCardState.DueNow -> colors.accentBorder
        else -> colors.lineOnSurface
    }
    val borderWidth = if (state == IntakeCardState.DueNow) SklyankaSizes.BorderAccent else SklyankaSizes.BorderThin
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(background, shape)
            .then(
                if (state == IntakeCardState.AsNeeded) {
                    Modifier.dashedBorder(SklyankaSizes.BorderThin, colors.lineInteractive, SklyankaShapes.CardRadius)
                } else {
                    Modifier.border(borderWidth, border, shape)
                },
            )
            .padding(SklyankaSpacing.Large)
            .testTag(TAG_INTAKE_CARD),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
    ) {
        IntakeRow(
            icon = { IconPlate(state) },
            text = { IntakeText(state, labels, markedByYou) },
            action = { IntakeAction(state, labels, actions) },
        )
        if (state == IntakeCardState.DueNow) {
            // Ряд действий раскрывается только когда время наступило: карточка
            // «требует действия» отличается не одной лишь рамкой (§6.1, вывод 5).
            Row(horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default)) {
                PrimaryButton(
                    text = stringResource(R.string.action_taken),
                    onClick = { actions.onMark?.invoke() },
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = stringResource(R.string.action_skip),
                    onClick = { actions.onSkip?.invoke() },
                )
            }
        }
    }
}

/** Плашка иконки 40×40 с радиусом `Button` (§4.1). */
@Composable
private fun IconPlate(state: IntakeCardState) {
    val colors = SklyankaTheme.colors
    val plate = when (state) {
        IntakeCardState.DueNow -> colors.accentSoft
        IntakeCardState.Taken -> colors.takenSoft
        else -> colors.neutralSoft
    }
    val glyph = when (state) {
        IntakeCardState.DueNow -> colors.accent
        IntakeCardState.Taken -> colors.taken
        else -> colors.textMuted
    }
    Box(
        modifier = Modifier
            .size(SklyankaSizes.IconPlate)
            .background(plate, SklyankaShapes.Button),
        contentAlignment = Alignment.Center,
    ) {
        MedicationGlyph(color = glyph)
    }
}

@Composable
private fun IntakeText(state: IntakeCardState, labels: IntakeCardLabels, markedByYou: Boolean) {
    val colors = SklyankaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair)) {
        Text(
            text = labels.medication,
            style = SklyankaType.BodyDefaultStrong,
            color = if (state == IntakeCardState.Missed) colors.textMuted else colors.text,
        )
        Text(text = labels.dose, style = SklyankaType.Caption, color = colors.textMuted)
        if (markedByYou) {
            // §6.1, вывод 1: подпись 11 sp цветом TextTertiary даёт 3,56 и до нормы
            // не дотягивает, поэтому берётся TextMuted. Документ прямо требует
            // поднять такой текст, а не оставить его «декоративным дублем».
            Text(
                text = stringResource(R.string.status_marked_by_you),
                style = SklyankaType.Micro,
                color = colors.textMuted,
                modifier = Modifier.testTag(TAG_MARKED_BY_YOU),
            )
        }
    }
}

@Composable
private fun IntakeAction(state: IntakeCardState, labels: IntakeCardLabels, actions: IntakeCardActions) {
    val colors = SklyankaTheme.colors
    when (state) {
        IntakeCardState.Pending -> MarkButton(labels.medication, actions.onMark)

        IntakeCardState.Taken -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro),
            modifier = Modifier.testTag(TAG_TAKEN_STATUS),
        ) {
            CheckGlyph(color = colors.taken, size = SklyankaSizes.BannerIcon)
            Text(text = labels.time, style = SklyankaType.CaptionStrong, color = colors.taken)
        }

        IntakeCardState.Missed -> Text(
            text = stringResource(R.string.status_missed),
            style = SklyankaType.CaptionStrong,
            color = colors.textMuted,
            modifier = Modifier.testTag(TAG_MISSED_STATUS),
        )

        IntakeCardState.AsNeeded -> SecondaryButton(
            text = stringResource(R.string.action_take),
            onClick = { actions.onMark?.invoke() },
        )

        // Ряд действий у наступившего времени раскрывается под содержимым, а не
        // справа от него, поэтому в ряду место действия остаётся пустым.
        IntakeCardState.DueNow -> Spacer(Modifier)
    }
}

/** Круглая кнопка отметки: нарисована 44 dp и целью нажатия не меньше нормы. */
@Composable
private fun MarkButton(medicationLabel: String, onMark: (() -> Unit)?) {
    val colors = SklyankaTheme.colors
    Box(
        modifier = Modifier
            .clickable(enabled = onMark != null) { onMark?.invoke() }
            .semanticsLabel(stringResource(R.string.cd_mark_intake, medicationLabel))
            .size(SklyankaSizes.MarkButton)
            .background(colors.background, SklyankaShapes.Pill)
            .border(SklyankaSizes.BorderAccent, colors.lineInteractive, SklyankaShapes.Pill)
            .testTag(TAG_MARK_BUTTON),
        contentAlignment = Alignment.Center,
    ) {
        CheckGlyph(color = colors.textTertiary)
    }
}

/**
 * Ряд «иконка — текст — действие», переносящий действие под текст.
 *
 * Перестроение по измеренной ширине, а не по порогу `fontScale` (TDD §6.3): порог
 * по масштабу врёт при длинном названии лекарства — оно перестаёт помещаться и на
 * обычном масштабе. Кнопка отметки при этом не сжимается: её размер задан в `dp`,
 * и ряд уступает ей место, а не наоборот (критерий 5).
 */
@Composable
private fun IntakeRow(
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
    action: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        contents = listOf(icon, text, action),
        modifier = modifier.fillMaxWidth(),
    ) { (iconMeasurables, textMeasurables, actionMeasurables), constraints ->
        val gap = SklyankaSpacing.Medium.roundToPx()
        val minTextWidth = SklyankaSizes.MinTextColumn.roundToPx()
        val loose = Constraints()

        val iconPlaceable = iconMeasurables.first().measure(loose)
        val actionPlaceable = actionMeasurables.first().measure(loose)
        val actionGap = if (actionPlaceable.width == 0) 0 else gap

        // Ширина может прийти неограниченной (горизонтальная прокрутка): тогда ряд
        // занимает нужное содержимому, а не Constraints.Infinity точек.
        val available = if (constraints.hasBoundedWidth) {
            constraints.maxWidth
        } else {
            iconPlaceable.width + gap + minTextWidth + actionGap + actionPlaceable.width
        }
        val inlineTextWidth = available - iconPlaceable.width - gap - actionPlaceable.width - actionGap
        val stacked = inlineTextWidth < minTextWidth && actionPlaceable.width > 0
        val textWidth = if (stacked) available - iconPlaceable.width - gap else inlineTextWidth
        val textPlaceable = textMeasurables.first()
            .measure(Constraints(maxWidth = textWidth.coerceAtLeast(0)))

        val rowHeight = maxOf(iconPlaceable.height, textPlaceable.height, actionHeightInRow(actionPlaceable, stacked))
        val height = if (stacked) rowHeight + gap + actionPlaceable.height else rowHeight

        layout(available, height) {
            iconPlaceable.place(0, (rowHeight - iconPlaceable.height) / 2)
            textPlaceable.place(iconPlaceable.width + gap, (rowHeight - textPlaceable.height) / 2)
            if (stacked) {
                actionPlaceable.place(iconPlaceable.width + gap, rowHeight + gap)
            } else {
                actionPlaceable.place(available - actionPlaceable.width, (rowHeight - actionPlaceable.height) / 2)
            }
        }
    }
}

/** Перенесённое под текст действие в высоту верхнего ряда не входит. */
private fun actionHeightInRow(action: Placeable, stacked: Boolean): Int = if (stacked) 0 else action.height

/**
 * Пунктирная граница.
 *
 * `Modifier.border` пунктир не умеет, а блок «по потребности» отличается от
 * обычной карточки именно им: сплошная граница читалась бы как запланированный
 * приём, которого в расписании нет.
 */
private fun Modifier.dashedBorder(width: Dp, color: Color, radius: Dp): Modifier = this.then(
    Modifier.drawBehind {
        val strokeWidth = width.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(
                width = strokeWidth,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(SklyankaSizes.DashOn.toPx(), SklyankaSizes.DashOff.toPx()),
                ),
            ),
        )
    },
)

const val TAG_INTAKE_CARD = "intake_card"
const val TAG_MARK_BUTTON = "mark_button"
const val TAG_TAKEN_STATUS = "taken_status"
const val TAG_MISSED_STATUS = "missed_status"
const val TAG_MARKED_BY_YOU = "marked_by_you"
