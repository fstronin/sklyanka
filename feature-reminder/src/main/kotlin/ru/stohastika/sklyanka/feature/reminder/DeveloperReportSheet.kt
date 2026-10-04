package ru.stohastika.sklyanka.feature.reminder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import ru.stohastika.sklyanka.ui.component.Banner
import ru.stohastika.sklyanka.ui.component.BannerKind
import ru.stohastika.sklyanka.ui.component.ButtonState
import ru.stohastika.sklyanka.ui.component.CheckGlyph
import ru.stohastika.sklyanka.ui.component.PrimaryButton
import ru.stohastika.sklyanka.ui.component.QuietButton
import ru.stohastika.sklyanka.ui.component.QuietButtonForm
import ru.stohastika.sklyanka.ui.component.QuietButtonKind
import ru.stohastika.sklyanka.ui.component.SklyankaBottomSheet
import ru.stohastika.sklyanka.ui.component.SklyankaTextField
import ru.stohastika.sklyanka.ui.component.TextFieldSurface
import ru.stohastika.sklyanka.ui.locale.interfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Шторка «Отчёт разработчику» (ОВ-126, ОВ-127, §4.18).
 *
 * Образец — шторка отчёта врачу (SKL-101): то же полотно, та же закреплённая шапка,
 * те же состояния содержимого. Комментарий необязателен; отправка идёт по явному
 * нажатию, а в первой версии — через заглушку без сети (ПМ-32).
 *
 * Закрытие («Отмена», системная «Назад», тап по затемнению) не даёт исхода и очищает
 * состояние: повторное открытие показывает пустое поле (AC-35, AC-40).
 */
@Composable
internal fun DeveloperReportSheet(
    state: DiagnosticsUiState,
    onCommentChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Последнее непустое содержимое держится на время ухода шторки: иначе она
    // схлопнулась бы в пустоту на анимации закрытия.
    var lastComment by remember { mutableStateOf("") }
    var lastOutcome by remember { mutableStateOf<DeveloperReportOutcome?>(null) }
    LaunchedEffect(state.reportOpen, state.reportComment, state.reportOutcome) {
        if (state.reportOpen) {
            lastComment = state.reportComment
            lastOutcome = state.reportOutcome
        }
    }
    val comment = if (state.reportOpen) state.reportComment else lastComment
    val outcome = if (state.reportOpen) state.reportOutcome else lastOutcome
    // Системная «Назад» закрывает шторку, а не уводит с «Проверки напоминаний».
    BackHandler(enabled = state.reportOpen, onBack = onDismiss)
    SklyankaBottomSheet(visible = state.reportOpen, onDismiss = onDismiss) {
        Column(modifier = Modifier.testTag(TAG_REPORT_SHEET)) {
            SheetHeader(success = outcome == DeveloperReportOutcome.SENT, onDismiss = onDismiss)
            if (outcome == DeveloperReportOutcome.SENT) {
                SuccessContent()
            } else {
                EditingContent(comment = comment, failed = outcome == DeveloperReportOutcome.FAILED, onCommentChange)
                ReportActions(failed = outcome == DeveloperReportOutcome.FAILED, onSend = onSend)
            }
        }
    }
}

@Composable
private fun SheetHeader(success: Boolean, onDismiss: () -> Unit) {
    val colors = SklyankaTheme.colors
    val quietLabel = stringResource(if (success) UiR.string.action_done else UiR.string.action_cancel)
    // Выход из успеха — подтверждающее действие шапки: «Готово» набрано `BodyDefaultStrong`
    // 15/600 `Accent`; «Отмена» остаётся ведущим приглушённым действием `BodyDefault`
    // 15/400 `TextMuted` (§4.9, ОВ-146). Иначе выход успеха читается как отмена.
    val quietKind = if (success) QuietButtonKind.Accent else QuietButtonKind.Muted
    // При крупном шрифте заголовок и действие не делят строку: доля заголовка становится
    // уже его самого длинного слова, и «разработчику» рвётся по буквам (AC-42, §6.3).
    // Тогда действие остаётся в своей строке, а заголовок занимает всю ширину следующей.
    val stacked = LocalDensity.current.fontScale > SklyankaSizes.SHEET_HEADER_STACK_FONT_SCALE
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SklyankaSpacing.Section)
                .heightIn(min = SklyankaSizes.MinTouchTarget),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetDismissButton(
                label = quietLabel,
                kind = quietKind,
                onDismiss = onDismiss,
                modifier = Modifier.testTag(TAG_REPORT_DISMISS),
            )
            if (!stacked) {
                SheetTitle(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = SklyankaSpacing.Small),
                )
                // Симметричный невидимый слот той же ширины, что видимая кнопка: название
                // остаётся по центру и при крупном шрифте не наезжает на неё.
                SheetDismissButton(
                    label = quietLabel,
                    kind = quietKind,
                    onDismiss = {},
                    enabled = false,
                    modifier = Modifier
                        .alpha(0f)
                        .clearAndSetSemantics {},
                )
            }
        }
        if (stacked) {
            SheetTitle(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SklyankaSpacing.Section)
                    .padding(bottom = SklyankaSpacing.Default),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(SklyankaSizes.DividerThickness)
                .background(colors.lineOnSurface),
        )
    }
}

