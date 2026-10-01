package ru.stohastika.sklyanka.feature.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/**
 * Графика экрана «Сегодня».
 *
 * Рисуется, а не подключается набором, по той же причине, что и графика дизайн-системы
 * и экранов лекарств: продуктового набора иконок в проекте нет (SKL-21 нарисовал знак,
 * а не набор). Пропорции вынесены в константы файла: число, оставленное в вызове
 * рисования, — такой же неподменяемый размер, как и любой другой (НФТ-47).
 */

private const val STROKE_RATIO = 0.09f
private const val MIDDLE = 0.5f

/** Шеврон вниз: свёрнутая секция плотного дня. */
private const val CHEVRON_LEFT = 0.24f
private const val CHEVRON_RIGHT = 0.76f
private const val CHEVRON_TOP = 0.38f
private const val CHEVRON_BOTTOM = 0.62f

/** Циферблат и стрелки: значок секции, которая ещё впереди. */
private const val DIAL_RADIUS = 0.38f
private const val HAND_TOP = 0.28f
private const val HAND_RIGHT = 0.66f
private const val HAND_BOTTOM = 0.56f

/** Шеврон вниз у свёрнутой секции (`MainDense.dc.html`). */
@Composable
internal fun ChevronDownGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.Chevron) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawPath(
            path = Path().apply {
                moveTo(side * CHEVRON_LEFT, side * CHEVRON_TOP)
                lineTo(side * MIDDLE, side * CHEVRON_BOTTOM)
                lineTo(side * CHEVRON_RIGHT, side * CHEVRON_TOP)
            },
            color = color,
            style = stroke(side),
        )
    }
}

/** Часы: секция времени суток, которая ещё не наступила (`MainDense.dc.html`). */
@Composable
internal fun ClockGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.MarkButtonGlyph) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawCircle(color, radius = side * DIAL_RADIUS, center = center, style = stroke(side))
        drawPath(
            path = Path().apply {
                moveTo(side * MIDDLE, side * HAND_TOP)
                lineTo(side * MIDDLE, side * HAND_BOTTOM)
                lineTo(side * HAND_RIGHT, side * HAND_BOTTOM)
            },
            color = color,
            style = stroke(side),
        )
    }
}

private fun stroke(side: Float): Stroke =
    Stroke(width = side * STROKE_RATIO, cap = StrokeCap.Round, join = StrokeJoin.Round)
