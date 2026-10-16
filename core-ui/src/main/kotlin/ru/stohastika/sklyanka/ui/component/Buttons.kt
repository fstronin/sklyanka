package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import ru.stohastika.sklyanka.ui.modifier.minTouchTarget
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaColors
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaSpacing
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
 *
 * [form] несёт высоту, радиус и боковое поле подписи: тёмная форма — 58 dp, радиус
 * `Card` 18 и поле 0 (§5.1, ОВ-146).
 *
 * Подпись с ведущим глифом — редкий случай конкретного места, а не общий: её принимает
 * перегрузка с [ButtonLabel], а простой вызов знает только слово. Смысл несёт слово,
 * поэтому глиф чтению с экрана не объявляется; цвет глифа выбирает место, галочка
 * тёмного напоминания — `onAccent`.
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
    PrimaryButton(
        label = ButtonLabel(text),
        onClick = onClick,
        modifier = modifier,
        state = state,
        kind = kind,
        form = form,
    )
}

/** [PrimaryButton] с подписью-значением: слово и необязательный ведущий глиф ([ButtonLabel]). */
@Composable
fun PrimaryButton(
    label: ButtonLabel,
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
        label = label,
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
            labelStyle = form.primaryLabelStyle(),
            labelPadding = form.labelPadding,
        ),
        modifier = modifier
            .heightIn(min = form.primaryHeight)
            .testTag(TAG_PRIMARY_BUTTON),
    )
}

/**
 * Второстепенная кнопка: действие рядом с главным.
 *
 * Прозрачного фона нет вовсе — не нарисован; граница `lineInteractive` заметнее
 * обычной линии, иначе кнопка не читается как кнопка. Тёмная форма меняет роли
 * по §5.1: граница — `line` (`LineDark`), подпись — `textOnDark`, нажатие —
 * подложка `surface` (`SurfaceDark`); заливки покоя у обеих форм нет.
 *
 * [form] по умолчанию карточный: кнопка внутри карточки — 44 dp и радиус 13.
 * Экранная кнопка задаёт `ButtonForm.Screen` (52 dp, радиус 16) сама, тёмное
 * напоминание — `ButtonForm.Dark` (54 dp, радиус 18); дизайн-система допускает
 * три формы (§4.3), а значение по умолчанию не трогает ни один существующий вызов.
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
    val roles = form.secondaryRoles(colors)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val showPressed = state == ButtonState.Pressed || pressed
    ButtonSurface(
        label = ButtonLabel(text),
        onClick = onClick,
        state = state,
        interactionSource = interactionSource,
        skin = ButtonSkin(
            background = if (showPressed && state.isActive) roles.pressedBackground else null,
            content = if (state.isActive) roles.content else colors.textDisabled,
            border = if (state.isActive) roles.border else colors.line,
            shape = form.shape,
            labelStyle = form.secondaryLabelStyle(),
            labelPadding = form.labelPadding,
        ),
        modifier = modifier
            .heightIn(min = form.secondaryHeight)
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
 * (цвет опасности, в чужом профиле не тонируется; ОВ-67, ОВ-124), `Warning` — действие
 * строки-предупреждения (цвет `warning`, §4.6).
 *
 * [form] — форма действия, а не его вид: строка/карточка набрана `LabelStrong` 14 sp/600,
 * шапка модального слоя — `BodyDefault` 15 sp/400 у отмены и `BodyDefaultStrong` 15 sp/600
 * у подтверждения (§4.9, §4.18, ОВ-146). Значение по умолчанию сохраняет вид строки.
 *
 * Подпись с ведущим глифом принимает перегрузка с [ButtonLabel] (ОВ-144): смысл несёт
 * слово, поэтому глиф не объявляется чтением с экрана. Цвет глифа выбирает место.
 */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: ButtonState = ButtonState.Normal,
    kind: QuietButtonKind = QuietButtonKind.Accent,
    form: QuietButtonForm = QuietButtonForm.Row,
) {
    QuietButton(
        label = ButtonLabel(text),
        onClick = onClick,
        modifier = modifier,
        state = state,
        kind = kind,
        form = form,
    )
}

/** [QuietButton] с подписью-значением: слово и необязательный ведущий глиф ([ButtonLabel]). */
@Composable
fun QuietButton(
    label: ButtonLabel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: ButtonState = ButtonState.Normal,
    kind: QuietButtonKind = QuietButtonKind.Accent,
    form: QuietButtonForm = QuietButtonForm.Row,
) {
    val colors = SklyankaTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val showPressed = state == ButtonState.Pressed || pressed
    val base = when (kind) {
        QuietButtonKind.Accent -> colors.accent
        QuietButtonKind.Muted -> colors.textMuted
        QuietButtonKind.Danger -> colors.danger
        // Тихие действия-предупреждения стоят на светлых подложках (§9, V-74).
        QuietButtonKind.Warning -> colors.warningOnSoft
    }
    Row(
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (label.leadingGlyph == null) {
            Arrangement.Center
        } else {
            Arrangement.spacedBy(SklyankaSpacing.Small)
        },
    ) {
        label.leadingGlyph?.invoke()
        Text(
            text = label.text,
            style = quietLabelStyle(form, kind),
            color = when {
                !state.isActive -> colors.textDisabled
                showPressed -> pressedTint(base)
                else -> base
            },
        )
    }
}

