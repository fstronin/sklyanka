package ru.heuri.sklyanka.feature.meds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import ru.heuri.sklyanka.domain.model.MedicationForm
import ru.heuri.sklyanka.domain.model.TabletShape
import ru.heuri.sklyanka.ui.token.SklyankaSizes

/**
 * Графика экранов лекарств.
 *
 * Рисуется, а не подключается набором, по той же причине, что и графика дизайн-системы:
 * продуктового набора иконок в проекте нет, а без силуэтов «Вид таблетки» превращается
 * в пять одинаковых пустых кнопок, различимых только программой чтения с экрана.
 *
 * Пропорции вынесены в константы файла: число, оставленное в вызове рисования, —
 * такой же неподменяемый размер, как и любой другой (НФТ-47).
 */

/** Толщина штриха относительно стороны — та же, что в графике дизайн-системы. */
private const val STROKE_RATIO = 0.09f
private const val THIN_STROKE_RATIO = 0.07f

/** Плюс и минус: середина стороны, поля по краям. */
private const val SIGN_NEAR = 0.22f
private const val SIGN_FAR = 0.78f
private const val MIDDLE = 0.5f

/** Шеврон перехода: вершина справа. */
private const val CHEVRON_LEFT = 0.34f
private const val CHEVRON_RIGHT = 0.66f
private const val CHEVRON_TOP = 0.22f
private const val CHEVRON_BOTTOM = 0.78f

/** Крестик удаления. */
private const val CROSS_NEAR = 0.26f
private const val CROSS_FAR = 0.74f

/** Циферблат и стрелки. */
private const val DIAL_RADIUS = 0.38f
private const val HAND_TOP = 0.27f
private const val HAND_RIGHT = 0.68f
private const val HAND_BOTTOM = 0.58f

/** Круглая таблетка и её риска. */
private const val PILL_RADIUS = 0.36f
private const val PILL_SCORE_TOP = 0.14f
private const val PILL_SCORE_BOTTOM = 0.86f

/** Овал, продолговатая и треугольник. */
private const val OVAL_RX = 0.42f
private const val OVAL_RY = 0.26f
private const val OBLONG_TOP = 0.32f
private const val OBLONG_BOTTOM = 0.68f
private const val OBLONG_LEFT = 0.08f
private const val OBLONG_RIGHT = 0.92f
private const val TRIANGLE_APEX_Y = 0.17f
private const val TRIANGLE_BASE_Y = 0.79f
private const val TRIANGLE_LEFT = 0.12f
private const val TRIANGLE_RIGHT = 0.88f

/** Капсула в сетке форм выпуска. */
private const val CAPSULE_TOP = 0.35f
private const val CAPSULE_BOTTOM = 0.65f
private const val CAPSULE_LEFT = 0.10f
private const val CAPSULE_RIGHT = 0.90f

/** Флакон: спрей и сироп. */
private const val BOTTLE_LEFT = 0.30f
private const val BOTTLE_RIGHT = 0.70f
private const val BOTTLE_NECK_TOP = 0.16f
private const val BOTTLE_SHOULDER = 0.36f
private const val BOTTLE_BOTTOM = 0.86f
private const val BOTTLE_NECK_LEFT = 0.42f
private const val BOTTLE_NECK_RIGHT = 0.58f

/** Капля. */
private const val DROP_TOP = 0.14f
private const val DROP_SIDE = 0.24f
private const val DROP_BOTTOM = 0.84f

/** Шприц: одна диагональ с поршнем. */
private const val NEEDLE_START = 0.16f
private const val NEEDLE_END = 0.84f
private const val PLUNGER_OFFSET = 0.16f

