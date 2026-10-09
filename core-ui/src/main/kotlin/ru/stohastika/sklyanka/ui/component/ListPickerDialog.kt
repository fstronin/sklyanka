package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.modifier.minTouchTarget
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Диалог выбора одного значения из заданного набора (SKL-78, §4.17, ОВ-98).
 *
 * Вид повторяет диалог даты и времени (§4.14, `DateTimePickerDialog`), чтобы диалоги
 * системы выглядели одинаково: та же поверхность с рамкой и радиусом `Large`, без
 * Material-тени. Набор и подписи — параметры: состав вариантов и его тексты знает
 * вызывающий, компонент отвечает только за вид. Значение обобщено, чтобы компонент
 * не знал ни про минуты, ни про пороги покупки.
 *
 * Выбор применяется сразу и закрывает диалог (это делает вызывающий в [onSelect]);
 * «Отмена», системная «Назад» и тап вне окна приходят в [onDismiss] без изменений.
 * Выбранная строка отмечена не одним цветом (НФТ-30): начертанием 600, галочкой
 * и состоянием `selected` для чтения с экрана. Если сохранённого значения нет в наборе
 * (его мог принести файл переноса), не отмечена ни одна строка — ложной отметки нет.
 */
@Composable
@Suppress("LongParameterList")
public fun <T> ListPickerDialog(
    title: String,
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SklyankaTheme.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // То же затемнение, что у остальных модальных слоёв (§2.3): одна роль,
            // одна непрозрачность — своя подложка у диалога выбора значения разошлась бы
            // с диалогом времени на первой же правке.
            AppDialogScrim(onDismiss = onDismiss)
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(SklyankaSpacing.Screen)
                    .testTag(TAG_LIST_PICKER),
                shape = SklyankaShapes.Large,
                color = colors.surface,
                border = BorderStroke(SklyankaSizes.BorderThin, colors.lineOnSurface),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = SklyankaSpacing.Block),
                    verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
                ) {
                    Text(
                        text = title,
                        style = SklyankaType.BodyDefaultStrong,
                        color = colors.text,
                        modifier = Modifier.padding(horizontal = SklyankaSpacing.Block),
                    )
                    options.forEachIndexed { index, option ->
                        if (index > 0) ListDivider()
                        OptionRow(
                            text = label(option),
                            selected = option == selected,
                            onClick = { onSelect(option) },
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SklyankaSpacing.Block),
                        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        QuietButton(
                            text = stringResource(R.string.action_cancel),
                            onClick = onDismiss,
                            kind = QuietButtonKind.Muted,
                            form = QuietButtonForm.Header,
                        )
                    }
                }
            }
        }
    }
}

/** Строка варианта: ≥44 dp, выбранная — акцентом, начертанием 600 и галочкой. */
@Composable
private fun OptionRow(text: String, selected: Boolean, onClick: () -> Unit) {
    val colors = SklyankaTheme.colors
    val selectedLabel = stringResource(R.string.cd_list_picker_selected)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .minTouchTarget()
            .semantics { this.selected = selected }
            .padding(horizontal = SklyankaSpacing.Block, vertical = SklyankaSpacing.Medium)
            .testTag(TAG_LIST_PICKER_OPTION),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text,
            style = if (selected) SklyankaType.BodyDefaultStrong else SklyankaType.BodyDefault,
            color = if (selected) colors.accent else colors.text,
        )
        if (selected) {
            CheckGlyph(
                color = colors.accent,
                modifier = Modifier
                    .semantics { contentDescription = selectedLabel }
                    .testTag(TAG_LIST_PICKER_SELECTED),
            )
        }
    }
}

/** Разделитель между вариантами: тот же токен линии, что и в списках. */
@Composable
private fun ListDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SklyankaSpacing.Block)
            .height(SklyankaSizes.DividerThickness)
            .background(SklyankaTheme.colors.lineOnSurface),
    )
}

/** Теги диалога: экранная проверка отличает диалог приложения от системного. */
public const val TAG_LIST_PICKER: String = "list_picker"
public const val TAG_LIST_PICKER_OPTION: String = "list_picker_option"
public const val TAG_LIST_PICKER_SELECTED: String = "list_picker_selected"
