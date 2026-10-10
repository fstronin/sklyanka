package ru.stohastika.sklyanka.feature.meds.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import ru.stohastika.sklyanka.domain.medication.CourseProgress
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.medication.WeekAdherenceStrip
import ru.stohastika.sklyanka.domain.medication.WeekCounter
import ru.stohastika.sklyanka.domain.medication.counter
import ru.stohastika.sklyanka.domain.stock.StockPanel
import ru.stohastika.sklyanka.feature.meds.ClockGlyph
import ru.stohastika.sklyanka.feature.meds.R
import ru.stohastika.sklyanka.feature.meds.SectionLabel
import ru.stohastika.sklyanka.feature.meds.amountOf
import ru.stohastika.sklyanka.feature.meds.fieldWord
import ru.stohastika.sklyanka.feature.meds.formatted
import ru.stohastika.sklyanka.feature.meds.sinceDayAndMonth
import ru.stohastika.sklyanka.feature.meds.stockLeftLabel
import ru.stohastika.sklyanka.feature.meds.stockPackageLabel
import ru.stohastika.sklyanka.ui.component.AdherenceWeek
import ru.stohastika.sklyanka.ui.component.ProgressPurpose
import ru.stohastika.sklyanka.ui.component.QuietButton
import ru.stohastika.sklyanka.ui.component.QuietButtonKind
import ru.stohastika.sklyanka.ui.component.SecondaryButton
import ru.stohastika.sklyanka.ui.component.SklyankaProgressTrack
import ru.stohastika.sklyanka.ui.component.SklyankaTextField
import ru.stohastika.sklyanka.ui.component.StackingRow
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Блоки карточки лекарства (`design/screens/MedCard.dc.html`).
 *
 * Каждый блок — белая карточка с надписью раздела; второе значение справа от надписи
 * появляется только там, где оно есть в макете.
 */

/** Общая оболочка блока: поверхность, рамка, радиус карточки. */
@Composable
private fun CardBlock(
    label: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    trailingAccent: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Card)
            .border(SklyankaSizes.BorderThin, colors.lineOnSurface, SklyankaShapes.Card)
            .padding(SklyankaSpacing.Block),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
        ) {
            SectionLabel(text = label, modifier = Modifier.weight(1f))
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = if (trailingAccent) SklyankaType.CaptionStrong else SklyankaType.CaptionSmall,
                    color = if (trailingAccent) colors.warning else colors.textMuted,
                    modifier = Modifier.testTag(TAG_BLOCK_TRAILING),
                )
            }
        }
        content()
    }
}

/** Блок «Расписание» либо ограничение «не чаще N раз в сутки» вместо него (ПР-7, ПР-8). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleBlock(schedule: ScheduleBlockUi, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    when (schedule) {
        is ScheduleBlockUi.Scheduled -> CardBlock(
            label = stringResource(R.string.card_section_schedule),
            modifier = modifier.testTag(TAG_SCHEDULE_BLOCK),
        ) {
            Text(text = schedule.summary(), style = SklyankaType.BodyStrong, color = colors.text)
            // Чипы переносятся целыми: при восьми приёмах в сутки ряд отдавал последним
            // чипам нулевую ширину, и «16:01» разворачивался по буквам (SKL-59).
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
                verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
            ) {
                schedule.times.forEach { time -> TimeChip(time.formatted()) }
            }
        }

        is ScheduleBlockUi.AsNeeded -> CardBlock(
            label = stringResource(R.string.card_section_limit),
            modifier = modifier.testTag(TAG_LIMIT_BLOCK),
        ) {
            Text(
                text = pluralStringResource(R.plurals.max_per_day, schedule.maxPerDay, schedule.maxPerDay),
                style = SklyankaType.BodyStrong,
                color = colors.text,
            )
        }
    }
}

/** Чип времени приёма с иконкой часов (`MedCard.dc.html`). */
@Composable
private fun TimeChip(text: String) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier
            .background(colors.neutralSoft, SklyankaShapes.Pill)
            .padding(horizontal = SklyankaSpacing.Medium, vertical = SklyankaSpacing.Small)
            .testTag(TAG_TIME_CHIP),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        ClockGlyph(color = colors.textTertiary)
        Text(text = text, style = SklyankaType.LabelStrong, color = colors.text)
    }
}

