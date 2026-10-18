package ru.stohastika.sklyanka.feature.today

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.today.DaySection
import ru.stohastika.sklyanka.domain.today.SectionState
import ru.stohastika.sklyanka.domain.today.SectionSummary
import ru.stohastika.sklyanka.domain.today.TodayLayout
import ru.stohastika.sklyanka.ui.component.CheckGlyph
import ru.stohastika.sklyanka.ui.modifier.clickableShape
import ru.stohastika.sklyanka.ui.modifier.minTouchTarget
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Плотный день: свёртка по времени суток (`MainDense.dc.html`).
 *
 * Свёрнутая секция — одна строка с итогом, развёрнутая — разделитель со счётчиком
 * «3 из 9» и ленты внутри. Начальное состояние приходит из домена; нажатие обращает
 * его и живёт в состоянии экрана, а не в локальном состоянии Compose.
 */
internal fun LazyListScope.denseSections(
    layout: TodayLayout.Dense,
    state: TodayUiState.Content,
    onIntent: (TodayIntent) -> Unit,
) {
    layout.sections.forEach { section ->
        val expanded = state.isExpanded(section)
        todayItem(key = "section-${section.partOfDay}") {
            if (expanded) {
                SectionDivider(section, onIntent)
            } else {
                CollapsedSection(section, onIntent)
            }
        }
        if (expanded) {
            section.groups.forEach { group ->
                todayItem(key = "dense-lane-${group.time}") {
                    if (group.items.size > 1 && group.isNow) {
                        TimeLane(group, state, onIntent)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default)) {
                            val timeWidth = denseTimeColumnWidth(group.items)
                            group.items.forEach { item -> DenseIntakeRow(item, onIntent, timeWidth) }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Развёрнутая секция: «День · 12:00 — 17:00» и счётчик «3 из 9» акцентом.
 *
 * Нажатие на ряд сворачивает секцию обратно: тот же [TodayIntent.ToggleSection], что
 * и у свёрнутой плашки. Шеврон вверх обещает это действие, цель нажатия — не меньше
 * 44 dp (НФТ-29); без него раскрытая секция оставалась без обратного пути.
 */
@Composable
private fun SectionDivider(section: DaySection, onIntent: (TodayIntent) -> Unit) {
    val colors = SklyankaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onIntent(TodayIntent.ToggleSection(section.partOfDay)) }
            .minTouchTarget()
            .testTag(TAG_SECTION_EXPANDED),
    ) {
        Text(text = section.title(), style = SklyankaType.LabelStrong, color = colors.text)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(SklyankaSizes.DividerThickness)
                .background(colors.line),
        )
        Text(
            text = stringResource(R.string.dense_section_counter, section.marked, section.total),
            style = SklyankaType.CaptionStrong,
            color = colors.accent,
        )
        ChevronUpGlyph(color = colors.iconFaint)
    }
}

/**
 * Свёрнутая секция: плашка со значком, заголовок, итог и шеврон.
 *
 * Значок не украшение, а второй носитель смысла рядом с текстом (НФТ-33): галочка
 * у отмеченного времени суток, часы — у того, что впереди.
 */
@Composable
private fun CollapsedSection(section: DaySection, onIntent: (TodayIntent) -> Unit) {
    val summary = (section.state as? SectionState.Collapsed)?.summary ?: SectionSummary.PARTIAL
    CollapsedBlock(
        title = section.title(),
        summary = section.summaryText(summary),
        done = summary == SectionSummary.ALL_MARKED,
        onClick = { onIntent(TodayIntent.ToggleSection(section.partOfDay)) },
        tag = TAG_SECTION_COLLAPSED,
    )
}

/**
 * Свёрнутый блок: общая форма секции времени суток и блока «по потребности».
 *
 * Одна форма на два случая не ради экономии: спецификация прямо требует сворачивать
 * блок «по потребности» «такой же группой», и две похожие, но разные строки разошлись
 * бы при первой же правке (`docs/norm/04-behavior.md`).
 */
@Composable
internal fun CollapsedBlock(
    title: String,
    summary: String,
    done: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
        modifier = modifier
            .fillMaxWidth()
            .background(colors.archiveBackground, SklyankaShapes.Card)
            .border(SklyankaSizes.BorderThin, colors.line, SklyankaShapes.Card)
            .clickableShape(SklyankaShapes.Card, onClick = onClick)
            .padding(SklyankaSpacing.Large)
            .testTag(tag),
    ) {
        Box(
            modifier = Modifier
                .size(SklyankaSizes.IconPlate)
                .background(if (done) colors.takenSoft else colors.neutralSoft, SklyankaShapes.Pill),
            contentAlignment = Alignment.Center,
        ) {
            ClockOrCheck(done)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair),
        ) {
            Text(text = title, style = SklyankaType.BodyDefaultStrong, color = colors.textMuted)
            Text(text = summary, style = SklyankaType.CaptionSmall, color = colors.textMuted)
        }
        ChevronDownGlyph(color = colors.iconFaint)
    }
}

@Composable
private fun ClockOrCheck(done: Boolean) {
    val colors = SklyankaTheme.colors
    if (done) CheckGlyph(color = colors.taken) else ClockGlyph(color = colors.textMuted)
}

/** «Утро · 07:00 — 11:00». */
@Composable
private fun DaySection.title(): String = stringResource(
    R.string.dense_section_title,
    partOfDay.label(),
    partOfDay.from.timeLabel(),
    partOfDay.toInclusive.timeLabel(),
)

/** «8 приёмов, все отмечены», «13 приёмов впереди», «отмечено 5 из 8». */
@Composable
private fun DaySection.summaryText(summary: SectionSummary): String = when (summary) {
    SectionSummary.ALL_MARKED -> pluralStringResource(R.plurals.dense_section_all_marked, total, total)
    SectionSummary.AHEAD -> pluralStringResource(R.plurals.dense_section_ahead, total, total)
    SectionSummary.PARTIAL -> stringResource(R.string.dense_section_partial, marked, total)
}

const val TAG_SECTION_COLLAPSED = "dense_section_collapsed"
const val TAG_SECTION_EXPANDED = "dense_section_expanded"
