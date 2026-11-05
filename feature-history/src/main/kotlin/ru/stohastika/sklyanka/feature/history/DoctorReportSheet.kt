package ru.stohastika.sklyanka.feature.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import ru.stohastika.sklyanka.domain.history.ReportPreset
import ru.stohastika.sklyanka.domain.history.ReportScope
import ru.stohastika.sklyanka.ui.component.CheckGlyph
import ru.stohastika.sklyanka.ui.component.SklyankaBottomSheet
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Отчёт врачу: выбор состава, период, состав словами, пояснение, передача и сохранение.
 *
 * Настоящая нижняя шторка (§4.18, ОВ-125): полотно по высоте содержимого, затемнение,
 * закреплённые шапка и действия. Документ один — PDF, с экрана он не показывается;
 * вместо сводки шторка показывает **состав** «подпись — значение», чтобы человек видел,
 * что именно отдаёт, до нажатия «Передать».
 *
 * **Состав выбирается здесь же** (Р-85 п. 1, §5.15): «Базовый» выбран по умолчанию (Р-36),
 * «Расширенный» доступен наравне с ним (Р-86). Выбор стоит до карточки состава: состав
 * выбирают раньше, чем смотрят на итог.
 *
 * Успех — **отдельное состояние содержимого**: чипы, выбор, карточка, пояснение и действия
 * убираются, остаются фраза «Отчёт сформирован», исход словом и контурная галочка.
 * Отмена и ошибка — сообщением над содержимым, обычное содержимое остаётся, повтор возможен.
 *
 * Период выбирается чипами-пресетами; «Другой период» заменяет строку «Период» строками
 * «С/По» с существующим диалогом даты (§4.14).
 */
@Composable
internal fun DoctorReportSheet(report: ReportUi, outcome: ReportOutcome?, onIntent: (HistoryIntent) -> Unit) {
    var dialog by remember { mutableStateOf(ReportDateField.NONE) }
    Column(modifier = Modifier.testTag(TAG_REPORT_SHEET)) {
        SheetHeader(
            title = R.string.history_report_title,
            success = outcome?.isSuccess == true,
            onDismiss = { onIntent(HistoryIntent.ReportDismissed) },
        )
        if (outcome?.isSuccess == true) {
            SuccessContent(
                title = R.string.history_report_success_title,
                saved = outcome == ReportOutcome.SAVED,
                titleTag = TAG_REPORT_SUCCESS,
                markTag = TAG_REPORT_SUCCESS_MARK,
            )
        } else {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SklyankaSpacing.Section)
                    .padding(top = SklyankaSpacing.Block),
                verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Block),
            ) {
                outcome?.takeIf { !it.isSuccess }?.let { OutcomeBanner(it) }
                PeriodChips(report.preset) { onIntent(HistoryIntent.ReportPresetChosen(it)) }
                // Доступ не украшает строку и не запирает её: он решает, что произойдёт
                // по нажатию, а это делает `ViewModel` — здесь уходит только выбор (П-7).
                CompositionChoice(scope = report.scope) { scope -> onIntent(HistoryIntent.ReportScopeChosen(scope)) }
                CompositionCard(report) { field -> dialog = field }
                DataNote()
            }
            SheetActions(
                shareTag = TAG_REPORT_SHARE,
                saveTag = TAG_REPORT_SAVE,
                onShare = { onIntent(HistoryIntent.ShareReport) },
                onSave = { onIntent(HistoryIntent.SaveRequested) },
            )
        }
    }
    ReportDateDialog(
        field = dialog,
        from = report.customFrom,
        to = report.customTo,
        today = report.today,
        onStart = { onIntent(HistoryIntent.ReportStartPicked(it)) },
        onEnd = { onIntent(HistoryIntent.ReportEndPicked(it)) },
        onClose = { dialog = ReportDateField.NONE },
    )
}

/**
 * Сообщение исхода отчёта: отмена — нейтрально, ошибка — предупреждением (НФТ-30, ОВ-125).
 *
 * Отмена системного диалога выглядит для человека так же, как отказ записи, и различает их
 * не цвет, а слова: у каждого исхода своя строка.
 */
@Composable
private fun OutcomeBanner(outcome: ReportOutcome) {
    InlineOutcome(text = stringResource(outcome.message()), warning = outcome.isWarning())
}

private fun ReportOutcome.message(): Int = when (this) {
    ReportOutcome.CANCELLED -> R.string.history_report_cancelled
    ReportOutcome.FAILED_SHARE -> R.string.history_report_share_failed
    ReportOutcome.FAILED_SAVE -> R.string.history_report_save_failed
    ReportOutcome.SHARED, ReportOutcome.SAVED -> R.string.history_report_success_title
}