/**
 * Блок «Курс».
 *
 * У бессрочного курса нет ни полосы, ни числа оставшихся дней (ПР-6): доля пройденного
 * у него не равна нулю, она не определена, и полоса на нуле процентов сообщала бы
 * неправду.
 */
@Composable
internal fun CourseBlock(course: CourseProgress, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    when (course) {
        is CourseProgress.Open -> CardBlock(
            label = stringResource(R.string.card_section_course),
            modifier = modifier.testTag(TAG_COURSE_BLOCK),
        ) {
            Text(
                text = stringResource(R.string.meds_period_open, course.start.sinceDayAndMonth()),
                style = SklyankaType.BodyStrong,
                color = colors.text,
            )
        }

        is CourseProgress.Bounded -> CardBlock(
            label = stringResource(R.string.card_section_course),
            modifier = modifier.testTag(TAG_COURSE_BLOCK),
            trailing = courseTrailing(course),
        ) {
            Text(
                text = coursePeriod(course.start, course.end),
                style = SklyankaType.BodyStrong,
                color = colors.text,
            )
            SklyankaProgressTrack(
                progress = course.fraction,
                description = courseTrailing(course),
                purpose = ProgressPurpose.Course,
                modifier = Modifier.testTag(TAG_COURSE_BAR),
            )
        }
    }
}

/**
 * Надпись справа от заголовка «Курс».
 *
 * «осталось 17 дней» в середине курса; в последний день остатка дней нет — там сказано
 * «последний день», потому что «осталось 0 дней» не говорит ничего (ЭКР-6).
 */
@Composable
private fun courseTrailing(course: CourseProgress.Bounded): String = when {
    course.isOver -> stringResource(R.string.card_course_over)
    course.isLastDay -> stringResource(R.string.card_course_last_day)
    else -> daysLeftText(course.daysLeft)
}

/**
 * Блок «Остаток».
 *
 * Два вида строки (ОВ-45). Оба числа заданы и N ≤ M — «9 таблеток из 60» и полоса доли
 * N/M: высота 8 dp, дорожка `ProgressTrack`, заполнение акцентом, а при сработавшем
 * пороге покупки — тревожным (`ProfileForeign`). Полоса всегда сопровождается тем же
 * числом текстом, в том числе для чтения с экрана (НФТ-30, НФТ-33). Размер не задан
 * (старые данные, файл без поля) или остаток больше упаковки — прежнее «осталось N»
 * без полосы и без сообщений: знаменатель не выдумывается.
 *
 * Предупреждение «хватит на N дней» и тревожный цвет появляются и исчезают вместе:
 * цветом одним статус не передаётся (НФТ-33).
 */
@Composable
internal fun StockBlock(state: MedCardUiState, onIntent: (MedCardIntent) -> Unit, modifier: Modifier = Modifier) {
    val panel = state.stock as? StockPanel.Visible ?: return
    val colors = SklyankaTheme.colors
    val fraction = panel.packageFraction
    val packageShare = fraction?.let { share ->
        // Доля существует только при заданном размере — это гарантирует packageFraction.
        val label = stockPackageLabel(panel.unitsLeft, requireNotNull(panel.packageSize), state.stockUnit)
        share to label
    }
    CardBlock(
        label = stringResource(R.string.card_section_stock),
        modifier = modifier.testTag(TAG_STOCK_BLOCK),
        trailing = panel.captionDays?.let { days -> stockDaysText(days) },
        trailingAccent = panel.warning,
    ) {
        // Ряд «значение — действие»: при нехватке ширины кнопка уходит под текст,
        // а значение получает всю ширину (§6.3). Взвешенный `Text` делил ширину,
        // но не знал, что делать, когда её меньше самого длинного слова: «капсул»
        // отрывался от числа (SKL-59, дефект пилота).
        StackingRow(
            leading = {},
            text = {
                Text(
                    text = packageShare?.second ?: stockLeftLabel(panel.unitsLeft, state.stockUnit),
                    style = SklyankaType.BodyStrong,
                    color = if (panel.warning) colors.warning else colors.text,
                    modifier = Modifier.testTag(TAG_STOCK_VALUE),
                )
            },
            action = {
                SecondaryButton(
                    text = stringResource(R.string.card_refill),
                    onClick = { onIntent(MedCardIntent.RefillRequested) },
                    modifier = Modifier.testTag(TAG_REFILL),
                )
            },
            gap = SklyankaSpacing.Medium,
            minTextWidth = SklyankaSizes.MinTextColumn,
            stackedActionFullWidth = true,
        )
        packageShare?.let { (share, label) ->
            SklyankaProgressTrack(
                progress = share,
                description = label,
                purpose = if (panel.warning) ProgressPurpose.StockCritical else ProgressPurpose.Stock,
                modifier = Modifier.testTag(TAG_STOCK_BAR),
            )
        }
        state.refill?.let { refill -> RefillRow(refill, state.stockUnit, onIntent) }
    }
}