/** Знак «плюс»: круглая кнопка добавления и кнопка «Увеличить». */
@Composable
internal fun PlusGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.MarkButtonGlyph) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawPath(
            path = Path().apply {
                moveTo(side * MIDDLE, side * SIGN_NEAR)
                lineTo(side * MIDDLE, side * SIGN_FAR)
                moveTo(side * SIGN_NEAR, side * MIDDLE)
                lineTo(side * SIGN_FAR, side * MIDDLE)
            },
            color = color,
            style = roundStroke(side),
        )
    }
}

/** Знак «минус»: кнопка «Уменьшить». */
@Composable
internal fun MinusGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.MarkButtonGlyph) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawLine(
            color = color,
            start = Offset(side * SIGN_NEAR, side * MIDDLE),
            end = Offset(side * SIGN_FAR, side * MIDDLE),
            strokeWidth = side * STROKE_RATIO,
            cap = StrokeCap.Round,
        )
    }
}

/** Шеврон перехода в карточке списка. */
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
            style = roundStroke(side),
        )
    }
}

/** Крестик: удаление строки времени. */
@Composable
internal fun CrossGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.MarkButtonGlyph) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawPath(
            path = Path().apply {
                moveTo(side * CROSS_NEAR, side * CROSS_NEAR)
                lineTo(side * CROSS_FAR, side * CROSS_FAR)
                moveTo(side * CROSS_FAR, side * CROSS_NEAR)
                lineTo(side * CROSS_NEAR, side * CROSS_FAR)
            },
            color = color,
            style = roundStroke(side),
        )
    }
}

/** Часы: строка времени приёма. */
@Composable
internal fun ClockGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.BannerIcon) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawCircle(color = color, radius = side * DIAL_RADIUS, style = thinStroke(side))
        drawPath(
            path = Path().apply {
                moveTo(side * MIDDLE, side * HAND_TOP)
                lineTo(side * MIDDLE, side * HAND_BOTTOM)
                lineTo(side * HAND_RIGHT, side * HAND_BOTTOM)
            },
            color = color,
            style = thinStroke(side),
        )
    }
}

/** Силуэт таблетки: пять видов ряда «Вид таблетки». */
@Composable
internal fun TabletShapeGlyph(
    shape: TabletShape,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = SklyankaSizes.ShapeGlyph,
) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        when (shape) {
            TabletShape.ROUND -> drawCircle(color, radius = side * PILL_RADIUS, style = thinStroke(side))
            TabletShape.ROUND_SCORED -> {
                drawCircle(color, radius = side * PILL_RADIUS, style = thinStroke(side))
                drawLine(
                    color = color,
                    start = Offset(side * MIDDLE, side * PILL_SCORE_TOP),
                    end = Offset(side * MIDDLE, side * PILL_SCORE_BOTTOM),
                    strokeWidth = side * THIN_STROKE_RATIO,
                )
            }

            TabletShape.OVAL -> drawOval(
                color = color,
                topLeft = Offset(side * (MIDDLE - OVAL_RX), side * (MIDDLE - OVAL_RY)),
                size = Size(side * OVAL_RX * 2, side * OVAL_RY * 2),
                style = thinStroke(side),
            )

            TabletShape.OBLONG -> drawRoundRect(
                color = color,
                topLeft = Offset(side * OBLONG_LEFT, side * OBLONG_TOP),
                size = Size(side * (OBLONG_RIGHT - OBLONG_LEFT), side * (OBLONG_BOTTOM - OBLONG_TOP)),
                cornerRadius = CornerRadius(side * (OBLONG_BOTTOM - OBLONG_TOP) / 2),
                style = thinStroke(side),
            )

            TabletShape.TRIANGULAR -> drawPath(
                path = Path().apply {
                    moveTo(side * MIDDLE, side * TRIANGLE_APEX_Y)
                    lineTo(side * TRIANGLE_RIGHT, side * TRIANGLE_BASE_Y)
                    lineTo(side * TRIANGLE_LEFT, side * TRIANGLE_BASE_Y)
                    close()
                },
                color = color,
                style = thinStroke(side),
            )
        }
    }
}

