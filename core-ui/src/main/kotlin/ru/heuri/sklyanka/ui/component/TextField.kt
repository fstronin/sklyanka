package ru.heuri.sklyanka.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.SklyankaShapes
import ru.heuri.sklyanka.ui.token.SklyankaSizes
import ru.heuri.sklyanka.ui.token.SklyankaSpacing
import ru.heuri.sklyanka.ui.token.SklyankaType

/**
 * Поле ввода.
 *
 * Размер текста — [SklyankaType.Body] 16 sp и ниже не опускается: EMUI при меньшем
 * кегле включает собственное масштабирование ввода, и поле начинает жить своей
 * жизнью.
 *
 * Доступность поля выражена состоянием [TextFieldState.Disabled], а не отдельным
 * булевым флагом: флаг рядом с перечислением позволяет собрать «недоступное поле в
 * фокусе», а перечисление — нет (TDD §6.1).
 *
 * [forcedState] в приложении передаётся только для недоступного поля; остальные
 * состояния выводятся из значения и фокуса. Каталог и превью задают его явно —
 * показать «в фокусе» без настоящего фокуса иначе нечем.
 */
@Composable
fun SklyankaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    unitLabel: String? = null,
    forcedState: TextFieldState? = null,
) {
    val colors = SklyankaTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val state = forcedState ?: when {
        focused -> TextFieldState.Focused
        value.isEmpty() -> TextFieldState.Empty
        else -> TextFieldState.Filled
    }
    val border = if (state == TextFieldState.Focused) colors.accent else colors.line
    val borderWidth = if (state == TextFieldState.Focused) SklyankaSizes.BorderAccent else SklyankaSizes.BorderThin
    Row(
        modifier = modifier
            .height(SklyankaSizes.FieldHeight)
            .background(colors.surface, SklyankaShapes.Medium)
            .border(borderWidth, border, SklyankaShapes.Medium)
            .padding(horizontal = SklyankaSizes.FieldPadding)
            .testTag(TAG_TEXT_FIELD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = state != TextFieldState.Disabled,
                interactionSource = interactionSource,
                textStyle = SklyankaType.Body.copy(
                    color = if (state == TextFieldState.Disabled) colors.textDisabled else colors.text,
                ),
                cursorBrush = SolidColor(colors.accent),
                singleLine = true,
                modifier = Modifier.testTag(TAG_TEXT_FIELD_INPUT),
            )
            if (value.isEmpty() && hint.isNotEmpty()) {
                Text(text = hint, style = SklyankaType.Body, color = colors.textDisabled)
            }
        }
        // Единица измерения — подпись, а не часть значения: она не редактируется и
        // не уезжает в домен вместе с числом.
        if (unitLabel != null) {
            Text(text = unitLabel, style = SklyankaType.Body, color = colors.textMuted)
        }
    }
}

/** Компактное числовое поле: счётчик приёмов в сутки, доза. */
@Composable
fun SklyankaNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = SklyankaTheme.colors
    Box(
        modifier = modifier
            .height(SklyankaSizes.CompactFieldHeight)
            .background(colors.background, SklyankaShapes.Compact)
            .border(SklyankaSizes.BorderThin, colors.line, SklyankaShapes.Compact)
            .padding(horizontal = SklyankaSizes.BannerPaddingVertical)
            .testTag(TAG_NUMBER_FIELD),
        contentAlignment = Alignment.Center,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            textStyle = SklyankaType.BodyStrong.copy(
                color = if (enabled) colors.text else colors.textDisabled,
            ),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
        )
    }
}

const val TAG_TEXT_FIELD = "text_field"
const val TAG_TEXT_FIELD_INPUT = "text_field_input"
const val TAG_NUMBER_FIELD = "number_field"
