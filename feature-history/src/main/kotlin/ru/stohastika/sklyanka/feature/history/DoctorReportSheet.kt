package ru.stohastika.sklyanka.feature.history

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import ru.stohastika.sklyanka.domain.history.ReportPreset
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.component.BannerKind
import ru.stohastika.sklyanka.ui.component.ButtonForm
import ru.stohastika.sklyanka.ui.component.CheckGlyph
import ru.stohastika.sklyanka.ui.component.ChipState
import ru.stohastika.sklyanka.ui.component.DateTimePickerDialog
import ru.stohastika.sklyanka.ui.component.ModalHeaderRow
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.QuietButton
import ru.stohastika.sklyanka.ui.component.QuietButtonForm
import ru.stohastika.sklyanka.ui.component.QuietButtonKind
import ru.stohastika.sklyanka.ui.component.SecondaryButton
import ru.stohastika.sklyanka.ui.component.SklyankaBottomSheet
import ru.stohastika.sklyanka.ui.component.SklyankaChip
import ru.stohastika.sklyanka.ui.component.StackingEqualButtons
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaMotion
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import ru.stohastika.sklyanka.ui.R as UiR2

/**
 * Отчёт врачу: выбор периода, состав, пояснение, передача системным обменом и сохранение.
 *
 * Настоящая нижняя шторка (§4.18, ОВ-125): полотно по высоте содержимого, затемнение,
 * закреплённые шапка и действия. Документ один — PDF, с экрана он не показывается;
 * вместо сводки шторка показывает **состав** «подпись — значение», чтобы человек видел,
 * что именно отдаёт, до нажатия «Передать».
 *
 * Успех — **отдельное состояние содержимого**: чипы, карточка, пояснение и действия
 * убираются, остаются фраза «Отчёт сформирован», исход словом и контурная галочка.
 * Отмена и ошибка — сообщением над составом, обычное содержимое остаётся, повтор возможен.
 *
 * Период выбирается чипами-пресетами; «Другой период» заменяет строку «Период» строками
 * «С»/«По» с существующим диалогом даты (§4.14).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DoctorReportSheet(report: ReportUi, outcome: ReportOutcome?, onIntent: (HistoryIntent) -> Unit) {
    var dialog by remember { mutableStateOf(ReportDateField.NONE) }
    Column(modifier = Modifier.testTag(TAG_REPORT_SHEET)) {
        SheetHeader(success = outcome?.isSuccess == true, onIntent = onIntent)
        if (outcome?.isSuccess == true) {
            SuccessContent(outcome)
        } else {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SklyankaSpacing.Section)
                    .padding(top = SklyankaSpacing.Block),
                verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Block),
            ) {
                outcome?.takeIf { !it.isSuccess }?.let { InlineOutcome(it) }
                PeriodChips(report.preset) { onIntent(HistoryIntent.ReportPresetChosen(it)) }
                CompositionCard(report) { field -> dialog = field }
                DataNote()
            }
            ReportActions(onIntent)
        }
    }
    ReportDateDialog(dialog, report, onIntent) { dialog = ReportDateField.NONE }
}

@Composable
private fun SheetHeader(success: Boolean, onIntent: (HistoryIntent) -> Unit) {
    val colors = SklyankaTheme.colors
    // Отмена — ведущее приглушённое действие шапки, «Готово» — замыкающее подтверждение:
    // «Отмена» набрана `BodyDefault` 15/400 `TextMuted`, «Готово» — `BodyDefaultStrong`
    // 15/600 `Accent` (§3.2, §7, ОВ-146). Иначе выход успеха читается как отмена.
    val action: @Composable () -> Unit = {
        QuietButton(
            text = stringResource(if (success) UiR2.string.action_done else UiR2.string.action_cancel),
            onClick = { onIntent(HistoryIntent.ReportDismissed) },
            kind = if (success) QuietButtonKind.Accent else QuietButtonKind.Muted,
            form = QuietButtonForm.Header,
        )
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        ModalHeaderRow(
            title = stringResource(R.string.history_report_title),
            // Слоты по общей сетке: на успехе ведущий слот пустеет, подтверждение встаёт
            // в замыкающий, а невидимой заглушки ради центровки названия нет (Р-53, V-15).
            // Пустой слот не заполняется ничем.
            leading = if (success) null else action,
            trailing = if (success) action else null,
            modifier = Modifier
                .padding(horizontal = SklyankaSpacing.Section)
                .heightIn(min = SklyankaSizes.MinTouchTarget),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SklyankaSizes.DividerThickness)
                .background(colors.line),
        )
    }
}

/** Отмена — нейтральный вид, ошибка — предупреждение: различает текст (НФТ-30, ОВ-125). */
@Composable
private fun InlineOutcome(outcome: ReportOutcome) {
    val warning = outcome == ReportOutcome.FAILED_SHARE || outcome == ReportOutcome.FAILED_SAVE
    val text = stringResource(
        when (outcome) {
            ReportOutcome.CANCELLED -> R.string.history_report_cancelled
            ReportOutcome.FAILED_SHARE -> R.string.history_report_share_failed
            ReportOutcome.FAILED_SAVE -> R.string.history_report_save_failed
            ReportOutcome.SHARED, ReportOutcome.SAVED -> R.string.history_report_success_title
        },
    )
    Banner(
        text = text,
        kind = if (warning) BannerKind.Warning else BannerKind.Neutral,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Чипы-пресеты периода: «Этот месяц» по умолчанию, варианты видны и переключаются одним касанием. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodChips(selected: ReportPreset, onChoose: (ReportPreset) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        ReportPreset.entries.forEach { preset ->
            SklyankaChip(
                text = stringResource(preset.label()),
                state = if (preset == selected) ChipState.Selected else ChipState.Unselected,
                onClick = { onChoose(preset) },
            )
        }
    }
}

private fun ReportPreset.label(): Int = when (this) {
    ReportPreset.THIS_MONTH -> R.string.history_report_period_this_month
    ReportPreset.PREVIOUS_MONTH -> R.string.history_report_period_previous_month
    ReportPreset.LAST_THREE_MONTHS -> R.string.history_report_period_last_three_months
    ReportPreset.CUSTOM -> R.string.history_report_period_custom
}

@Composable
private fun CompositionCard(report: ReportUi, onPickDate: (ReportDateField) -> Unit) {
    val colors = SklyankaTheme.colors
    val composition = report.composition
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SklyankaShapes.Card)
            .background(colors.surface)
            .border(SklyankaSizes.BorderThin, colors.lineOnSurface, SklyankaShapes.Card)
            .padding(horizontal = SklyankaSpacing.Block)
            .testTag(TAG_REPORT_CONTENT),
    ) {
        CompositionRow(
            label = stringResource(R.string.history_report_label_profile),
            testTag = TAG_REPORT_PROFILE,
            value = composition.profile,
        )
        CardDivider()
        if (report.preset == ReportPreset.CUSTOM) {
            CompositionRow(
                label = stringResource(R.string.history_report_label_start),
                testTag = TAG_REPORT_START,
                value = composition.from,
                onValueClick = { onPickDate(ReportDateField.START) },
                numbersInLiterata = true,
            )
            CardDivider()
            CompositionRow(
                label = stringResource(R.string.history_report_label_end),
                testTag = TAG_REPORT_END,
                value = composition.to,
                onValueClick = { onPickDate(ReportDateField.END) },
                numbersInLiterata = true,
            )
        } else {
            CompositionRow(
                label = stringResource(R.string.history_report_label_period),
                testTag = TAG_REPORT_PERIOD,
                value = composition.period,
                numbersInLiterata = true,
            )
        }
        CardDivider()
        CompositionTaken(composition)
        CardDivider()
        CompositionRow(
            label = stringResource(R.string.history_report_label_format),
            testTag = TAG_REPORT_FORMAT,
            value = composition.format,
        )
    }
}

