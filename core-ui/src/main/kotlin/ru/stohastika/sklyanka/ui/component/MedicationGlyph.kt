package ru.stohastika.sklyanka.ui.component

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
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.TabletShape
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/**
 * Иконография лекарства: силуэт формы выпуска и вида таблетки (§4.15, ОВ-94).
 *
 * Правило **иконка = форма плюс вид**: форма задаёт силуэт, вид таблетки его уточняет;
 * у форм, кроме «Таблетки», вид не применяется (ОВ-88). Рисуется на `Canvas`, как и вся
 * графика проекта: продуктового набора иконок нет, а без силуэтов плитки формы и вида
 * превращаются в пустые квадраты, различимые только программой чтения с экрана.
 *
 * Пропорции вынесены в константы файла: число, оставленное в вызове рисования, —
 * такой же неподменяемый размер, как и любой другой (НФТ-47).
 */

/** Толщина штриха относительно стороны — тонкая линия контура без заливки (§4.15). */
private const val THIN_STROKE_RATIO = 0.07f

/** Середина стороны. */
private const val MIDDLE = 0.5f

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

/** Капсула. */
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

/**
 * Значок лекарства «форма + вид» (§4.15).
 *
 * Вид используется только у формы «Таблетка»; у остальных он игнорируется, и это не
 * защита от плохих данных, а само правило ОВ-88: силуэт капсулы задан самой формой.
 */
@Composable
fun MedicationGlyph(
    form: MedicationForm,
    shape: TabletShape?,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = SklyankaSizes.FormTileGlyph,
) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        val stroke = thinStroke(side)
        when (form) {
            MedicationForm.TABLET -> {
                if (shape == null) {
                    drawCircle(color, radius = side * PILL_RADIUS, style = stroke)
                    drawLine(
                        color = color,
                        start = Offset(side * (MIDDLE - PILL_RADIUS), side * MIDDLE),
                        end = Offset(side * (MIDDLE + PILL_RADIUS), side * MIDDLE),
                        strokeWidth = side * THIN_STROKE_RATIO,
                    )
                } else {
                    drawTabletShape(shape, side, color, stroke)
                }
            }

            MedicationForm.CAPSULE -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(side * CAPSULE_LEFT, side * CAPSULE_TOP),
                    size = Size(side * (CAPSULE_RIGHT - CAPSULE_LEFT), side * (CAPSULE_BOTTOM - CAPSULE_TOP)),
                    cornerRadius = CornerRadius(side * (CAPSULE_BOTTOM - CAPSULE_TOP) / 2),
                    style = stroke,
                )
                drawLine(
                    color = color,
                    start = Offset(side * MIDDLE, side * CAPSULE_TOP),
                    end = Offset(side * MIDDLE, side * CAPSULE_BOTTOM),
                    strokeWidth = side * THIN_STROKE_RATIO,
                )
            }

            MedicationForm.SPRAY, MedicationForm.SYRUP -> drawBottle(side, color, stroke)
            MedicationForm.DROPS -> drawPath(
                path = Path().apply {
                    moveTo(side * MIDDLE, side * DROP_TOP)
                    lineTo(side * (MIDDLE + DROP_SIDE), side * (DROP_BOTTOM - DROP_SIDE))
                    lineTo(side * MIDDLE, side * DROP_BOTTOM)
                    lineTo(side * (MIDDLE - DROP_SIDE), side * (DROP_BOTTOM - DROP_SIDE))
                    close()
                },
                color = color,
                style = stroke,
            )

            MedicationForm.INJECTION -> drawPath(
                path = Path().apply {
                    moveTo(side * NEEDLE_START, side * NEEDLE_END)
                    lineTo(side * NEEDLE_END, side * NEEDLE_START)
                    moveTo(side * (NEEDLE_END - PLUNGER_OFFSET), side * NEEDLE_START)
                    lineTo(side * NEEDLE_END, side * (NEEDLE_START + PLUNGER_OFFSET))
                },
                color = color,
                style = stroke,
            )
        }
    }
}

/** Силуэт вида таблетки: ряд «Вид таблетки» (§4.15). */
@Composable
fun TabletShapeGlyph(
    shape: TabletShape,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = SklyankaSizes.ShapeGlyph,
) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawTabletShape(shape, side, color, thinStroke(side))
    }
}

/** Правило силуэта вида: табличное, без обращения к цветам и теме. */
private fun DrawScope.drawTabletShape(shape: TabletShape, side: Float, color: Color, stroke: Stroke) {
    when (shape) {
        TabletShape.ROUND -> drawCircle(color, radius = side * PILL_RADIUS, style = stroke)
        TabletShape.ROUND_SCORED -> {
            drawCircle(color, radius = side * PILL_RADIUS, style = stroke)
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
            style = stroke,
        )

        TabletShape.OBLONG -> drawRoundRect(
            color = color,
            topLeft = Offset(side * OBLONG_LEFT, side * OBLONG_TOP),
            size = Size(side * (OBLONG_RIGHT - OBLONG_LEFT), side * (OBLONG_BOTTOM - OBLONG_TOP)),
            cornerRadius = CornerRadius(side * (OBLONG_BOTTOM - OBLONG_TOP) / 2),
            style = stroke,
        )

        TabletShape.TRIANGULAR -> drawPath(
            path = Path().apply {
                moveTo(side * MIDDLE, side * TRIANGLE_APEX_Y)
                lineTo(side * TRIANGLE_RIGHT, side * TRIANGLE_BASE_Y)
                lineTo(side * TRIANGLE_LEFT, side * TRIANGLE_BASE_Y)
                close()
            },
            color = color,
            style = stroke,
        )
    }
}

/** Флакон с горлышком: спрей и сироп различаются подписью, а не силуэтом. */
private fun DrawScope.drawBottle(side: Float, color: Color, stroke: Stroke) {
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
        style = stroke,
    )
}

private fun thinStroke(side: Float) =
    Stroke(width = side * THIN_STROKE_RATIO, cap = StrokeCap.Round, join = StrokeJoin.Round)
