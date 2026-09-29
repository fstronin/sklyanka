package ru.stohastika.sklyanka.feature.meds.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import ru.stohastika.sklyanka.domain.medication.DraftField
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.RowDivider
import ru.stohastika.sklyanka.feature.meds.SectionCard
import ru.stohastika.sklyanka.feature.meds.ValueRow
import ru.stohastika.sklyanka.feature.meds.fullDate
import ru.stohastika.sklyanka.feature.meds.message
import ru.stohastika.sklyanka.feature.meds.stockLabel
import ru.stohastika.sklyanka.ui.component.SklyankaNumberField
import ru.stohastika.sklyanka.ui.component.SklyankaSwitchRow
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * «Остаток в упаковке» и «Период приёма» — две карточки-группы в конце формы.
 *
 * Оба переключателя выражены отсутствием значения, а не отдельным флагом: пустой порог
 * и есть выключенное напоминание о покупке, пустая дата окончания и есть бессрочный
 * приём (ПР-6). Флаг рядом со значением позволил бы собрать «бессрочно, но до 30 сентября».
 */

/** «Остаток в упаковке»: сколько есть сейчас и порог напоминания о покупке. */
@Composable
internal fun StockBlock(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit, anchors: FormAnchors) {
    val colors = SklyankaTheme.colors
    val threshold = state.draft.stockThresholdDays
    FormBlock(stringResource(R.string.form_label_stock), DraftField.STOCK, anchors) {
        SectionCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SklyankaSpacing.Block, vertical = SklyankaSpacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
            ) {
                Text(
                    text = stringResource(R.string.form_stock_now),
                    style = SklyankaType.BodyDefault,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                SklyankaNumberField(
                    value = state.inputs.stockLeft,
                    onValueChange = { onIntent(MedFormIntent.StockChanged(it)) },
                    modifier = Modifier.width(SklyankaSizes.StockField).testTag(TAG_STOCK_FIELD),
                )
                Text(
                    text = state.applicability.doseUnit.stockLabel(),
                    style = SklyankaType.Label,
                    color = colors.textMuted,
                    modifier = Modifier.testTag(TAG_STOCK_UNIT),
                )
            }
            RowDivider(modifier = Modifier.padding(horizontal = SklyankaSpacing.Block))
            SklyankaSwitchRow(
                label = stringResource(R.string.form_stock_reminder),
                caption = pluralStringResource(
                    R.plurals.stock_threshold_caption,
                    threshold ?: DEFAULT_THRESHOLD_DAYS,
                    threshold ?: DEFAULT_THRESHOLD_DAYS,
                ),
                checked = state.applicability.stockThresholdEnabled,
                onCheckedChange = { onIntent(MedFormIntent.StockReminderToggled(it)) },
                modifier = Modifier.testTag(TAG_STOCK_REMINDER),
            )
        }
        state.violationOf(DraftField.STOCK)?.let { ViolationText(it.message()) }
    }
}

/**
 * «Период приёма»: начало, бессрочность и окончание.
 *
 * Строка «Окончание» приглушена и показывает «—», пока включена бессрочность; при
 * выключении она становится активной и требует даты — по умолчанию совпадающей
 * с датой начала, то есть курса на один день, который человек тут же и правит.
 */
@Composable
internal fun PeriodBlock(
    state: MedFormUiState,
    onIntent: (MedFormIntent) -> Unit,
    anchors: FormAnchors,
    onPickDate: (DateTarget) -> Unit,
) {
    val endDate = state.draft.endDate
    FormBlock(stringResource(R.string.form_label_period), DraftField.PERIOD, anchors) {
        SectionCard {
            ValueRow(
                label = stringResource(R.string.form_period_start),
                value = state.draft.startDate.fullDate(),
                onClick = { onPickDate(DateTarget.START) },
                modifier = Modifier.testTag(TAG_START_DATE),
            )
            RowDivider()
            SklyankaSwitchRow(
                label = stringResource(R.string.form_period_indefinite),
                caption = stringResource(R.string.form_period_indefinite_caption),
                checked = endDate == null,
                onCheckedChange = { onIntent(MedFormIntent.IndefiniteToggled(it)) },
                modifier = Modifier.testTag(TAG_INDEFINITE),
            )
            RowDivider()
            ValueRow(
                label = stringResource(R.string.form_period_end),
                value = endDate?.fullDate() ?: stringResource(R.string.form_period_end_empty),
                enabled = endDate != null,
                onClick = { onPickDate(DateTarget.END) },
                modifier = Modifier.testTag(TAG_END_DATE),
            )
        }
        state.violationOf(DraftField.PERIOD)?.let { ViolationText(it.message()) }
    }
}

/** Какую из двух дат периода выбирает человек. */
enum class DateTarget { START, END }

/** Подпись порога в макете: «Когда останется на 5 дней». */
private const val DEFAULT_THRESHOLD_DAYS = 5

const val TAG_STOCK_FIELD = "form_stock"
const val TAG_STOCK_UNIT = "form_stock_unit"
const val TAG_STOCK_REMINDER = "form_stock_reminder"
const val TAG_START_DATE = "form_start_date"
const val TAG_INDEFINITE = "form_indefinite"
const val TAG_END_DATE = "form_end_date"
