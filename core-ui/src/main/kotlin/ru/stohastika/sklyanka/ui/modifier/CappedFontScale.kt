package ru.stohastika.sklyanka.ui.modifier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/**
 * Ограничивает масштабирование кегля потолком [maxScale].
 *
 * Применяется **только** к [ru.stohastika.sklyanka.ui.token.SklyankaType.Display] на
 * полноэкранном напоминании: 64 sp при масштабе 200 % не помещается на 390 dp, и
 * время приёма — то единственное, ради чего этот экран существует, — обрезается.
 *
 * Остальная типографика масштабируется свободно (НФТ-31). Ограничение масштаба по
 * всему приложению — это отъём у человека того, что система ему дала, и делать
 * этого нельзя.
 */
@Composable
@ReadOnlyComposable
fun cappedFontSize(value: TextUnit, maxScale: Float = SklyankaSizes.DISPLAY_MAX_FONT_SCALE): TextUnit {
    val fontScale = LocalDensity.current.fontScale
    if (!value.isSp || fontScale <= maxScale) return value
    // Кегль уменьшается ровно настолько, насколько система увеличила его сверх
    // потолка: на экране получается размер, соответствующий масштабу maxScale.
    val capped = value.value * maxScale / fontScale
    return capped.sp
}

/** Тот же потолок, применённый к готовому стилю шкалы. */
@Composable
@ReadOnlyComposable
fun TextStyle.withCappedFontScale(maxScale: Float = SklyankaSizes.DISPLAY_MAX_FONT_SCALE): TextStyle = copy(
    fontSize = cappedFontSize(fontSize, maxScale),
    lineHeight = cappedFontSize(lineHeight, maxScale),
)
