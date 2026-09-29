package ru.stohastika.sklyanka.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
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
 * является (`docs/spec/08-design-system.md`).
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

/** Календарь вкладки «Сегодня». */
private const val SHEET_LEFT = 0.14f
private const val SHEET_RIGHT = 0.86f
private const val SHEET_TOP = 0.20f
private const val SHEET_BOTTOM = 0.86f
private const val SHEET_RADIUS = 0.14f
private const val RING_TOP = 0.10f
private const val RING_BOTTOM = 0.30f
private const val RING_LEFT = 0.34f
private const val RING_RIGHT = 0.66f
private const val SHEET_LINE = 0.42f

/** Шестерёнка вкладки «Настройки»: сердцевина и шесть лучей. */
private const val CORE_RADIUS = 0.15f
private const val SPOKE_INNER = 0.26f
private const val SPOKE_OUTER = 0.44f
private const val SPOKE_COUNT = 6
private const val FULL_TURN = 360f

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

/** Календарь — вкладка «Сегодня». */
@Composable
internal fun CalendarGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.IconPlateGlyph) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawRoundRect(
            color = color,
            topLeft = Offset(side * SHEET_LEFT, side * SHEET_TOP),
            size = Size(side * (SHEET_RIGHT - SHEET_LEFT), side * (SHEET_BOTTOM - SHEET_TOP)),
            cornerRadius = CornerRadius(side * SHEET_RADIUS),
            style = stroke(side),
        )
        listOf(RING_LEFT, RING_RIGHT).forEach { x ->
            drawLine(
                color = color,
                start = Offset(side * x, side * RING_TOP),
                end = Offset(side * x, side * RING_BOTTOM),
                strokeWidth = side * STROKE_RATIO,
                cap = StrokeCap.Round,
            )
        }
        drawLine(
            color = color,
            start = Offset(side * SHEET_LEFT, side * SHEET_LINE),
            end = Offset(side * SHEET_RIGHT, side * SHEET_LINE),
            strokeWidth = side * STROKE_RATIO,
            cap = StrokeCap.Round,
        )
    }
}

/** Шестерёнка — вкладка «Настройки». */
@Composable
internal fun GearGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.IconPlateGlyph) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawCircle(color, radius = side * CORE_RADIUS, center = center, style = stroke(side))
        repeat(SPOKE_COUNT) { index ->
            val angle = Math.toRadians((FULL_TURN / SPOKE_COUNT * index).toDouble())
            drawLine(
                color = color,
                start = spoke(side, angle, SPOKE_INNER),
                end = spoke(side, angle, SPOKE_OUTER),
                strokeWidth = side * STROKE_RATIO,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun DrawScope.spoke(side: Float, angle: Double, length: Float): Offset = Offset(
    x = center.x + (side * length * kotlin.math.cos(angle)).toFloat(),
    y = center.y + (side * length * kotlin.math.sin(angle)).toFloat(),
)

private fun stroke(side: Float): Stroke =
    Stroke(width = side * STROKE_RATIO, cap = StrokeCap.Round, join = StrokeJoin.Round)
