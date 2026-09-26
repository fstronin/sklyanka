package ru.heuri.sklyanka.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.heuri.sklyanka.domain.today.GroupAction
import ru.heuri.sklyanka.domain.today.IntakeItem
import ru.heuri.sklyanka.domain.today.IntakePresentation
import ru.heuri.sklyanka.domain.today.TimeGroup
import ru.heuri.sklyanka.ui.component.IntakeCard
import ru.heuri.sklyanka.ui.component.IntakeCardActions
import ru.heuri.sklyanka.ui.component.IntakeCardLabels
import ru.heuri.sklyanka.ui.component.IntakeCardState
import ru.heuri.sklyanka.ui.component.PrimaryButton
import ru.heuri.sklyanka.ui.component.SecondaryButton
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.SklyankaShapes
import ru.heuri.sklyanka.ui.token.SklyankaSizes
import ru.heuri.sklyanka.ui.token.SklyankaSpacing
import ru.heuri.sklyanka.ui.token.SklyankaType

/**
 * Лента приёмов одного времени: разделитель и карточки (`Main.dc.html`).
 *
 * Несколько приёмов на одно время, время которых наступило, собираются в одну
 * групповую карточку (`MainDense.dc.html`, решение ОВ-32). Отказ заказчика от групповой
 * отметки убирается невыдачей [GroupAction] — разметка при этом не правится вовсе.
 */
@Composable
internal fun TimeLane(group: TimeGroup, state: TodayUiState.Content, onIntent: (TodayIntent) -> Unit) {
    val action = GroupAction.of(group).takeUnless { state.isGroupExpanded(group) }
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default)) {
        LaneHeader(group)
        if (action != null && group.isNow) {
            IntakeGroupCard(group, action, onIntent)
        } else {
            group.items.forEach { item -> IntakeRow(item, onIntent) }
        }
    }
}

/** Разделитель ленты: время слева, метка времени суток или «Сейчас» справа. */
@Composable
private fun LaneHeader(group: TimeGroup) {
    val colors = SklyankaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
        modifier = Modifier.fillMaxWidth().testTag(TAG_LANE_HEADER),
    ) {
        Text(text = group.time.timeLabel(), style = SklyankaType.LabelStrong, color = colors.text)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(SklyankaSizes.DividerThickness)
                .background(colors.line),
        )
        Text(
            text = if (group.isNow) stringResource(R.string.today_now) else group.partOfDay.label(),
            style = if (group.isNow) SklyankaType.CaptionStrong else SklyankaType.CaptionSmall,
            color = if (group.isNow) colors.accent else colors.textMuted,
        )
    }
}

/**
 * Одна строка приёма.
 *
 * Вид карточки приходит из домена, а не выводится по месту: «принято», «время
 * наступило», «время впереди» и «пропущено» — предметные состояния, и разметка их
 * не пересчитывает (`docs/spec/03-screens.md`).
 */
@Composable
internal fun IntakeRow(item: IntakeItem, onIntent: (TodayIntent) -> Unit, modifier: Modifier = Modifier) {
    IntakeCard(
        state = item.presentation.cardState(),
        labels = IntakeCardLabels(
            medication = item.medication.name,
            dose = item.doseCaption(),
            time = item.markedAt?.timeLabel().orEmpty(),
        ),
        actions = IntakeCardActions(
            onMark = { onIntent(TodayIntent.MarkTaken(item.id)) }.takeIf { item.isMarkable },
            onSkip = { onIntent(TodayIntent.MarkSkipped(item.id)) }.takeIf { item.isMarkable },
        ),
        modifier = modifier
            // Нажатие мимо кнопки отметки открывает карточку лекарства (`Main.dc.html`).
            .clickable { onIntent(TodayIntent.OpenMedication(item.medicationId)) },
    )
}

/**
 * Групповая карточка одновременных приёмов (`MainDense.dc.html`).
 *
 * Собрана из уже существующих частей дизайн-системы и отдельным компонентом каталога
 * не является (`docs/spec/08-design-system.md` §4.11): время с меткой «сейчас»,
 * перечисление лекарств одной строкой и две кнопки.
 */
@Composable
internal fun IntakeGroupCard(group: TimeGroup, action: GroupAction, onIntent: (TodayIntent) -> Unit) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Card)
            .border(SklyankaSizes.BorderAccent, colors.accentBorder, SklyankaShapes.Card)
            .padding(SklyankaSpacing.Large)
            .testTag(TAG_GROUP_CARD),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(text = group.time.timeLabel(), style = SklyankaType.BodyDefaultStrong, color = colors.text)
                Text(
                    text = stringResource(R.string.today_now_inline),
                    style = SklyankaType.CaptionStrong,
                    color = colors.accent,
                )
            }
            Text(
                text = group.items.joinToString(stringResource(R.string.group_medications_separator)) {
                    it.medication.name
                },
                style = SklyankaType.Caption,
                color = colors.textMuted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default)) {
            PrimaryButton(
                text = action.labelForm.label(action.count),
                onClick = { onIntent(TodayIntent.MarkGroupTaken(action.markableIntakeIds)) },
                modifier = Modifier.weight(1f).testTag(TAG_GROUP_MARK_ALL),
            )
            SecondaryButton(
                text = stringResource(R.string.group_mark_one_by_one),
                onClick = { onIntent(TodayIntent.ExpandGroup(group.time)) },
                modifier = Modifier.testTag(TAG_GROUP_ONE_BY_ONE),
            )
        }
    }
}

/**
 * Строка приёма плотного дня: время отдельной колонкой слева (`MainDense.dc.html`).
 *
 * Колонка шириной с цель нажатия: в плотном дне у каждой строки своё время, и без
 * выровненной колонки список читается как каша.
 */
@Composable
internal fun DenseIntakeRow(item: IntakeItem, onIntent: (TodayIntent) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = item.intake.scheduledAt.toLocalTime().timeLabel(),
            style = SklyankaType.LabelStrong,
            color = SklyankaTheme.colors.textMuted,
            modifier = Modifier.width(SklyankaSizes.MinTouchTarget),
        )
        IntakeRow(item, onIntent, Modifier.weight(1f))
    }
}

/** Вид карточки дизайн-системы по предметному виду приёма. */
private fun IntakePresentation.cardState(): IntakeCardState = when (this) {
    IntakePresentation.TAKEN -> IntakeCardState.Taken
    IntakePresentation.DUE_NOW -> IntakeCardState.DueNow
    IntakePresentation.UPCOMING -> IntakeCardState.Pending
    IntakePresentation.MISSED -> IntakeCardState.Missed
}

const val TAG_LANE_HEADER = "lane_header"
const val TAG_GROUP_CARD = "intake_group_card"
const val TAG_GROUP_MARK_ALL = "group_mark_all"
const val TAG_GROUP_ONE_BY_ONE = "group_one_by_one"
