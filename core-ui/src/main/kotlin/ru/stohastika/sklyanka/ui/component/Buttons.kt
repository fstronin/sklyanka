package ru.stohastika.sklyanka.ui.component

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
import ru.stohastika.sklyanka.ui.modifier.minTouchTarget
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaType
import ru.stohastika.sklyanka.ui.token.pressedTint

/**
 * Основная кнопка: одно главное действие экрана.
 *
 * Фон — роль `accent`, то есть в чужом профиле автоматически цвет профиля (ПРФ-11).
 * Цвет параметром не принимается: параметр цвета — это тот же хардкод, только
 * переехавший на этаж выше (TDD §6.1).
 *
 * [kind] — вид, а не цвет: `Danger` берёт роль `danger`, которая в чужом профиле
 * не тонируется (ОВ-67). Нажатие затемняет базовый цвет вида; недоступное состояние
 * у обоих видов одинаковое — `progressTrack` + `textDisabled` (§4.3).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: ButtonState = ButtonState.Normal,
    kind: PrimaryButtonKind = PrimaryButtonKind.Primary,
    form: ButtonForm = ButtonForm.Screen,
) {
    val colors = SklyankaTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val showPressed = state == ButtonState.Pressed || pressed
    val base = when (kind) {
        PrimaryButtonKind.Primary -> colors.accent
        PrimaryButtonKind.Danger -> colors.danger
    }
    ButtonSurface(
        text = text,
        onClick = onClick,
        state = state,
        interactionSource = interactionSource,
        skin = ButtonSkin(
            background = when {
                !state.isActive -> colors.progressTrack
                showPressed -> pressedTint(base)
                else -> base
            },
            content = if (state.isActive) colors.onAccent else colors.textDisabled,
            border = null,
            shape = form.shape,
        ),
        modifier = modifier
            .heightIn(min = form.height)
            .testTag(TAG_PRIMARY_BUTTON),
    )
}

/**
 * Второстепенная кнопка: действие рядом с главным.
 *
 * Прозрачного фона нет вовсе — не нарисован; граница `lineInteractive` заметнее
 * обычной линии, иначе кнопка не читается как кнопка.
 *
 * [form] по умолчанию карточный: кнопка внутри карточки — 44 dp и радиус 13.
 * Экранная кнопка задаёт `ButtonForm.Screen` (52 dp, радиус 16) сама — дизайн-система
 * допускает обе формы (§4.3), а значение по умолчанию не трогает ни один существующий
 * вызов.
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: ButtonState = ButtonState.Normal,
    form: ButtonForm = ButtonForm.Card,
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
            shape = form.shape,
        ),
        modifier = modifier
            .heightIn(min = form.height)
            .testTag(TAG_SECONDARY_BUTTON),
    )
}

/**
 * Тихая кнопка: действие в шапке и внутри строк.
 *
 * Без фона и границы, поэтому нарисована она мельче нормы. Цель нажатия доводится
 * до 44 dp модификатором, а не увеличением рисунка (§3.3, критерий 6).
 *
 * [kind] — вид, а не цвет: `Muted` — вариант «Отмена» (то же действие приглушённым
 * текстом, чтобы рядом с «Готово» оно читалось как отказ), `Danger` — удаление
 * (цвет опасности, в чужом профиле не тонируется; ОВ-67, ОВ-124).
 */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: ButtonState = ButtonState.Normal,
    kind: QuietButtonKind = QuietButtonKind.Accent,
) {
    val colors = SklyankaTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val showPressed = state == ButtonState.Pressed || pressed
    val base = when (kind) {
        QuietButtonKind.Accent -> colors.accent
        QuietButtonKind.Muted -> colors.textMuted
        QuietButtonKind.Danger -> colors.danger
    }
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
