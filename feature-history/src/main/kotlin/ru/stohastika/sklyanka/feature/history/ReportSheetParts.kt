package ru.stohastika.sklyanka.feature.history

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import ru.stohastika.sklyanka.ui.component.SklyankaChip
import ru.stohastika.sklyanka.ui.component.StackingEqualButtons
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaMotion
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import java.time.LocalDate
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Общая сборка нижних шторок «Отчёта врачу» и «Экспорта истории» (SKL-119, §5.15–§5.16).
 *
 * Шторки — **одна сборка**, а не две похожие: у них общие шапка, чипы периода, карточка состава,
 * тихое пояснение, действия, состояние успеха и диалог даты. Собранные порознь, они разойдутся
 * на первой же правке — и разойдутся молча: обе выглядят правильными по отдельности.
 * Различаются шторки составом содержимого (строки карточки и заголовок успеха), а не сборкой.
 *
 * Признак нажатия — тот же, что у действий: одна форма из двух равных половин, при крупном
 * шрифте — столбик (§5.1). Галочка успеха декоративная: исход называют слова (НФТ-30).
 */

@Composable
internal fun SheetHeader(title: Int, success: Boolean, onDismiss: () -> Unit) {
    val colors = SklyankaTheme.colors
    // Отмена — ведущее приглушённое действие шапки, «Готово» — замыкающее подтверждение:
    // «Отмена» набрана `BodyDefault` 15/400 `TextMuted`, «Готово» — `BodyDefaultStrong`
    // 15/600 `Accent` (§3.2, §7, ОВ-146). Иначе выход успеха читается как отмена.
    val action: @Composable () -> Unit = {
        QuietButton(
            text = stringResource(if (success) UiR.string.action_done else UiR.string.action_cancel),
            onClick = onDismiss,
            kind = if (success) QuietButtonKind.Accent else QuietButtonKind.Muted,
            form = QuietButtonForm.Header,
        )
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        ModalHeaderRow(
            title = stringResource(title),
            // Слоты по общей сетке: на успехе ведущий слот пустеет, подтверждение встаёт
            // в замыкающий, а невидимой заглушки ради центровки названия нет (Р-53, V-15).
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

/** Сообщение исхода первым элементом шторки: отмена — нейтрально, ошибка — предупреждением. */
@Composable
internal fun InlineOutcome(text: String, warning: Boolean) {
    Banner(
        text = text,
        kind = if (warning) BannerKind.Warning else BannerKind.Neutral,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Чипы-пресеты периода: «Этот месяц» по умолчанию, варианты видны и переключаются одним касанием. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeriodChips(selected: ReportPreset, onChoose: (ReportPreset) -> Unit) {
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

internal fun ReportPreset.label(): Int = when (this) {
    ReportPreset.THIS_MONTH -> R.string.history_report_period_this_month
    ReportPreset.PREVIOUS_MONTH -> R.string.history_report_period_previous_month
    ReportPreset.LAST_THREE_MONTHS -> R.string.history_report_period_last_three_months
    ReportPreset.CUSTOM -> R.string.history_report_period_custom
}

/**
 * Строка карточки состава: подпись, значение и, если у строки есть переход, шеврон.
 *
 * Значение занимает остаток строки и переносится внутри своей колонки. `StackingRow` здесь
 * не годится: при нехватке ширины он уносил значение под подпись, и смена длины периода
 * (пресеты) скачком меняла высоту карточки и всей шторки (SKL-101, дефект «скачет форма»).
 */
@Composable
internal fun CompositionRow(
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
 * Разделитель строк карточки.
 *
 * Карточки набирают его внутри колонки со своим боковым полем, а карточка выбора состава —
 * строки с полем внутри: ей разделитель отдаётся с полем снаружи, чтобы линия начиналась
 * от колонки текста, а не от грани карточки (§5.15).
 */
@Composable
internal fun CardDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SklyankaSizes.DividerThickness)
            .background(SklyankaTheme.colors.lineOnSurface),
    )
}

/**
 * Значение, где числа набраны Literata 600 (§2.3): цифры показателя читаются как
 * показатель, а слова — интерфейсным шрифтом. Одна строка остаётся одной строкой
 * для чтения (`Text` с `AnnotatedString`), а не набором отдельных надписей.
 */
internal fun numberedValue(value: String): AnnotatedString = buildAnnotatedString {
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

/** Действия шторки одной формой: две равные половины, при крупном шрифте — столбик (§5.1). */
@Composable
internal fun SheetActions(shareTag: String, saveTag: String, onShare: () -> Unit, onSave: () -> Unit) {
    StackingEqualButtons(
        first = {
            PrimaryButton(
                text = stringResource(R.string.history_report_share),
                onClick = onShare,
                modifier = Modifier.testTag(shareTag),
            )
        },
        second = {
            SecondaryButton(
                text = stringResource(R.string.history_report_save),
                onClick = onSave,
                form = ButtonForm.Screen,
                modifier = Modifier.testTag(saveTag),
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
internal fun SuccessContent(title: Int, saved: Boolean, titleTag: String, markTag: String) {
    val colors = SklyankaTheme.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(SklyankaMotion.DurationShort, easing = SklyankaMotion.EasingEnter),
        label = "sheet-success",
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
            text = stringResource(title),
            style = SklyankaType.Subtitle,
            color = colors.text,
            modifier = Modifier
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag(titleTag),
        )
        Text(
            text = stringResource(
                if (saved) R.string.history_report_success_saved else R.string.history_report_success_shared,
            ),
            style = SklyankaType.BodyDefault,
            color = colors.textMuted,
        )
        Spacer(modifier = Modifier.height(SklyankaSpacing.Block))
        CheckGlyph(
            color = colors.taken,
            size = SklyankaSizes.BottomSheetSuccessMark,
            strokeWidth = SklyankaSizes.BottomSheetSuccessMarkStroke,
            modifier = Modifier.testTag(markTag),
        )
    }
}

/**
 * Диалог даты «Другого периода».
 *
 * «С» — любая прошедшая дата, будущее недоступно, нижней границы нет; «По» — не раньше «С»
 * и не позже сегодня (ОВ-125). Границы приходят значениями, а не из состояния шторки:
 * обе шторки берут их из своего состава, и второго их источника не заводится.
 */
@Composable
@Suppress("LongParameterList")
internal fun ReportDateDialog(
    field: ReportDateField,
    from: LocalDate,
    to: LocalDate,
    today: LocalDate,
    onStart: (LocalDate) -> Unit,
    onEnd: (LocalDate) -> Unit,
    onClose: () -> Unit,
) {
    when (field) {
        ReportDateField.NONE -> Unit
        ReportDateField.START -> DateTimePickerDialog(
            title = stringResource(R.string.history_report_date_start_title),
            initial = from,
            onDismiss = onClose,
            onConfirm = { date ->
                onStart(date)
                onClose()
            },
            notAfter = today,
        )
        ReportDateField.END -> DateTimePickerDialog(
            title = stringResource(R.string.history_report_date_end_title),
            initial = to,
            onDismiss = onClose,
            onConfirm = { date ->
                onEnd(date)
                onClose()
            },
            notBefore = from,
            notAfter = today,
        )
    }
}

private const val SUCCESS_SCALE_FROM = 0.96f