/** Строка «Принято»: число приёмов или фраза пустого периода (ревизия 33-B). */
@Composable
private fun CompositionTaken(composition: ReportComposition) {
    val taken = composition.taken
    if (taken == null) {
        Text(
            text = stringResource(R.string.report_document_empty),
            style = SklyankaType.BodyDefaultStrong,
            color = SklyankaTheme.colors.text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SklyankaSpacing.Large)
                .testTag(TAG_REPORT_EMPTY),
        )
    } else {
        CompositionRow(
            label = stringResource(R.string.history_report_label_taken),
            testTag = TAG_REPORT_TAKEN,
            value = taken,
            numbersInLiterata = true,
        )
    }
}

@Composable
private fun CardDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SklyankaSizes.DividerThickness)
            .background(SklyankaTheme.colors.lineOnSurface),
    )
}

@Composable
private fun CompositionRow(
    label: String,
    testTag: String,
    value: String,
    onValueClick: (() -> Unit)? = null,
    numbersInLiterata: Boolean = false,
) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onValueClick == null) Modifier else Modifier.clickable(onClick = onValueClick))
            .padding(vertical = SklyankaSpacing.Large)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = SklyankaType.Caption, color = colors.textMuted)
        Spacer(modifier = Modifier.width(SklyankaSpacing.Medium))
        // Значение занимает остаток строки и переносится внутри своей колонки.
        // `StackingRow` здесь не годится: при нехватке ширины он уносил значение
        // под подпись, и смена длины периода (пресеты) скачком меняла высоту
        // карточки и всей шторки (SKL-101, дефект «скачет форма»).
        Text(
            text = if (numbersInLiterata) numberedValue(value) else AnnotatedString(value),
            style = SklyankaType.BodyDefaultStrong,
            color = colors.text,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        if (onValueClick != null) {
            Spacer(modifier = Modifier.width(SklyankaSpacing.Small))
            ChevronGlyph(color = colors.textTertiary, pointsLeft = false, size = SklyankaSizes.Chevron)
        }
    }
}

