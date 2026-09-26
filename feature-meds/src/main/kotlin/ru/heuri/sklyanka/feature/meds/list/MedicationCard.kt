package ru.heuri.sklyanka.feature.meds.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import ru.heuri.sklyanka.domain.medication.TrailingLine
import ru.heuri.sklyanka.feature.meds.ChevronGlyph
import ru.heuri.sklyanka.feature.meds.FormPlate
import ru.heuri.sklyanka.feature.meds.R
import ru.heuri.sklyanka.feature.meds.ReleaseFormGlyph
import ru.heuri.sklyanka.feature.meds.dayAndMonth
import ru.heuri.sklyanka.feature.meds.formatted
import ru.heuri.sklyanka.feature.meds.label
import ru.heuri.sklyanka.feature.meds.stockLeftLabel
import ru.heuri.sklyanka.feature.meds.strengthUnitLabel
import ru.heuri.sklyanka.ui.component.ButtonState
import ru.heuri.sklyanka.ui.component.ChipState
import ru.heuri.sklyanka.ui.component.SecondaryButton
import ru.heuri.sklyanka.ui.component.SklyankaChip
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.SklyankaShapes
import ru.heuri.sklyanka.ui.token.SklyankaSizes
import ru.heuri.sklyanka.ui.token.SklyankaSpacing
import ru.heuri.sklyanka.ui.token.SklyankaType
import ru.heuri.sklyanka.ui.R as UiR

/**
 * Карточка лекарства в списке (`Meds.dc.html`).
 *
 * Карточка «по потребности» отличается составом строк, а не оформлением (ЭКР-5):
 * серый чип рядом с названием, ограничение в конце строки описания, строка последнего
 * приёма вместо строки расписания и кнопка «Принять» вместо шеврона перехода.
 */
@Composable
internal fun MedicationCard(
    card: MedicationCardUi,
    onOpen: () -> Unit,
    onTake: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Card)
            .border(SklyankaSizes.BorderThin, colors.line, SklyankaShapes.Card)
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(SklyankaSpacing.Large)
            .testTag(TAG_MEDICATION_CARD),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        FormPlate(glyph = { ReleaseFormGlyph(form = card.form, color = colors.textMuted) })
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro),
        ) {
            TitleLine(card)
            Text(text = descriptionOf(card), style = SklyankaType.Caption, color = colors.textMuted)
            if (card.asNeeded != null) {
                LastIntakeLine(card.asNeeded)
            } else {
                ScheduleLine(card)
            }
        }
        TrailingAction(card, onTake)
    }
}

@Composable
private fun TitleLine(card: MedicationCardUi) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        Text(text = card.title, style = SklyankaType.BodyStrong, color = SklyankaTheme.colors.text)
        if (card.asNeeded != null) {
            SklyankaChip(
                text = stringResource(R.string.meds_as_needed_chip),
                state = ChipState.Info,
                shape = SklyankaShapes.Pill,
            )
        }
    }
}

/** Строка расписания: чипы времени и уточнение — остаток либо период (ЭКР-4). */
@Composable
private fun ScheduleLine(card: MedicationCardUi) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        card.times.forEach { time ->
            SklyankaChip(text = time.formatted(), state = ChipState.Info, shape = SklyankaShapes.Pill)
        }
        Text(
            text = trailingTextOf(card.trailingLine),
            style = SklyankaType.CaptionSmall,
            color = SklyankaTheme.colors.textMuted,
            modifier = Modifier.testTag(TAG_TRAILING_LINE),
        )
    }
}

@Composable
private fun LastIntakeLine(asNeeded: AsNeededCardUi) {
    val last = asNeeded.lastIntakeDate ?: return
    Text(
        text = stringResource(R.string.meds_last_intake, last.dayAndMonth()),
        style = SklyankaType.CaptionSmall,
        color = SklyankaTheme.colors.textMuted,
        modifier = Modifier.testTag(TAG_LAST_INTAKE),
    )
}

/** Шеврон перехода либо кнопка «Принять» — у одной карточки только одно из двух (ЭКР-5). */
@Composable
private fun TrailingAction(card: MedicationCardUi, onTake: () -> Unit) {
    val asNeeded = card.asNeeded
    if (asNeeded == null) {
        ChevronGlyph(
            color = SklyankaTheme.colors.iconFaint,
            modifier = Modifier.padding(top = SklyankaSpacing.Large),
        )
    } else {
        SecondaryButton(
            text = stringResource(UiR.string.action_take),
            onClick = onTake,
            state = if (asNeeded.takeEnabled) ButtonState.Normal else ButtonState.Disabled,
        )
    }
}

/** «Таблетка · 50 мкг · 1 раз в сутки» и «Капсула · 200 мг · не чаще 3 раз в сутки». */
@Composable
private fun descriptionOf(card: MedicationCardUi): String {
    val separator = stringResource(R.string.meds_separator)
    val strength = card.strength
    val unit = card.strengthUnit
    val frequency = card.asNeeded?.let { pluralStringResource(R.plurals.max_per_day, it.maxPerDay, it.maxPerDay) }
        ?: pluralStringResource(R.plurals.times_per_day, card.timesPerDay, card.timesPerDay)
    return listOfNotNull(
        card.form.label(),
        if (strength != null && unit != null) "$strength ${strengthUnitLabel(unit)}" else null,
        frequency,
    ).joinToString(separator)
}

/** «осталось 9 таблеток», «с 12 марта · бессрочно» или «до 30 сентября». */
@Composable
private fun trailingTextOf(line: TrailingLine): String = when (line) {
    is TrailingLine.Stock -> stockLeftLabel(line.left, line.unit)
    is TrailingLine.Period -> {
        val end = line.endDate
        if (end == null) {
            stringResource(R.string.meds_period_open, line.startDate.dayAndMonth())
        } else {
            stringResource(R.string.meds_period_until, end.dayAndMonth())
        }
    }
}

const val TAG_MEDICATION_CARD = "medication_card"
const val TAG_TRAILING_LINE = "medication_trailing_line"
const val TAG_LAST_INTAKE = "medication_last_intake"
