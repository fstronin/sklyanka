package ru.stohastika.sklyanka.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import ru.stohastika.sklyanka.domain.history.MedicationAdherence
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import java.time.LocalTime
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Блок «По лекарствам» и строка-вывод (`design/screens/History.dc.html`).
 *
 * Строки идут по названию, а не по доле принятого: сортировка по доле переставляла бы
 * их при каждой отметке, и человек терял бы место, на которое смотрел.
 *
 * Архивированный курс показан наравне с активным и помечен словом, а не цветом: архив
 * придуман ровно для того, чтобы завершённый курс остался в истории (ПР-11).
 */
@Composable
internal fun MedicationBreakdown(
    rows: List<MedicationAdherence>,
    insightTime: LocalTime?,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Card)
            .border(SklyankaSizes.BorderThin, colors.lineOnSurface, SklyankaShapes.Card)
            .padding(SklyankaSpacing.Block)
            .testTag(TAG_BREAKDOWN),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Large),
    ) {
        Text(
            text = stringResource(R.string.history_breakdown_title),
            style = SklyankaType.Overline,
            color = colors.textMuted,
        )
        Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium)) {
            rows.forEach { row -> BreakdownRow(row) }
        }
        insightTime?.let { time ->
            Text(
                text = insightText(time),
                style = SklyankaType.CaptionSmall,
                color = colors.textMuted,
                modifier = Modifier.testTag(TAG_INSIGHT),
            )
        }
    }
}

@Composable
private fun BreakdownRow(row: MedicationAdherence) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_BREAKDOWN_ROW),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
        ) {
            Text(
                text = row.name,
                style = SklyankaType.BodyDefaultStrong,
                color = if (row.archived) colors.archiveText else colors.text,
                modifier = Modifier.weight(1f),
            )
            if (row.archived) {
                Text(
                    text = stringResource(R.string.history_archived_course),
                    style = SklyankaType.Micro,
                    color = colors.textTertiary,
                    modifier = Modifier.testTag(TAG_ARCHIVED_MARK),
                )
            }
            // ПРФ-14: отметку, сделанную владельцем телефона в чужом профиле, подписываем.
            // В истории своего профиля этой подписи не бывает: `actor_profile_id` там
            // не заполняется вовсе.
            if (row.markedByOtherProfile) {
                Text(
                    text = stringResource(UiR.string.status_marked_by_you),
                    style = SklyankaType.Micro,
                    color = colors.accent,
                    modifier = Modifier.testTag(TAG_MARKED_BY_YOU),
                )
            }
            Text(
                text = row.percentLabel(),
                style = SklyankaType.Caption,
                color = colors.textMuted,
            )
        }
        AdherenceBar(row)
    }
}

/**
 * Полоса доли принятого.
 *
 * Лекарство с низкой приверженностью окрашено акцентом, а не цветом «принято»
 * (`docs/spec/03-screens.md`): в чужом профиле акцент — цвет профиля, и выделение
 * остаётся выделением (ПРФ-11).
 *
 * Полоса убрана из дерева доступности: число рядом уже прочитано, и «прогресс-бар без
 * значения» читалка озвучивает шумом. Подпись всей строки собрана в [description].
 */
@Composable
private fun AdherenceBar(row: MedicationAdherence) {
    val colors = SklyankaTheme.colors
    val description = row.description()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SklyankaSizes.AdherenceBar)
            .background(colors.progressTrack, SklyankaShapes.Pill)
            // Метка — до очистки семантики: заданная после, она стирается вместе с ней.
            .testTag(TAG_BREAKDOWN_BAR)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(row.takenShare.toFloat().coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(if (row.low) colors.accent else colors.taken, SklyankaShapes.Pill),
        )
    }
}

const val TAG_BREAKDOWN = "history_breakdown"
const val TAG_BREAKDOWN_ROW = "history_breakdown_row"
const val TAG_BREAKDOWN_BAR = "history_breakdown_bar"
const val TAG_ARCHIVED_MARK = "history_archived_mark"
const val TAG_MARKED_BY_YOU = "history_marked_by_you"
const val TAG_INSIGHT = "history_insight"