/**
 * Значение, где числа набраны Literata 600 (§2.3): цифры показателя читаются как
 * показатель, а слова — интерфейсным шрифтом. Одна строка остаётся одной строкой
 * для чтения (`Text` с `AnnotatedString`), а не набором отдельных надписей.
 */
private fun numberedValue(value: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    NUMBER.findAll(value).forEach { match ->
        append(value.substring(cursor, match.range.first))
        withStyle(SklyankaType.NumberSpan) {
            append(match.value)
        }
        cursor = match.range.last + 1
    }
    append(value.substring(cursor))
}

/** Число, при желании вместе со знаком «%»: «86», «100», «86 %» — цельные куски. */
private val NUMBER = Regex("""\d+(?:\s*%)?""")

/** Тихое пояснение о данных под карточкой: это пояснение к действию, а не сообщение об ошибке (НФТ-18). */
@Composable
private fun DataNote() {
    Text(
        text = stringResource(R.string.history_report_data_note),
        style = SklyankaType.Caption,
        color = SklyankaTheme.colors.textMuted,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_REPORT_DATA_NOTE),
    )
}

@Composable
private fun ReportActions(onIntent: (HistoryIntent) -> Unit) {
    StackingEqualButtons(
        first = {
            PrimaryButton(
                text = stringResource(R.string.history_report_share),
                onClick = { onIntent(HistoryIntent.ShareReport) },
                modifier = Modifier.testTag(TAG_REPORT_SHARE),
            )
        },
        second = {
            SecondaryButton(
                text = stringResource(R.string.history_report_save),
                onClick = { onIntent(HistoryIntent.SaveRequested) },
                form = ButtonForm.Screen,
                modifier = Modifier.testTag(TAG_REPORT_SAVE),
            )
        },
        gap = SklyankaSpacing.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Section, vertical = SklyankaSpacing.Block),
    )
}

/**
 * Состояние успеха: содержимое заменяется целиком.
 *
 * Появление композиции — 0,96 → 1 и прозрачность, 200 ms `EasingEnter` (§9.3, сценарий 10);
 * при системном уменьшении движения Compose гасит длительность сам, конечный кадр тот же.
 * Галочка декоративная: чтению с экрана не объявляется — исход называют слова.
 */