/** Значок формы выпуска: плитка сетки 3×2 и плашка карточки списка. */
@Composable
internal fun ReleaseFormGlyph(
    form: MedicationForm,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = SklyankaSizes.FormTileGlyph,
) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        when (form) {
            MedicationForm.TABLET -> {
                drawCircle(color, radius = side * PILL_RADIUS, style = thinStroke(side))
                drawLine(
                    color = color,
                    start = Offset(side * (MIDDLE - PILL_RADIUS), side * MIDDLE),
                    end = Offset(side * (MIDDLE + PILL_RADIUS), side * MIDDLE),
                    strokeWidth = side * THIN_STROKE_RATIO,
                )
            }

            MedicationForm.CAPSULE -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(side * CAPSULE_LEFT, side * CAPSULE_TOP),
                    size = Size(side * (CAPSULE_RIGHT - CAPSULE_LEFT), side * (CAPSULE_BOTTOM - CAPSULE_TOP)),
                    cornerRadius = CornerRadius(side * (CAPSULE_BOTTOM - CAPSULE_TOP) / 2),
                    style = thinStroke(side),
                )
                drawLine(
                    color = color,
                    start = Offset(side * MIDDLE, side * CAPSULE_TOP),
                    end = Offset(side * MIDDLE, side * CAPSULE_BOTTOM),
                    strokeWidth = side * THIN_STROKE_RATIO,
                )
            }

            MedicationForm.SPRAY, MedicationForm.SYRUP -> drawBottle(side, color)
            MedicationForm.DROPS -> drawPath(
                path = Path().apply {
                    moveTo(side * MIDDLE, side * DROP_TOP)
                    lineTo(side * (MIDDLE + DROP_SIDE), side * (DROP_BOTTOM - DROP_SIDE))
                    lineTo(side * MIDDLE, side * DROP_BOTTOM)
                    lineTo(side * (MIDDLE - DROP_SIDE), side * (DROP_BOTTOM - DROP_SIDE))
                    close()
                },
                color = color,
                style = thinStroke(side),
            )

            MedicationForm.INJECTION -> drawPath(
                path = Path().apply {
                    moveTo(side * NEEDLE_START, side * NEEDLE_END)
                    lineTo(side * NEEDLE_END, side * NEEDLE_START)
                    moveTo(side * (NEEDLE_END - PLUNGER_OFFSET), side * NEEDLE_START)
                    lineTo(side * NEEDLE_END, side * (NEEDLE_START + PLUNGER_OFFSET))
                },
                color = color,
                style = thinStroke(side),
            )
        }
    }
}

/** Флакон с горлышком: спрей и сироп различаются подписью, а не силуэтом. */
private fun DrawScope.drawBottle(side: Float, color: Color) {
    drawPath(
        path = Path().apply {
            moveTo(side * BOTTLE_NECK_LEFT, side * BOTTLE_NECK_TOP)
            lineTo(side * BOTTLE_NECK_RIGHT, side * BOTTLE_NECK_TOP)
            lineTo(side * BOTTLE_NECK_RIGHT, side * BOTTLE_SHOULDER)
            lineTo(side * BOTTLE_RIGHT, side * BOTTLE_SHOULDER)
            lineTo(side * BOTTLE_RIGHT, side * BOTTLE_BOTTOM)
            lineTo(side * BOTTLE_LEFT, side * BOTTLE_BOTTOM)
            lineTo(side * BOTTLE_LEFT, side * BOTTLE_SHOULDER)
            lineTo(side * BOTTLE_NECK_LEFT, side * BOTTLE_SHOULDER)
            close()
        },
        color = color,
        style = thinStroke(side),
    )
}

private fun roundStroke(side: Float) =
    Stroke(width = side * STROKE_RATIO, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun thinStroke(side: Float) =
    Stroke(width = side * THIN_STROKE_RATIO, cap = StrokeCap.Round, join = StrokeJoin.Round)