/**
 * Ввод нового значения остатка.
 *
 * «Пополнить» заменяет значение (ОВ-13, решено в SKL-12), поэтому спрашивается именно
 * «сколько осталось», а не «сколько добавить». Строка открывается внутри блока,
 * а не отдельным экраном: макета у неё нет, и заводить экран, которого не рисовали,
 * хуже, чем показать поле там, где человек нажал кнопку. Единица — та же, что у строки
 * остатка; действия — «Сохранить» (второстепенная) и тихая «Отмена».
 *
 * Размер упаковки строка не меняет: он свойство упаковки, а не остатка, и правится
 * только формой «Изменить».
 */
@Composable
private fun RefillRow(refill: RefillUi, unit: DoseUnit, onIntent: (MedCardIntent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(TAG_REFILL_ROW),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        SklyankaTextField(
            value = refill.text,
            onValueChange = { text -> onIntent(MedCardIntent.RefillValueChanged(text)) },
            hint = stringResource(R.string.card_refill_hint),
            unitLabel = unit.fieldWord(amountOf(refill.text)),
            numeric = true,
            errorText = stringResource(R.string.error_stock_negative).takeIf { refill.invalid },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            QuietButton(
                text = stringResource(UiR.string.action_cancel),
                onClick = { onIntent(MedCardIntent.RefillCancelled) },
                kind = QuietButtonKind.Muted,
                modifier = Modifier.testTag(TAG_REFILL_CANCEL),
            )
            SecondaryButton(
                text = stringResource(R.string.card_refill_confirm),
                onClick = { onIntent(MedCardIntent.RefillConfirmed) },
                modifier = Modifier.testTag(TAG_REFILL_CONFIRM),
            )
        }
    }
}

/** Блок «Последние 7 дней» на общем компоненте дизайн-системы (общий с SKL-18). */
@Composable
internal fun WeekBlock(week: WeekAdherenceStrip, modifier: Modifier = Modifier) {
    // Нулевой знаменатель — доля не рисуется: фраза называет факт, а не «0 из 0» (ПМ-15 п. 2).
    val trailing = when (val counter = week.counter()) {
        is WeekCounter.Fraction -> weekCounter(counter.taken, counter.planned)
        WeekCounter.NoRecords -> stringResource(R.string.card_week_empty)
    }
    CardBlock(
        label = stringResource(R.string.card_section_week),
        modifier = modifier.testTag(TAG_WEEK_BLOCK),
        trailing = trailing,
    ) {
        AdherenceWeek(days = week.days.map { cell -> cell.toAdherenceDay() })
    }
}

const val TAG_BLOCK_TRAILING = "med_card_block_trailing"
const val TAG_SCHEDULE_BLOCK = "med_card_schedule"
const val TAG_LIMIT_BLOCK = "med_card_limit"
const val TAG_TIME_CHIP = "med_card_time_chip"
const val TAG_COURSE_BLOCK = "med_card_course"
const val TAG_COURSE_BAR = "med_card_course_bar"
const val TAG_STOCK_BLOCK = "med_card_stock"
const val TAG_STOCK_VALUE = "med_card_stock_value"
const val TAG_STOCK_BAR = "med_card_stock_bar"
const val TAG_REFILL = "med_card_refill"
const val TAG_REFILL_ROW = "med_card_refill_row"
const val TAG_REFILL_CONFIRM = "med_card_refill_confirm"
const val TAG_REFILL_CANCEL = "med_card_refill_cancel"
const val TAG_WEEK_BLOCK = "med_card_week"