/**
 * Общая оболочка кнопок с фоном: один способ собрать габарит, подпись и границу.
 *
 * Кегль подписи и её боковое поле приходят в [ButtonSkin] — их выбирает форма и вид
 * кнопки (§4.3, ОВ-145): экранная форма набрана `BodyStrong` 16 sp/600, карточная —
 * `LabelStrong`/`Label` 14 sp, тёмная — `BodyLarge`/`BodyDefaultMedium` 17/15 sp
 * (§5.1); боковое поле подписи — 28 dp у светлых форм, 0 у тёмной.
 */
@Composable
private fun ButtonSurface(
    label: ButtonLabel,
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
            .padding(horizontal = skin.labelPadding),
        contentAlignment = Alignment.Center,
    ) {
        if (label.leadingGlyph == null) {
            ButtonLabelText(text = label.text, style = skin.labelStyle, color = skin.content)
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SklyankaSpacing.Default),
            ) {
                label.leadingGlyph()
                ButtonLabelText(text = label.text, style = skin.labelStyle, color = skin.content)
            }
        }
    }
}

/** Подпись кнопки одним способом: центрированная строка роли [ButtonSkin.content]. */
@Composable
private fun ButtonLabelText(text: String, style: TextStyle, color: Color) {
    Text(text = text, style = style, color = color, textAlign = TextAlign.Center)
}

/**
 * Подпись основной кнопки по форме (§4.3, ОВ-145, §5.1, ОВ-146): экранная —
 * `BodyStrong` 16 sp/600, карточная — `LabelStrong` 14 sp/600, тёмная —
 * `BodyLarge` 17 sp/600. Кегль — свойство формы, а не места вызова.
 */
internal fun ButtonForm.primaryLabelStyle(): TextStyle = when (this) {
    ButtonForm.Screen -> SklyankaType.BodyStrong
    ButtonForm.Card -> SklyankaType.LabelStrong
    ButtonForm.Dark -> SklyankaType.BodyLarge
}

/**
 * Подпись второстепенной кнопки по форме (§4.3, ОВ-145, §5.1, ОВ-146): экранная —
 * `BodyStrong` 16 sp/600, карточная — `Label` 14 sp/500: в паре «Принято / Пропустить»
 * второстепенная легче весом; тёмная — `BodyDefaultMedium` 15 sp/500.
 */
internal fun ButtonForm.secondaryLabelStyle(): TextStyle = when (this) {
    ButtonForm.Screen -> SklyankaType.BodyStrong
    ButtonForm.Card -> SklyankaType.Label
    ButtonForm.Dark -> SklyankaType.BodyDefaultMedium
}

/**
 * Роли второстепенной кнопки по форме (§4.3, §5.1): тёмная поверхность меняет
 * границу, подпись и подложку нажатия, значение — только ролью.
 */
internal data class SecondaryButtonRoles(val content: Color, val border: Color, val pressedBackground: Color)

internal fun ButtonForm.secondaryRoles(colors: SklyankaColors): SecondaryButtonRoles = when (this) {
    ButtonForm.Dark -> SecondaryButtonRoles(
        content = colors.textOnDark,
        border = colors.line,
        pressedBackground = colors.surface,
    )

    ButtonForm.Card, ButtonForm.Screen -> SecondaryButtonRoles(
        content = colors.text,
        border = colors.lineInteractive,
        pressedBackground = colors.neutralSoft,
    )
}

/**
 * Подпись тихой кнопки по форме и виду (§4.3, §4.9, ОВ-146): строка и карточка —
 * `LabelStrong` 14 sp/600, шапка модального слоя — `BodyDefault` 15 sp/400 у отмены
 * (`Muted`) и `BodyDefaultStrong` 15 sp/600 у подтверждения и прочих действий.
 */
internal fun quietLabelStyle(form: QuietButtonForm, kind: QuietButtonKind): TextStyle = when (form) {
    QuietButtonForm.Row -> SklyankaType.LabelStrong
    QuietButtonForm.Header -> when (kind) {
        QuietButtonKind.Muted -> SklyankaType.BodyDefault
        QuietButtonKind.Accent, QuietButtonKind.Danger, QuietButtonKind.Warning -> SklyankaType.BodyDefaultStrong
    }
}

const val TAG_PRIMARY_BUTTON = "primary_button"
const val TAG_SECONDARY_BUTTON = "secondary_button"
const val TAG_QUIET_BUTTON = "quiet_button"
