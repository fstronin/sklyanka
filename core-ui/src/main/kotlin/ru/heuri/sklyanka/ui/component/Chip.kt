package ru.heuri.sklyanka.ui.component

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.SklyankaColors
import ru.heuri.sklyanka.ui.token.SklyankaShapes
import ru.heuri.sklyanka.ui.token.SklyankaSizes
import ru.heuri.sklyanka.ui.token.SklyankaType

/**
 * Чип: компактная метка или компактный выбор.
 *
 * Высота чипа-кнопки не меньше цели нажатия (§4.2); у информационного чипа нажатия
 * нет, поэтому и требования к области нет.
 *
 * Выбранное состояние отличается не только цветом: рамка утолщается до 1,5 dp, а
 * подпись становится полужирной (НФТ-33).
 */
@Composable
fun SklyankaChip(
    text: String,
    modifier: Modifier = Modifier,
    state: ChipState = ChipState.Unselected,
    shape: RoundedCornerShape = SklyankaShapes.Button,
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
                        .clickable(onClick = { onClick?.invoke() })
                        .defaultMinSize(minHeight = SklyankaSizes.ChipHeight)
                } else {
                    Modifier
                },
            )
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
