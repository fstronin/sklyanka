package ru.stohastika.sklyanka.feature.meds.form

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import ru.stohastika.sklyanka.domain.medication.CourseEndPreset
import ru.stohastika.sklyanka.domain.medication.DraftField
import ru.stohastika.sklyanka.domain.stock.StockThresholds
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.RowDivider
import ru.stohastika.sklyanka.feature.meds.SectionCard
import ru.stohastika.sklyanka.feature.meds.ValueRow
import ru.stohastika.sklyanka.feature.meds.fieldWord
import ru.stohastika.sklyanka.feature.meds.fullDate
import ru.stohastika.sklyanka.feature.meds.label
import ru.stohastika.sklyanka.feature.meds.message
import ru.stohastika.sklyanka.ui.component.ChipState
import ru.stohastika.sklyanka.ui.component.SklyankaChip
import ru.stohastika.sklyanka.ui.component.SklyankaNumberField
import ru.stohastika.sklyanka.ui.component.SklyankaSwitchRow
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import java.time.LocalDate

/**
 * «Остаток в упаковке» и «Период приёма» — две карточки-группы в конце формы.
 *
 * Оба переключателя выражены отсутствием значения, а не отдельным флагом: пустой порог
 * и есть выключенное напоминание о покупке, пустая дата окончания и есть бессрочный
 * приём (ПР-6). Флаг рядом со значением позволил бы собрать «бессрочно, но до 30 сентября».
 */

/**
 * «Остаток в упаковке»: сколько есть сейчас, размер упаковки и порог напоминания о покупке.
 *
 * Строка «Размер упаковки» стоит сразу под «Сейчас есть» и повторяет её: компактное
 * числовое поле с той же единицей. Порядок — решение ОВ-45: привычное первое число
 * не двигается, а новое читается вместе с ним. Поле необязательное: пустое значит
 * «размер не задан», автоматически из остатка не подставляется — подставленный
 * знаменатель выдавался бы за измеренный.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StockBlock(state: MedFormUiState, onIntent: (MedFormIntent) -> Unit, anchors: FormAnchors) {
    val threshold = state.draft.stockThresholdDays
    val packageViolation = state.violationOf(DraftField.PACKAGE_SIZE)
    FormBlock(stringResource(R.string.form_label_stock), DraftField.STOCK, anchors) {
        SectionCard {
            StockValueRow(
                label = stringResource(R.string.form_stock_now),
                value = state.inputs.stockLeft,
                onValueChange = { onIntent(MedFormIntent.StockChanged(it)) },
                unit = state.applicability.doseUnit.fieldWord(state.draft.stockLeft),
                tags = StockRowTags(field = TAG_STOCK_FIELD, unit = TAG_STOCK_UNIT),
            )
            StockValueRow(
                label = stringResource(R.string.form_package_size),
                value = state.inputs.packageSize,
                onValueChange = { onIntent(MedFormIntent.PackageSizeChanged(it)) },
                unit = state.applicability.doseUnit.fieldWord(state.draft.packageSize),
                tags = StockRowTags(field = TAG_PACKAGE_SIZE_FIELD, unit = TAG_PACKAGE_SIZE_UNIT),
                error = packageViolation != null,
            )
            Text(
                text = stringResource(R.string.form_package_size_hint),
                style = SklyankaType.CaptionSmall,
                color = SklyankaTheme.colors.textMuted,
                modifier = Modifier
                    .padding(horizontal = SklyankaSpacing.Block)
                    .padding(bottom = SklyankaSpacing.Medium)
                    .testTag(TAG_PACKAGE_SIZE_HINT),
            )
            RowDivider(modifier = Modifier.padding(horizontal = SklyankaSpacing.Block))
            SklyankaSwitchRow(
                label = stringResource(R.string.form_stock_reminder),
                caption = pluralStringResource(
                    R.plurals.stock_threshold_caption,
                    threshold ?: StockThresholds.DEFAULT_DAYS,
                    threshold ?: StockThresholds.DEFAULT_DAYS,
                ),
                checked = state.applicability.stockThresholdEnabled,
                onCheckedChange = { onIntent(MedFormIntent.StockReminderToggled(it)) },
                modifier = Modifier.testTag(TAG_STOCK_REMINDER),
            )
            if (state.applicability.stockThresholdEnabled) {
                ThresholdChips(threshold, onIntent)
            }
        }
        state.violationOf(DraftField.STOCK)?.let { ViolationText(it.message()) }
        packageViolation?.let { ViolationText(it.message()) }
    }
}

/**
 * Ряд порогов напоминания о покупке (SKL-74, ПМ-13).
 *
 * Состав — [StockThresholds.Options] из домена, а не перечисление по месту: изменение
 * набора не требует правки формы. Высота чипа — цель нажатия 44 dp (§4.2), [FlowRow]
 * переносит набор на узком экране и при крупном шрифте (§6.3).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThresholdChips(threshold: Int?, onIntent: (MedFormIntent) -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Block)
            .padding(bottom = SklyankaSpacing.Medium)
            .testTag(TAG_STOCK_THRESHOLD_OPTIONS),
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        StockThresholds.Options.forEach { days ->
            SklyankaChip(
                text = days.toString(),
                state = if (threshold == days) ChipState.Selected else ChipState.Unselected,
                onClick = { onIntent(MedFormIntent.StockThresholdChanged(days)) },
            )
        }
    }
}

/** Теги строки остатка: поле и его единица — их ищут экранные проверки. */
private data class StockRowTags(val field: String, val unit: String)

