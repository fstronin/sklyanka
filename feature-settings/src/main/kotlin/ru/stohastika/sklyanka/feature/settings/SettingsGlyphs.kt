package ru.stohastika.sklyanka.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/**
 * Графика экрана настроек (`design/screens/Settings.dc.html`).
 *
 * Рисуется здесь по той же причине, по которой свою графику держат «Сегодня», лекарства
 * и история: продуктового набора иконок в проекте нет, знак дизайн-системы — не набор.
 * Значки вкладок нижней панели повторяют значки панели «Сегодня»: там они внутренние
 * для своего модуля, а `feature-*` друг от друга не зависят, и общий их дом — либо
 * дизайн-система, либо два файла. Выбран второй вариант: панель нарисована в двух
 * макетах и в каталоге компонентов отсутствует, то есть компонентом системы она не
 * является (`docs/norm/06-design.md`).
 *
 * Пропорции — константами файла: число, оставленное в вызове рисования, — такой же
 * неподменяемый размер, как и любой другой (НФТ-47).
 */

private const val STROKE_RATIO = 0.09f
private const val MIDDLE = 0.5f

/** Шеврон перехода в строке настроек. */
private const val CHEVRON_LEFT = 0.34f
private const val CHEVRON_RIGHT = 0.66f
private const val CHEVRON_TOP = 0.24f
private const val CHEVRON_BOTTOM = 0.76f

/** Плюс на пустом кружке строки «Добавить профиль». */
private const val PLUS_NEAR = 0.24f
private const val PLUS_FAR = 0.76f

/** Пунктирная обводка кружка «Добавить профиль»: штрих и разрыв из макета. */
private const val DASH_ON = 3f
private const val DASH_OFF = 3f

/** Шеврон вправо: строка ведёт дальше. */
@Composable
internal fun ChevronGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.Chevron) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawPath(
            path = Path().apply {
                moveTo(side * CHEVRON_LEFT, side * CHEVRON_TOP)
                lineTo(side * CHEVRON_RIGHT, side * MIDDLE)
                lineTo(side * CHEVRON_LEFT, side * CHEVRON_BOTTOM)
            },
            color = color,
            style = stroke(side),
        )
    }
}

/** Плюс: «Добавить профиль». */
@Composable
internal fun PlusGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.Chevron) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawLine(
            color = color,
            start = Offset(side * MIDDLE, side * PLUS_NEAR),
            end = Offset(side * MIDDLE, side * PLUS_FAR),
            strokeWidth = side * STROKE_RATIO,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(side * PLUS_NEAR, side * MIDDLE),
            end = Offset(side * PLUS_FAR, side * MIDDLE),
            strokeWidth = side * STROKE_RATIO,
            cap = StrokeCap.Round,
        )
    }
}

/** Пунктирный кружок вокруг плюса: место профиля, которого ещё нет. */
@Composable
internal fun DashedCircle(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.IconPlate) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawCircle(
            color = color,
            radius = side / 2 - side * STROKE_RATIO / 2,
            center = center,
            style = Stroke(
                width = side * STROKE_RATIO / 2,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(DASH_ON, DASH_OFF)),
            ),
        )
    }
}

private fun stroke(side: Float): Stroke =
    Stroke(width = side * STROKE_RATIO, cap = StrokeCap.Round, join = StrokeJoin.Round)
