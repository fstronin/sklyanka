package ru.heuri.sklyanka.feature.meds.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import ru.heuri.sklyanka.domain.medication.CourseProgress
import ru.heuri.sklyanka.domain.medication.WeekAdherenceStrip
import ru.heuri.sklyanka.domain.stock.StockPanel
import ru.heuri.sklyanka.feature.meds.ClockGlyph
import ru.heuri.sklyanka.feature.meds.R
import ru.heuri.sklyanka.feature.meds.SectionLabel
import ru.heuri.sklyanka.feature.meds.dayAndMonth
import ru.heuri.sklyanka.feature.meds.formatted
import ru.heuri.sklyanka.feature.meds.stockLeftLabel
import ru.heuri.sklyanka.ui.component.AdherenceWeek
import ru.heuri.sklyanka.ui.component.ProgressPurpose
import ru.heuri.sklyanka.ui.component.SecondaryButton
import ru.heuri.sklyanka.ui.component.SklyankaProgressTrack
import ru.heuri.sklyanka.ui.component.SklyankaSwitchRow
import ru.heuri.sklyanka.ui.component.SklyankaTextField
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.SklyankaShapes
import ru.heuri.sklyanka.ui.token.SklyankaSizes
import ru.heuri.sklyanka.ui.token.SklyankaSpacing
import ru.heuri.sklyanka.ui.token.SklyankaType

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
@Composable
internal fun ScheduleBlock(schedule: ScheduleBlockUi, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    when (schedule) {
        is ScheduleBlockUi.Scheduled -> CardBlock(
            label = stringResource(R.string.card_section_schedule),
            modifier = modifier.testTag(TAG_SCHEDULE_BLOCK),
        ) {
            Text(text = schedule.summary(), style = SklyankaType.BodyStrong, color = colors.text)
            Row(horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
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
                text = stringResource(R.string.meds_period_open, course.start.dayAndMonth()),
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
 * Знаменателя «из 60» здесь нет: размера упаковки в модели данных не существует (ОВ-45),
 * и SKL-12 уже решил показывать «осталось 9 таблеток» до ответа заказчика. Полосы
 * заполнения по той же причине нет — делить остаток не на что, а полоса, нарисованная
 * от выдуманного числа, врёт убедительнее текста.
 *
 * Предупреждение «хватит на N дней» и тревожный цвет появляются и исчезают вместе:
 * цветом одним статус не передаётся (НФТ-33).
 */
@Composable
internal fun StockBlock(state: MedCardUiState, onIntent: (MedCardIntent) -> Unit, modifier: Modifier = Modifier) {
    val panel = state.stock as? StockPanel.Visible ?: return
    val colors = SklyankaTheme.colors
    CardBlock(
        label = stringResource(R.string.card_section_stock),
        modifier = modifier.testTag(TAG_STOCK_BLOCK),
        trailing = panel.captionDays?.let { days -> stockDaysText(days) },
        trailingAccent = panel.warning,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Medium),
        ) {
            Text(
                text = stockLeftLabel(panel.unitsLeft, state.stockUnit),
                style = SklyankaType.BodyStrong,
                color = if (panel.warning) colors.warning else colors.text,
                modifier = Modifier
                    .weight(1f)
                    .testTag(TAG_STOCK_VALUE),
            )
            SecondaryButton(
                text = stringResource(R.string.card_refill),
                onClick = { onIntent(MedCardIntent.RefillRequested) },
                modifier = Modifier.testTag(TAG_REFILL),
            )
        }
        state.refill?.let { refill -> RefillRow(refill, onIntent) }
    }
}

/**
 * Ввод нового значения остатка.
 *
 * «Пополнить» заменяет значение (ОВ-13, решено в SKL-12), поэтому спрашивается именно
 * «сколько осталось», а не «сколько добавить». Строка ввода открывается внутри блока, а
 * не отдельным экраном: макета у неё нет, и заводить экран, которого не рисовали, хуже,
 * чем показать поле там, где человек нажал кнопку.
 */
@Composable
private fun RefillRow(refill: RefillUi, onIntent: (MedCardIntent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag(TAG_REFILL_ROW),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        SklyankaTextField(
            value = refill.text,
            onValueChange = { text -> onIntent(MedCardIntent.RefillValueChanged(text)) },
            hint = stringResource(R.string.card_refill_hint),
            numeric = true,
            errorText = stringResource(R.string.error_stock_negative).takeIf { refill.invalid },
            modifier = Modifier.weight(1f),
        )
        SecondaryButton(
            text = stringResource(R.string.card_refill_confirm),
            onClick = { onIntent(MedCardIntent.RefillConfirmed) },
            modifier = Modifier.testTag(TAG_REFILL_CONFIRM),
        )
    }
}

/** Блок «Последние 7 дней» на общем компоненте дизайн-системы (общий с SKL-18). */
@Composable
internal fun WeekBlock(week: WeekAdherenceStrip, modifier: Modifier = Modifier) {
    CardBlock(
        label = stringResource(R.string.card_section_week),
        modifier = modifier.testTag(TAG_WEEK_BLOCK),
        trailing = weekCounter(week.taken, week.planned),
    ) {
        AdherenceWeek(days = week.days.map { cell -> cell.toAdherenceDay() })
    }
}

/**
 * Строка «Напоминать на часах».
 *
 * Нажимается вся строка, а не бегунок: цель нажатия 52×32 меньше нормы по высоте (НФТ-29).
 * У архивированного курса строка недоступна: напоминаний у него нет (ПР-11).
 */
@Composable
internal fun WatchRow(watch: WatchMirrorUi, onIntent: (MedCardIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = SklyankaTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, SklyankaShapes.Card)
            .border(SklyankaSizes.BorderThin, colors.lineOnSurface, SklyankaShapes.Card)
            .testTag(TAG_WATCH_ROW),
    ) {
        SklyankaSwitchRow(
            label = stringResource(R.string.card_watch_mirror),
            checked = watch.checked,
            enabled = watch.enabled,
            onCheckedChange = { enabled -> onIntent(MedCardIntent.WatchMirrorToggled(enabled)) },
        )
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
const val TAG_REFILL = "med_card_refill"
const val TAG_REFILL_ROW = "med_card_refill_row"
const val TAG_REFILL_CONFIRM = "med_card_refill_confirm"
const val TAG_WEEK_BLOCK = "med_card_week"
const val TAG_WATCH_ROW = "med_card_watch_row"