/** Строка «надпись — числовое поле — единица»: «Сейчас есть» и «Размер упаковки» устроены одинаково. */
@Composable
private fun StockValueRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    tags: StockRowTags,
    error: Boolean = false,
) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Block, vertical = SklyankaSpacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        Text(
            text = label,
            style = SklyankaType.BodyDefault,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        SklyankaNumberField(
            value = value,
            onValueChange = onValueChange,
            error = error,
            modifier = Modifier.width(SklyankaSizes.StockField).testTag(tags.field),
        )
        Text(
            text = unit,
            style = SklyankaType.Label,
            color = colors.textMuted,
            modifier = Modifier.testTag(tags.unit),
        )
    }
}

/**
 * «Период приёма»: начало, бессрочность и окончание.
 *
 * Строка «Окончание» приглушена и показывает «—», пока включена бессрочность; при
 * выключении она становится активной и требует даты — по умолчанию совпадающей
 * с датой начала, то есть курса на один день, который человек тут же и правит.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeriodBlock(
    state: MedFormUiState,
    onIntent: (MedFormIntent) -> Unit,
    anchors: FormAnchors,
    onPickDate: (DateTarget) -> Unit,
) {
    val endDate = state.draft.endDate
    val startDate = state.draft.startDate
    FormBlock(stringResource(R.string.form_label_period), DraftField.PERIOD, anchors) {
        SectionCard {
            ValueRow(
                label = stringResource(R.string.form_period_start),
                value = startDate.fullDate(),
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
            PresetChips(
                startDate = startDate,
                endDate = endDate,
                onPreset = { preset -> onIntent(MedFormIntent.CourseEndPresetChosen(preset)) },
                onOtherDate = { onPickDate(DateTarget.END) },
            )
        }
        state.violationOf(DraftField.PERIOD)?.let { ViolationText(it.message()) }
    }
}

/**
 * Ряд пресетов срока окончания под строкой «Окончание» (SKL-75, ПМ-12).
 *
 * Состав — [CourseEndPreset.Options] из домена; «Другая дата» идёт последней и
 * открывает тот же диалог SKL-69, что и строка «Окончание»: второй диалог не заводится.
 * Пока курс бессрочен ([endDate] пуст), пресеты приглушены и не нажимаются — состояния
 * совпадают с приглушённой строкой «Окончание». Выбранным считается пресет, чья дата
 * совпала с текущей датой окончания; произвольная дата не отмечает ни одного пресета.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetChips(
    startDate: LocalDate,
    endDate: LocalDate?,
    onPreset: (CourseEndPreset) -> Unit,
    onOtherDate: () -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Block)
            .padding(top = SklyankaSpacing.Small, bottom = SklyankaSpacing.Medium)
            .testTag(TAG_PERIOD_PRESETS),
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        CourseEndPreset.Options.forEach { preset ->
            SklyankaChip(
                text = preset.label(),
                state = when {
                    endDate == null -> ChipState.Disabled
                    endDate == preset.endDateFrom(startDate) -> ChipState.Selected
                    else -> ChipState.Unselected
                },
                onClick = { onPreset(preset) },
            )
        }
        SklyankaChip(
            text = stringResource(R.string.period_preset_other),
            state = if (endDate == null) ChipState.Disabled else ChipState.Unselected,
            onClick = onOtherDate,
        )
    }
}

/** Какую из двух дат периода выбирает человек. */
enum class DateTarget { START, END }

const val TAG_STOCK_FIELD = "form_stock"
const val TAG_STOCK_UNIT = "form_stock_unit"
const val TAG_STOCK_REMINDER = "form_stock_reminder"
const val TAG_STOCK_THRESHOLD_OPTIONS = "form_stock_thresholds"
const val TAG_PACKAGE_SIZE_FIELD = "form_package_size"
const val TAG_PACKAGE_SIZE_UNIT = "form_package_size_unit"
const val TAG_PACKAGE_SIZE_HINT = "form_package_size_hint"
const val TAG_START_DATE = "form_start_date"
const val TAG_INDEFINITE = "form_indefinite"
const val TAG_END_DATE = "form_end_date"
const val TAG_PERIOD_PRESETS = "form_period_presets"