@Composable
private fun SuccessContent(outcome: ReportOutcome) {
    val colors = SklyankaTheme.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(SklyankaMotion.DurationShort, easing = SklyankaMotion.EasingEnter),
        label = "report-success",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Section)
            .padding(top = SklyankaSpacing.Screen, bottom = SklyankaSpacing.Screen)
            .graphicsLayer {
                val scale = SUCCESS_SCALE_FROM + (1f - SUCCESS_SCALE_FROM) * progress
                scaleX = scale
                scaleY = scale
                alpha = progress
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        Text(
            text = stringResource(R.string.history_report_success_title),
            style = SklyankaType.Subtitle,
            color = colors.text,
            modifier = Modifier
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(TAG_REPORT_SUCCESS),
        )
        Text(
            text = stringResource(
                if (outcome == ReportOutcome.SAVED) {
                    R.string.history_report_success_saved
                } else {
                    R.string.history_report_success_shared
                },
            ),
            style = SklyankaType.BodyDefault,
            color = colors.textMuted,
        )
        Spacer(modifier = Modifier.height(SklyankaSpacing.Block))
        CheckGlyph(
            color = colors.taken,
            size = SklyankaSizes.BottomSheetSuccessMark,
            strokeWidth = SklyankaSizes.BottomSheetSuccessMarkStroke,
            modifier = Modifier.testTag(TAG_REPORT_SUCCESS_MARK),
        )
    }
}

@Composable
private fun ReportDateDialog(
    field: ReportDateField,
    report: ReportUi,
    onIntent: (HistoryIntent) -> Unit,
    onClose: () -> Unit,
) {
    when (field) {
        ReportDateField.NONE -> Unit
        ReportDateField.START -> DateTimePickerDialog(
            title = stringResource(R.string.history_report_date_start_title),
            initial = report.customFrom,
            onDismiss = onClose,
            onConfirm = { date ->
                onIntent(HistoryIntent.ReportStartPicked(date))
                onClose()
            },
            // «С» — любая прошедшая дата; будущее недоступно, нижней границы нет (ОВ-125).
            notAfter = report.today,
        )
        ReportDateField.END -> DateTimePickerDialog(
            title = stringResource(R.string.history_report_date_end_title),
            initial = report.customTo,
            onDismiss = onClose,
            onConfirm = { date ->
                onIntent(HistoryIntent.ReportEndPicked(date))
                onClose()
            },
            // «По» не раньше «С» и не позже сегодня (ОВ-125).
            notBefore = report.customFrom,
            notAfter = report.today,
        )
    }
}

/** Поле диалога даты: `NONE` — диалог закрыт. */
internal enum class ReportDateField { NONE, START, END }

/** Обёртка шторки для экрана истории: слой, затемнение и движение живут в дизайн-системе. */
@Composable
internal fun ReportSheetHost(report: ReportUi?, outcome: ReportOutcome?, onIntent: (HistoryIntent) -> Unit) {
    var lastReport by remember { mutableStateOf<ReportUi?>(null) }
    var lastOutcome by remember { mutableStateOf<ReportOutcome?>(null) }
    // Последнее непустое содержимое держится на время ухода шторки: иначе она
    // схлопнулась бы в пустоту на анимации закрытия.
    LaunchedEffect(report, outcome) {
        if (report != null) {
            lastReport = report
            lastOutcome = outcome
        }
    }
    // Системная «Назад» закрывает шторку, а не уводит с «Истории» (ОВ-125): пока слой
    // открыт, возврат принадлежит ему; после закрытия работает обычный возврат экрана.
    BackHandler(enabled = report != null) { onIntent(HistoryIntent.ReportDismissed) }
    SklyankaBottomSheet(
        visible = report != null,
        onDismiss = { onIntent(HistoryIntent.ReportDismissed) },
    ) {
        lastReport?.let { shown -> DoctorReportSheet(shown, lastOutcome, onIntent) }
    }
}

const val TAG_REPORT_SHEET = "history_report_sheet"
const val TAG_REPORT_DATA_NOTE = "history_report_data_note"
const val TAG_REPORT_CONTENT = "history_report_content"
const val TAG_REPORT_PROFILE = "history_report_profile"
const val TAG_REPORT_PERIOD = "history_report_period"
const val TAG_REPORT_START = "history_report_start"
const val TAG_REPORT_END = "history_report_end"
const val TAG_REPORT_TAKEN = "history_report_taken"
const val TAG_REPORT_EMPTY = "history_report_empty"
const val TAG_REPORT_FORMAT = "history_report_format"
const val TAG_REPORT_SHARE = "history_report_share"
const val TAG_REPORT_SAVE = "history_report_save"
const val TAG_REPORT_SUCCESS = "history_report_success"
const val TAG_REPORT_SUCCESS_MARK = "history_report_success_mark"

private const val SUCCESS_SCALE_FROM = 0.96f
