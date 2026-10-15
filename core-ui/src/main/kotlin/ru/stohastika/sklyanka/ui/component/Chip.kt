package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaColors
import ru.stohastika.sklyanka.ui.token.SklyankaShapes
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Чип: компактная метка или компактный выбор.
 *
 * Высота чипа-кнопки не меньше цели нажатия (§4.2); у информационного чипа нажатия
 * нет, поэтому и требования к области нет.
 *
 * Выбранное состояние отличается не только цветом: рамка утолщается до 1,5 dp, а
 * подпись становится полужирной (НФТ-33).
 */
@Suppress("LongParameterList")
@Composable
fun SklyankaChip(
    text: String,
    modifier: Modifier = Modifier,
    state: ChipState = ChipState.Unselected,
    shape: RoundedCornerShape = SklyankaShapes.Button,
    /**
     * Объявленная роль чипа (НФТ-30, §5.10). По умолчанию `RadioButton` — выбор одного
     * значения из набора (порог покупки, пресет срока). Ряд дней недели выбирает несколько
     * значений разом и объявляет `Role.Checkbox`; вид чипа и токены при этом не меняются.
     */
    role: Role = Role.RadioButton,
    /**
     * Полное имя для чтения с экрана, если подпись чипа сокращена: дни недели стоят короткой
     * формой («Ср»), а читаются полной («среда», §5.10). `null` — читается сама подпись.
     */
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = SklyankaTheme.colors
    val background = chipBackground(state, colors)
    val border = chipBorder(state, colors)
    val borderWidth = if (state == ChipState.Selected) SklyankaSizes.BorderAccent else SklyankaSizes.BorderThin
    val clickable = onClick != null && state != ChipState.Info && state != ChipState.Disabled
    Box(
        modifier = modifier
            .then(
                if (clickable) {
                    Modifier
                        // Индикация нажатия обрезается формой чипа: без этого мазок
                        // нажима выходит за скругление квадратными углами (SKL-101,
                        // дефект 3). Фон и рамка и так нарисованы по форме — обрезка
                        // их не меняет, а нежимаемые чипы остаются без лишнего слоя.
                        .clip(shape)
                        .clickable(role = role, onClick = { onClick?.invoke() })
                        .defaultMinSize(minHeight = SklyankaSizes.ChipHeight)
                } else {
                    Modifier
                },
            )
            // Выбор чипа показан не только цветом и начертанием: без семантики
            // `selected` программа чтения не отличает выбранный порог или пресет
            // от остальных, а без `disabled` — не отличает недоступный чип от
            // ненажимаемой метки (НФТ-30, SKL-74/75).
            .semantics {
                if (state != ChipState.Info) selected = state == ChipState.Selected
                if (state == ChipState.Disabled) disabled()
                if (contentDescription != null) this.contentDescription = contentDescription
            }
            .background(background, shape)
            .then(
                if (border == null) Modifier else Modifier.border(borderWidth, border, shape),
            )
            .padding(horizontal = SklyankaSizes.ChipPadding, vertical = SklyankaSizes.BannerPaddingVertical)
            .testTag(TAG_CHIP),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (state == ChipState.Selected) SklyankaType.CaptionStrong else SklyankaType.Caption,
            color = if (state == ChipState.Disabled) colors.textDisabled else colors.text,
            // Полное имя заменяет сокращённую подпись в чтении с экрана целиком, а не
            // читается вместе с ней (НФТ-30).
            modifier = if (contentDescription == null) Modifier else Modifier.clearAndSetSemantics { },
        )
    }
}

/** Подложка чипа по состоянию (§4.2). */
private fun chipBackground(state: ChipState, colors: SklyankaColors): Color = when (state) {
    ChipState.Info -> colors.neutralSoft
    ChipState.Selected -> colors.accentSoft
    else -> colors.surface
}

/** Граница чипа по состоянию; у информационного её нет вовсе. */
private fun chipBorder(state: ChipState, colors: SklyankaColors): Color? = when (state) {
    ChipState.Info -> null
    ChipState.Selected -> colors.accent
    ChipState.Disabled -> colors.line
    ChipState.Unselected -> colors.lineOnSurface
}

const val TAG_CHIP = "chip"
