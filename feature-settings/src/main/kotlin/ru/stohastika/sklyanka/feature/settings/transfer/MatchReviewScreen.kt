package ru.stohastika.sklyanka.feature.settings.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.medication.StrengthUnit
import ru.stohastika.sklyanka.domain.transfer.MatchDecision
import ru.stohastika.sklyanka.domain.transfer.MatchDifference
import ru.stohastika.sklyanka.domain.transfer.MedicationMatch
import ru.stohastika.sklyanka.feature.settings.R
import ru.stohastika.sklyanka.ui.component.ChipState
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.QuietButton
import ru.stohastika.sklyanka.ui.component.ScreenHeader
import ru.stohastika.sklyanka.ui.component.ScreenHeaderKind
import ru.stohastika.sklyanka.ui.component.ScreenHeaderLabels
import ru.stohastika.sklyanka.ui.component.SklyankaChip
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Разбор совпадений при слиянии (ПЕР-15 п. 5, ПЕР-21, ПЕР-22).
 *
 * Отдельный экран, а не диалоги поверх списка: спецификация требует по одному решению
 * на совпадение и запрещает массовое применение, а двадцать совпадений в диалогах — это
 * двадцать модальных окон. Списком карточек это читается, и кнопка подтверждения одна.
 *
 * Кнопки «применить ко всем» здесь нет и не появится: именно на этом шаге человек
 * распоряжается своими медицинскими данными. Выход из длинного списка есть — «не загружать
 * ничего из совпавших», и это не то же самое, что «применить ко всем»: он ничего
 * не переписывает.
 *
 * Экрана в макетах нет; собран из существующих компонентов дизайн-системы (ОВ-67).
 */
@Composable
fun MatchReviewScreen(
    state: TransferUiState,
    onIntent: (TransferIntent) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .navigationBarsPadding()
            .testTag(TAG_MATCHES),
    ) {
        ScreenHeader(
            labels = ScreenHeaderLabels(title = stringResource(R.string.transfer_matches_title)),
            kind = ScreenHeaderKind.Nested,
            onBack = onCancel,
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SklyankaSpacing.Section),
            verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
        ) {
            Text(
                text = stringResource(
                    if (state.matches.isEmpty()) {
                        R.string.transfer_matches_empty
                    } else {
                        R.string.transfer_matches_hint
                    },
                ),
                style = SklyankaType.BodyDefault,
                color = colors.textMuted,
            )
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
            ) {
                items(state.matches, key = { it.file.id }) { match ->
                    MatchCard(
                        match = match,
                        decision = state.decisionFor(match.file.id),
                        onDecide = { onIntent(TransferIntent.Decide(match.file.id, it)) },
                    )
                }
            }
            if (state.matches.isNotEmpty()) {
                QuietButton(
                    text = stringResource(R.string.transfer_matches_drop_all),
                    onClick = { onIntent(TransferIntent.DropAllMatches) },
                    modifier = Modifier.testTag(TAG_DROP_ALL),
                )
            }
            PrimaryButton(
                text = stringResource(R.string.transfer_matches_apply),
                onClick = { onIntent(TransferIntent.Apply) },
                modifier = Modifier.fillMaxWidth().testTag(TAG_APPLY_MERGE),
            )
        }
    }
}

/**
 * Карточка одного совпадения: название с дозировкой, различия и выбор одного из четырёх решений.
 *
 * Различия показываются, но на признание совпадения не влияют (ПЕР-20): совпало название
 * и дозировка — значит это одно лекарство, а расписание и остаток человек сравнивает сам.
 */
@Composable
private fun MatchCard(match: MedicationMatch, decision: MatchDecision, onDecide: (MatchDecision) -> Unit) {
    val colors = SklyankaTheme.colors
    // Дозировка записи необязательна (ПЕР-20): без неё заголовок — одно название, иначе
    // разделитель повис бы в пустоте (V-42).
    val dosage = strengthLabel(match)
    TransferCard(
        title = if (dosage.isEmpty()) {
            match.file.name
        } else {
            stringResource(R.string.transfer_match_dosage, match.file.name, dosage)
        },
        subtitle = stringResource(R.string.transfer_match_subtitle),
        modifier = Modifier.testTag(matchTag(match.file.id)),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro)) {
            match.differences.forEach { difference ->
                Text(
                    text = difference.label(
                        mine = if (difference == MatchDifference.TIMES) match.device.times.timesLabel() else "",
                        theirs = if (difference == MatchDifference.TIMES) match.file.times.timesLabel() else "",
                    ),
                    style = SklyankaType.CaptionSmall,
                    color = colors.textMuted,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
            Row(horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
                DecisionChip(MatchDecision.KEEP_MINE, decision, onDecide)
                DecisionChip(MatchDecision.TAKE_FROM_FILE, decision, onDecide)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
                DecisionChip(MatchDecision.ADD_SEPARATELY, decision, onDecide)
                DecisionChip(MatchDecision.DO_NOT_LOAD, decision, onDecide)
            }
        }
    }
}

@Composable
private fun DecisionChip(value: MatchDecision, selected: MatchDecision, onDecide: (MatchDecision) -> Unit) {
    SklyankaChip(
        text = value.label(),
        state = if (value == selected) ChipState.Selected else ChipState.Unselected,
        shape = SklyankaShapes.Pill,
        onClick = { onDecide(value) },
    )
}

/**
 * Дозировка, а не доза на приём: именно она вместе с названием образует совпадение (ПЕР-20),
 * и человек обязан видеть в карточке ровно то, по чему совпадение признано.
 *
 * Единица из закрытого списка (Р-45) печатается словом языка; незнакомая единица файла —
 * как есть: подменять чужую единицу своей нельзя, это медицинские данные (МГ-6).
 */
@Composable
private fun strengthLabel(match: MedicationMatch): String {
    val strength = match.file.strength?.stripTrailingZeros()?.toPlainString().orEmpty()
    val unit = strengthUnitText(match.file.strengthUnit.orEmpty())
    return stringResource(R.string.transfer_match_dosage_value, strength, unit).trim()
}

/** Слово единицы дозировки: закрытый список Р-45, незнакомый код — как есть (МГ-6). */
@Composable
private fun strengthUnitText(code: String): String = when (StrengthUnit.ofCode(code)) {
    StrengthUnit.MILLIGRAM -> stringResource(R.string.transfer_strength_unit_milligram)
    StrengthUnit.MICROGRAM -> stringResource(R.string.transfer_strength_unit_microgram)
    StrengthUnit.GRAM -> stringResource(R.string.transfer_strength_unit_gram)
    StrengthUnit.MILLILITRE -> stringResource(R.string.transfer_strength_unit_millilitre)
    StrengthUnit.INTERNATIONAL_UNIT -> stringResource(R.string.transfer_strength_unit_international_unit)
    StrengthUnit.PERCENT -> stringResource(R.string.transfer_strength_unit_percent)
    null -> code
}

internal fun matchTag(fileMedicationId: String): String = "transfer_match_$fileMedicationId"

const val TAG_MATCHES = "transfer_matches"
const val TAG_DROP_ALL = "transfer_drop_all"
const val TAG_APPLY_MERGE = "transfer_apply_merge"