/**
 * Выбор состава отчёта: надстрочник и карточка-группа из двух строк (§5.15).
 *
 * Под обеими подписями стоит пояснение состава: человек видит, что войдёт в документ,
 * **до** нажатия (П-7). Пояснение пишется словами состава, а не названием тарифа (Р-71).
 * Оба состава доступны наравне: выбора «есть доступ / нет доступа» в продукте нет.
 */
@Composable
private fun CompositionChoice(scope: ReportScope, onChoose: (ReportScope) -> Unit) {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        Text(
            text = stringResource(R.string.history_report_composition_section).uppercase(),
            style = SklyankaType.Overline,
            color = colors.textMuted,
            modifier = Modifier
                .padding(start = SklyankaSpacing.Micro)
                .testTag(TAG_REPORT_COMPOSITION_SECTION),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(SklyankaShapes.Card)
                .background(colors.surface)
                .border(SklyankaSizes.BorderThin, colors.lineOnSurface, SklyankaShapes.Card),
        ) {
            ChoiceRow(
                label = stringResource(R.string.history_report_composition_base),
                note = stringResource(R.string.history_report_composition_base_note),
                isSelected = scope == ReportScope.BASE,
                testTag = TAG_REPORT_COMPOSITION_BASE,
                onClick = { onChoose(ReportScope.BASE) },
            )
            CardDivider(modifier = Modifier.padding(horizontal = SklyankaSpacing.Block))
            ChoiceRow(
                label = stringResource(R.string.history_report_composition_extended),
                note = stringResource(R.string.history_report_composition_extended_note),
                isSelected = scope == ReportScope.EXTENDED,
                testTag = TAG_REPORT_COMPOSITION_EXTENDED,
                onClick = { onChoose(ReportScope.EXTENDED) },
            )
        }
    }
}

/**
 * Строка выбора состава: подпись, пояснение под ней, галочка у выбранной и семантика «выбрана».
 *
 * Строка собрана тем же способом, что выбор типа виджета (§5.12) и строки диалога списка (§5.10):
 * подпись набрана `BodyDefault`, у выбранной — `BodyDefaultStrong` цветом акцента (НФТ-30, §6.4).
 */
@Composable
private fun ChoiceRow(label: String, note: String, isSelected: Boolean, testTag: String, onClick: () -> Unit) {
    val colors = SklyankaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SklyankaSpacing.Block, vertical = SklyankaSpacing.Large)
            .semantics { selected = isSelected }
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Hair)) {
            Text(
                text = label,
                style = if (isSelected) SklyankaType.BodyDefaultStrong else SklyankaType.BodyDefault,
                color = if (isSelected) colors.accent else colors.text,
            )
            Text(text = note, style = SklyankaType.CaptionSmall, color = colors.textMuted)
        }
        // Место галочки зарезервировано всегда: галочка нарисована и у невыбранной строки,
        // просто без краски. Так колонка текста одна и та же в обоих состояниях, и пояснение
        // переносится одинаково независимо от выбора (§5.1 п. 4 нормы дизайна, находка V-19).
        Spacer(modifier = Modifier.width(SklyankaSpacing.Medium))
        // Галочка занимает своё место всегда и теряет только краску у невыбранной строки:
        // слот состояния остаётся зарезервированным, а новых красок и токенов не заводится
        // (НФТ-47 — цвета живут только в дизайн-системе).
        CheckGlyph(
            color = colors.accent,
            modifier = if (isSelected) Modifier else Modifier.alpha(INVISIBLE_GLYPH),
        )
    }
}

private fun ReportOutcome.isWarning(): Boolean = this == ReportOutcome.FAILED_SHARE || this == ReportOutcome.FAILED_SAVE

/** Тихое пояснение о данных под карточкой: пояснение к действию, а не сообщение об ошибке (НФТ-18). */
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

/**
 * Строка «Принято»: число приёмов или фраза пустого периода (ревизия 33-B).
 *
 * У расширенного состава карточка называет состав словами — и тем же числом: документ один,
 * и «принято» в нём то же самое. Отдельной строки «расширенный» в карточке не заводится:
 * выбранный состав уже отмечен галочкой в блоке выбора (Р-36 — состав документ не называет).
 */
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

/** Метки блока выбора состава (§5.15): на них опираются экранные проверки и эталоны. */
const val TAG_REPORT_COMPOSITION_SECTION = "history_report_composition_section"
const val TAG_REPORT_COMPOSITION_BASE = "history_report_composition_base"
const val TAG_REPORT_COMPOSITION_EXTENDED = "history_report_composition_extended"

/** Невидимая галочка невыбранной строки: место занято, краски нет (§5.1 п. 4). */
private const val INVISIBLE_GLYPH = 0f
