package ru.heuri.sklyanka.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import ru.heuri.sklyanka.ui.modifier.minTouchTarget
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.SklyankaShapes
import ru.heuri.sklyanka.ui.token.SklyankaSizes
import ru.heuri.sklyanka.ui.token.SklyankaType
import ru.heuri.sklyanka.ui.token.pressedTint

/**
 * Основная кнопка: одно главное действие экрана.
 *
 * Фон — роль `accent`, то есть в чужом профиле автоматически цвет профиля (ПРФ-11).
 * Цвет параметром не принимается: параметр цвета — это тот же хардкод, только
 * переехавший на этаж выше (TDD §6.1).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: ButtonState = ButtonState.Normal,
) {
    val colors = SklyankaTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val showPressed = state == ButtonState.Pressed || pressed
    ButtonSurface(
        text = text,
        onClick = onClick,
        state = state,
        interactionSource = interactionSource,
        skin = ButtonSkin(
            background = when {
                !state.isActive -> colors.progressTrack
                showPressed -> pressedTint(colors.accent)
                else -> colors.accent
            },
            content = if (state.isActive) colors.surface else colors.textDisabled,
            border = null,
            shape = SklyankaShapes.Large,
        ),
        modifier = modifier
            .heightIn(min = SklyankaSizes.PrimaryButtonHeight)
            .testTag(TAG_PRIMARY_BUTTON),
    )
}

/**
 * Второстепенная кнопка: действие рядом с главным.
 *
 * Прозрачного фона нет вовсе — не нарисован; граница `lineInteractive` заметнее
 * обычной линии, иначе кнопка не читается как кнопка.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: ButtonState = ButtonState.Normal,
) {
    val colors = SklyankaTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val showPressed = state == ButtonState.Pressed || pressed
    ButtonSurface(
        text = text,
        onClick = onClick,
        state = state,
        interactionSource = interactionSource,
        skin = ButtonSkin(
            background = if (showPressed && state.isActive) colors.neutralSoft else null,
            content = if (state.isActive) colors.text else colors.textDisabled,
            border = if (state.isActive) colors.lineInteractive else colors.line,
            shape = SklyankaShapes.Button,
        ),
        modifier = modifier
            .heightIn(min = SklyankaSizes.SecondaryButtonHeight)
            .testTag(TAG_SECONDARY_BUTTON),
    )
}

/**
 * Тихая кнопка: действие в шапке и внутри строк.
 *
 * Без фона и границы, поэтому нарисована она мельче нормы. Цель нажатия доводится
 * до 44 dp модификатором, а не увеличением рисунка (§3.3, критерий 6).
 *
 * [muted] — вариант «Отмена»: то же действие приглушённым текстом, чтобы рядом с
 * «Готово» оно читалось как отказ, а не как второе равноправное действие.
 */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: ButtonState = ButtonState.Normal,
    muted: Boolean = false,
) {
    val colors = SklyankaTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val showPressed = state == ButtonState.Pressed || pressed
    val base = if (muted) colors.textMuted else colors.accent
    Box(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                // Рябь Material здесь не нужна: нажатие показано цветом подписи,
                // а рябь на прозрачном фоне в этой вёрстке выглядит грязью.
                indication = null,
                enabled = state.isInteractive,
                onClick = onClick,
            )
            .minTouchTarget()
            .padding(horizontal = SklyankaSizes.QuietButtonPadding)
            .testTag(TAG_QUIET_BUTTON),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = SklyankaType.LabelStrong,
            color = when {
                !state.isActive -> colors.textDisabled
                showPressed -> pressedTint(base)
                else -> base
            },
        )
    }
}

/** Общая оболочка кнопок с фоном: один способ собрать габарит, подпись и границу. */
@Composable
private fun ButtonSurface(
    text: String,
    onClick: () -> Unit,
    state: ButtonState,
    interactionSource: MutableInteractionSource,
    skin: ButtonSkin,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = SklyankaSizes.MinTouchTarget)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = state.isInteractive,
                onClick = onClick,
            )
            .then(
                if (skin.background == null) Modifier else Modifier.background(skin.background, skin.shape),
            )
            .then(
                if (skin.border == null) {
                    Modifier
                } else {
                    Modifier.border(SklyankaSizes.BorderThin, skin.border, skin.shape)
                },
            )
            .padding(horizontal = SklyankaSizes.ButtonPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = SklyankaType.LabelStrong,
            color = skin.content,
            textAlign = TextAlign.Center,
        )
    }
}

const val TAG_PRIMARY_BUTTON = "primary_button"
const val TAG_SECONDARY_BUTTON = "secondary_button"
const val TAG_QUIET_BUTTON = "quiet_button"
