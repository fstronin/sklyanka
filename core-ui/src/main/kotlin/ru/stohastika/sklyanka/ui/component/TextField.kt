package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaColors
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
import ru.stohastika.sklyanka.ui.token.SklyankaType

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
 * состояния выводятся из значения, фокуса и [errorText]. Каталог и превью задают его
 * явно — показать «в фокусе» без настоящего фокуса иначе нечем.
 *
 * [errorText] — пояснение под полем (§4.4, состояние «Ошибка»). Именно текстом, а не
 * одной лишь красной рамкой: цвет в одиночку значения не несёт (НФТ-33), и человеку
 * нужно знать, что именно не так, а не что «что-то не так».
 */
@Suppress("LongParameterList")
@Composable
fun SklyankaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String = "",
    unitLabel: String? = null,
    errorText: String? = null,
    numeric: Boolean = false,
    forcedState: TextFieldState? = null,
) {
    val colors = SklyankaTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val state = forcedState ?: when {
        // Ошибка старше фокуса: поле, в котором человек стоит курсором, остаётся
        // ошибочным, пока значение не исправлено, иначе подсказка исчезает ровно
        // в тот момент, когда человек до неё добрался.
        errorText != null -> TextFieldState.Error
        focused -> TextFieldState.Focused
        value.isEmpty() -> TextFieldState.Empty
        else -> TextFieldState.Filled
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SklyankaSpacing.Micro),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SklyankaSizes.FieldHeight)
                .background(colors.surface, SklyankaShapes.Medium)
                .border(borderWidthOf(state), borderOf(state, colors), SklyankaShapes.Medium)
                .padding(horizontal = SklyankaSizes.FieldPadding)
                .testTag(TAG_TEXT_FIELD),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Small),
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.CenterStart,
            ) {
                FieldInput(
                    value = value,
                    onValueChange = onValueChange,
                    state = state,
                    numeric = numeric,
                    interactionSource = interactionSource,
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
        if (errorText != null) {
            Text(
                text = errorText,
                style = SklyankaType.CaptionSmall,
                color = colors.warning,
                modifier = Modifier.testTag(TAG_TEXT_FIELD_ERROR),
            )
        }
    }
}

/**
 * Строка ввода внутри поля.
 *
 * Занимает всю высоту поля, а не высоту строки текста. Без этого нажимаемой была
 * строка в 20 dp внутри поля высотой 50 dp — то есть НФТ-29 нарушался ровно там, где
 * человек вводит дозу. Найдено переносом экранных проверок на JVM (SKL-24):
 * до него каталог компонентов не собирал ни один прогон.
 */
@Composable
private fun FieldInput(
    value: String,
    onValueChange: (String) -> Unit,
    state: TextFieldState,
    numeric: Boolean,
    interactionSource: MutableInteractionSource,
) {
    val colors = SklyankaTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = state != TextFieldState.Disabled,
        interactionSource = interactionSource,
        textStyle = SklyankaType.Body.copy(
            color = if (state == TextFieldState.Disabled) colors.textDisabled else colors.text,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
        ),
        cursorBrush = SolidColor(colors.accent),
        singleLine = true,
        modifier = Modifier.fillMaxSize().testTag(TAG_TEXT_FIELD_INPUT),
        decorationBox = { inner ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { inner() }
        },
    )
}

/**
 * Граница по состоянию.
 *
 * Ошибка окрашивается ролью `warning`, а не акцентом: акцент в чужом профиле подменяется
 * цветом профиля, и красная рамка стала бы там фиолетовой — предупреждение обязано
 * оставаться предупреждением в любом профиле.
 */
private fun borderOf(state: TextFieldState, colors: SklyankaColors): Color = when (state) {
    TextFieldState.Focused -> colors.accent
    TextFieldState.Error -> colors.warning
    else -> colors.line
}

/** Утолщение границы — тот же приём, что отличает фокус от покоя (§4.4). */
private fun borderWidthOf(state: TextFieldState) =
    if (state == TextFieldState.Focused || state == TextFieldState.Error) {
        SklyankaSizes.BorderAccent
    } else {
        SklyankaSizes.BorderThin
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
            // Нижняя граница, а не точная высота: макет рисует поле в 42 dp, а НФТ-29
            // требует 44 по обеим сторонам, и конфликт разрешается в пользу цели
            // нажатия, а не плотности вёрстки (см. `Modifier.minTouchTarget`, ОВ-17).
            .height(SklyankaSizes.CompactFieldHeight.coerceAtLeast(SklyankaSizes.MinTouchTarget))
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = SolidColor(colors.accent),
            singleLine = true,
            // Та же причина, что и у обычного поля: нажатие принимает всё поле.
            modifier = Modifier.fillMaxHeight(),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                    inner()
                }
            },
        )
    }
}

const val TAG_TEXT_FIELD = "text_field"
const val TAG_TEXT_FIELD_INPUT = "text_field_input"
const val TAG_TEXT_FIELD_ERROR = "text_field_error"
const val TAG_NUMBER_FIELD = "number_field"