/** Название шторки по центру: `BodyDefaultStrong` 15/600 `Text` — роль названия шапки (§4.9, ОВ-146). */
@Composable
private fun SheetTitle(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.diagnostics_report_title),
        style = SklyankaType.BodyDefaultStrong,
        color = SklyankaTheme.colors.text,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
private fun SheetDismissButton(
    label: String,
    kind: QuietButtonKind,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    QuietButton(
        text = label,
        onClick = onDismiss,
        kind = kind,
        form = QuietButtonForm.Header,
        state = if (enabled) ButtonState.Normal else ButtonState.Disabled,
        modifier = modifier,
    )
}

@Composable
private fun ColumnScope.EditingContent(comment: String, failed: Boolean, onCommentChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SklyankaSpacing.Section)
            .padding(top = SklyankaSpacing.Block),
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Block),
    ) {
        if (failed) {
            Banner(
                text = stringResource(R.string.diagnostics_report_failed),
                kind = BannerKind.Warning,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TAG_REPORT_FAILED),
            )
        }
        ExplanationPlate(text = stringResource(R.string.diagnostics_report_explanation))
        CommentField(comment, onCommentChange)
    }
}

/**
 * Плашка-объяснение на полотне шторки: `BodyDefault` 15 sp, `NeutralSoft`, `Large` 16,
 * поля 13/15 (ОВ-142, §4.18).
 *
 * Это не `Banner`: на белом полотне шторки объяснение — основной текст для чтения,
 * а `Banner` §4.6 задан для строки-шапки экрана и набран `Caption` 13 sp.
 */
@Composable
private fun ExplanationPlate(text: String) {
    Text(
        text = text,
        style = SklyankaType.BodyDefault,
        color = SklyankaTheme.colors.textMuted,
        modifier = Modifier
            .fillMaxWidth()
            .background(SklyankaTheme.colors.neutralSoft, SklyankaShapes.Large)
            .padding(
                horizontal = SklyankaSizes.ExplanationPlatePaddingHorizontal,
                vertical = SklyankaSizes.ExplanationPlatePaddingVertical,
            ),
    )
}

@Composable
private fun CommentField(comment: String, onCommentChange: (String) -> Unit) {
    val colors = SklyankaTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
        ) {
            Text(
                // Подпись поля — Overline 12 sp ВЕРХНИМ РЕГИСТРОМ, как в макете и в
                // надстрочниках секций (§4.4).
                text = stringResource(R.string.diagnostics_report_comment_label).uppercase(interfaceLocale()),
                style = SklyankaType.Overline,
                color = colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.diagnostics_report_comment_optional),
                style = SklyankaType.CaptionSmall,
                color = colors.textMuted,
            )
        }
        SklyankaTextField(
            value = comment,
            onValueChange = onCommentChange,
            hint = stringResource(R.string.diagnostics_report_comment_hint),
            multiline = true,
            // На белом полотне шторки заливка `Background`, а не `Surface`: иначе граница
            // `Line` на белом почти не видна (§4.4).
            surface = TextFieldSurface.Background,
            modifier = Modifier.testTag(TAG_REPORT_COMMENT),
        )
    }
}

@Composable
private fun ReportActions(failed: Boolean, onSend: () -> Unit) {
    val tag = if (failed) TAG_REPORT_RETRY else TAG_REPORT_SEND
    val label = stringResource(
        if (failed) R.string.diagnostics_report_retry else R.string.diagnostics_report_send,
    )
    PrimaryButton(
        text = label,
        onClick = onSend,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = SklyankaSpacing.Section,
                end = SklyankaSpacing.Section,
                top = SklyankaSpacing.Block,
                bottom = SklyankaSpacing.Screen,
            )
            .testTag(tag),
    )
}

/** Успех: содержимое заменяется целиком, выход — «Готово» в шапке (AC-32). */
@Composable
private fun SuccessContent() {
    val colors = SklyankaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Section)
            .padding(top = SklyankaSpacing.Screen, bottom = SklyankaSpacing.Screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        Text(
            text = stringResource(R.string.diagnostics_report_sent_title),
            style = SklyankaType.Subtitle,
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_REPORT_SENT),
        )
        Text(
            text = stringResource(R.string.diagnostics_report_sent_sub),
            style = SklyankaType.BodyDefault,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(SklyankaSpacing.Block))
        CheckGlyph(
            color = colors.taken,
            size = SklyankaSizes.BottomSheetSuccessMark,
            strokeWidth = SklyankaSizes.BottomSheetSuccessMarkStroke,
        )
    }
}
