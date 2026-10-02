package ru.stohastika.sklyanka.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.today.PrnEntry
import ru.stohastika.sklyanka.ui.component.IntakeCard
import ru.stohastika.sklyanka.ui.component.IntakeCardActions
import ru.stohastika.sklyanka.ui.component.IntakeCardLabels
import ru.stohastika.sklyanka.ui.component.IntakeCardState
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Блок «По потребности» (`Main.dc.html`).
 *
 * Показывается отдельно от лент расписания и скрывается целиком, когда лекарств без
 * расписания нет: пустого места на его месте не остаётся. В плотном дне блок
 * сворачивается такой же секцией, как время суток (`docs/spec/03-screens.md`).
 */
internal fun LazyListScope.prnBlock(state: TodayUiState.Content, onIntent: (TodayIntent) -> Unit) {
    if (state.prn.isEmpty()) return
    if (state.isDense && state.prnCollapsed) {
        item(key = "prn-collapsed") {
            CollapsedBlock(
                title = stringResource(R.string.today_prn_divider),
                summary = pluralStringResource(R.plurals.today_prn_collapsed, state.prn.size, state.prn.size),
                done = false,
                onClick = { onIntent(TodayIntent.TogglePrnBlock) },
                tag = TAG_PRN_COLLAPSED,
            )
        }
        return
    }
    item(key = "prn") {
        Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default)) {
            PrnDivider(onIntent, collapsible = state.isDense)
            state.prn.forEach { entry -> PrnCard(entry, onIntent) }
        }
    }
}

/** Разделитель с подписью «По потребности» — слева текст, справа линия. */
@Composable
private fun PrnDivider(onIntent: (TodayIntent) -> Unit, collapsible: Boolean) {
    val colors = SklyankaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_PRN_DIVIDER),
    ) {
        Text(
            text = stringResource(R.string.today_prn_divider),
            style = SklyankaType.LabelStrong,
            color = colors.text,
            modifier = if (collapsible) {
                // В плотном дне блок сворачивается так же, как время суток.
                Modifier.clickable { onIntent(TodayIntent.TogglePrnBlock) }
            } else {
                Modifier
            },
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(SklyankaSizes.DividerThickness)
                .background(colors.line),
        )
    }
}

/**
 * Карточка лекарства без расписания.
 *
 * Кнопка «Принять» недоступна, когда суточное ограничение выбрано (ПР-7), и причина
 * подписана текстом под карточкой: недоступность, выраженная одним серым цветом,
 * не объясняет человеку ничего (НФТ-33).
 */
@Composable
private fun PrnCard(entry: PrnEntry, onIntent: (TodayIntent) -> Unit) {
    val limit = entry.maxPerDay
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
        IntakeCard(
            state = IntakeCardState.AsNeeded,
            labels = IntakeCardLabels(
                medication = entry.medication.name,
                dose = if (limit == null) {
                    stringResource(R.string.today_prn_no_limit)
                } else {
                    pluralStringResource(R.plurals.today_prn_subtitle, limit, limit)
                },
            ),
            actions = IntakeCardActions(
                onMark = { onIntent(TodayIntent.TakePrn(entry.medicationId)) }.takeIf { entry.canTake },
            ),
            actionEnabled = entry.canTake,
            form = entry.medication.form,
            shape = entry.medication.shape,
            modifier = Modifier.testTag(TAG_PRN_CARD),
        )
        if (!entry.canTake) {
            Text(
                text = pluralStringResource(
                    R.plurals.today_prn_limit_reached,
                    entry.takenToday,
                    entry.takenToday,
                ),
                style = SklyankaType.CaptionSmall,
                color = SklyankaTheme.colors.textMuted,
                modifier = Modifier.testTag(TAG_PRN_LIMIT_REASON),
            )
        }
    }
}

const val TAG_PRN_DIVIDER = "prn_divider"
const val TAG_PRN_CARD = "prn_card"
const val TAG_PRN_LIMIT_REASON = "prn_limit_reason"
const val TAG_PRN_COLLAPSED = "prn_collapsed"
