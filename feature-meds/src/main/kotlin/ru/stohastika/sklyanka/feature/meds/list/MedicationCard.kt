package ru.stohastika.sklyanka.feature.meds.list

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
import androidx.compose.ui.text.style.TextOverflow
import ru.stohastika.sklyanka.feature.meds.ChevronGlyph
import ru.stohastika.sklyanka.feature.meds.FormPlate
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.dayAndMonth
import ru.stohastika.sklyanka.feature.meds.label
import ru.stohastika.sklyanka.feature.meds.schedulePhrase
import ru.stohastika.sklyanka.feature.meds.strengthUnitLabel
import ru.stohastika.sklyanka.ui.component.ButtonState
import ru.stohastika.sklyanka.ui.component.ChipState
import ru.stohastika.sklyanka.ui.component.MedicationGlyph
import ru.stohastika.sklyanka.ui.component.SecondaryButton
import ru.stohastika.sklyanka.ui.component.SklyankaChip
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import ru.stohastika.sklyanka.ui.R as UiR

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
        FormPlate(
            glyph = {
                MedicationGlyph(
                    form = card.form,
                    shape = card.shape,
                    color = colors.textMuted,
                    size = SklyankaSizes.MedsListPlateGlyph,
                    modifier = Modifier.testTag(TAG_MED_LIST_GLYPH),
                )
            },
        )
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

/**
 * Название и, у карточки «по потребности», серый чип рядом с ним.
 *
 * Название ужимается, чип — нет. Без `weight(fill = false)` строка названия забирает
 * всю ширину первой, чипу достаётся нулевая, и его подпись разворачивается в столбик
 * по одной букве, раздувая карточку втрое. В макете это выражено `min-width: 0`
 * на колонке (`design/screens/Meds.dc.html`). Найдено переносом экранных проверок
 * на JVM (SKL-24): до него карточку «по потребности» не собирал ни один прогон.
 */
@Composable
private fun TitleLine(card: MedicationCardUi) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        Text(
            text = card.title,
            style = SklyankaType.BodyStrong,
            color = SklyankaTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (card.asNeeded != null) {
            SklyankaChip(
                text = stringResource(R.string.meds_as_needed_chip),
                state = ChipState.Info,
                shape = SklyankaShapes.Pill,
            )
        }
    }
}

/**
 * Строка расписания — одна фраза: частота, времена и уточнение (ОВ-94).
 *
 * Не набор чипов времени: «Ежедневно · 08:00 · с 12 марта · бессрочно» читается как
 * фраза, а длинная фраза переносится по словам. Уточнение — остаток либо период,
 * одновременно они не показываются (ЭКР-4).
 */
@Composable
private fun ScheduleLine(card: MedicationCardUi) {
    Text(
        text = schedulePhrase(card.timesPerDay, card.times, card.trailingLine),
        style = SklyankaType.CaptionSmall,
        color = SklyankaTheme.colors.textMuted,
        modifier = Modifier.testTag(TAG_TRAILING_LINE),
    )
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

/** «Таблетка · 50 мкг» и «Капсула · 200 мг · не чаще 3 раз в сутки». */
@Composable
private fun descriptionOf(card: MedicationCardUi): String {
    val separator = stringResource(R.string.meds_separator)
    val strength = card.strength
    val unit = card.strengthUnit
    // У расписания частота ушла во фразу расписания; у «по потребности» ограничение
    // остаётся частью описания (ЭКР-5).
    val limit = card.asNeeded?.let { pluralStringResource(R.plurals.max_per_day, it.maxPerDay, it.maxPerDay) }
    return listOfNotNull(
        card.form.label(),
        if (strength != null && unit != null) "$strength ${strengthUnitLabel(unit)}" else null,
        limit,
    ).joinToString(separator)
}

const val TAG_MEDICATION_CARD = "medication_card"
const val TAG_MED_LIST_GLYPH = "medication_list_glyph"
const val TAG_TRAILING_LINE = "medication_trailing_line"
const val TAG_LAST_INTAKE = "medication_last_intake"
