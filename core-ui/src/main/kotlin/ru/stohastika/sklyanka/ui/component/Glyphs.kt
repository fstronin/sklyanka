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
import ru.stohastika.sklyanka.ui.token.MarkVariant
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.markVariantFor

/**
 * Простая графика дизайн-системы.
 *
 * Иконки рисуются, а не подключаются набором: продуктовый набор иконок — работа
 * SKL-21, а без галочки и предупреждающего знака каталог компонентов показывал бы
 * состояния, отличающиеся только цветом, чего требование НФТ-33 прямо не допускает.
 *
 * Пропорции вынесены в константы файла: число, оставленное в вызове рисования, —
 * такой же неподменяемый размер, как и любой другой (НФТ-47).
 */

/** Толщина штриха относительно стороны: тоньше не читается на 16 dp. */
private const val STROKE_RATIO = 0.13f

/** Точки галочки в долях стороны. */
private const val CHECK_START_X = 0.17f
private const val CHECK_START_Y = 0.52f
private const val CHECK_MID_X = 0.40f
private const val CHECK_MID_Y = 0.75f
private const val CHECK_END_X = 0.83f
private const val CHECK_END_Y = 0.27f

/** Треугольник предупреждения. */
private const val TRIANGLE_APEX_X = 0.5f
private const val TRIANGLE_APEX_Y = 0.16f
private const val TRIANGLE_LEFT_X = 0.10f
private const val TRIANGLE_BASE_Y = 0.83f
private const val TRIANGLE_RIGHT_X = 0.90f
private const val EXCLAMATION_TOP = 0.42f
private const val EXCLAMATION_BOTTOM = 0.62f
private const val EXCLAMATION_DOT = 0.72f

/** Голова и плечи в значке профиля. */
private const val HEAD_CENTER_Y = 0.33f
private const val HEAD_RADIUS = 0.16f
private const val SHOULDER_LEFT_X = 0.18f
private const val SHOULDER_RIGHT_X = 0.82f
private const val SHOULDER_TOP_Y = 0.85f
private const val SHOULDER_CONTROL_Y = 0.55f

/** Капсула: скруглённый прямоугольник в плашке лекарства. */
private const val CAPSULE_TOP = 0.34f
private const val CAPSULE_BOTTOM = 0.66f
private const val CAPSULE_LEFT = 0.12f
private const val CAPSULE_RIGHT = 0.88f

/** Календарь вкладки «Сегодня» (перенесён в дизайн-систему, ОВ-91). */
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

/** Галочка: обязательный спутник цвета в статусе «принято» (НФТ-33). */
@Composable
fun CheckGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.MarkButtonGlyph) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawPath(
            path = Path().apply {
                moveTo(side * CHECK_START_X, side * CHECK_START_Y)
                lineTo(side * CHECK_MID_X, side * CHECK_MID_Y)
                lineTo(side * CHECK_END_X, side * CHECK_END_Y)
            },
            color = color,
            style = Stroke(
                width = side * STROKE_RATIO,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

/** Предупреждение: треугольник с восклицательным знаком. */
@Composable
fun WarningGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.BannerIcon) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        val stroke = Stroke(width = side * STROKE_RATIO, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(
            path = Path().apply {
                moveTo(side * TRIANGLE_APEX_X, side * TRIANGLE_APEX_Y)
                lineTo(side * TRIANGLE_RIGHT_X, side * TRIANGLE_BASE_Y)
                lineTo(side * TRIANGLE_LEFT_X, side * TRIANGLE_BASE_Y)
                close()
            },
            color = color,
            style = stroke,
        )
        drawPath(
            path = Path().apply {
                moveTo(side * TRIANGLE_APEX_X, side * EXCLAMATION_TOP)
                lineTo(side * TRIANGLE_APEX_X, side * EXCLAMATION_BOTTOM)
                moveTo(side * TRIANGLE_APEX_X, side * EXCLAMATION_DOT)
                lineTo(side * TRIANGLE_APEX_X, side * EXCLAMATION_DOT)
            },
            color = color,
            style = stroke,
        )
    }
}

/** Значок профиля: тот же силуэт, что в баннере «Это не ваши приёмы». */
@Composable
fun ProfileGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.BannerIcon) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        val stroke = Stroke(width = side * STROKE_RATIO, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(
            color = color,
            radius = side * HEAD_RADIUS,
            center = Offset(side * TRIANGLE_APEX_X, side * HEAD_CENTER_Y),
            style = stroke,
        )
        drawPath(
            path = Path().apply {
                moveTo(side * SHOULDER_LEFT_X, side * SHOULDER_TOP_Y)
                quadraticTo(
                    side * TRIANGLE_APEX_X,
                    side * SHOULDER_CONTROL_Y,
                    side * SHOULDER_RIGHT_X,
                    side * SHOULDER_TOP_Y,
                )
            },
            color = color,
            style = stroke,
        )
    }
}

/**
 * Нейтральная пиктограмма лекарства для плашки иконки.
 *
 * Имя `MedicationGlyph` носит иконография «форма + вид» (§4.15): пиктограмма приёма —
 * это не силуэт выбранного лекарства, а обобщённый знак на плашке. Имена разные, потому
 * что разные и сущности; см. `MedicationGlyph`.
 */
@Composable
fun MedicationPictogram(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.IconPlateGlyph) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        val stroke = Stroke(width = side * STROKE_RATIO, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val height = side * (CAPSULE_BOTTOM - CAPSULE_TOP)
        drawRoundRect(
            color = color,
            topLeft = Offset(side * CAPSULE_LEFT, side * CAPSULE_TOP),
            size = Size(side * (CAPSULE_RIGHT - CAPSULE_LEFT), height),
            cornerRadius = CornerRadius(height / 2),
            style = stroke,
        )
        drawLine(
            color = color,
            start = Offset(side * TRIANGLE_APEX_X, side * CAPSULE_TOP),
            end = Offset(side * TRIANGLE_APEX_X, side * CAPSULE_BOTTOM),
            strokeWidth = side * STROKE_RATIO,
            cap = StrokeCap.Round,
        )
    }
}

/**
 * Календарь — вкладка «Сегодня» (ОВ-91).
 *
 * Значки вкладок нижней панели живут в дизайн-системе: панель — общий компонент
 * (§4.13), и второй её экземпляр в экранном модуле разошёлся бы с первым.
 */
@Composable
fun CalendarGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.IconPlateGlyph) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawRoundRect(
            color = color,
            topLeft = Offset(side * SHEET_LEFT, side * SHEET_TOP),
            size = Size(side * (SHEET_RIGHT - SHEET_LEFT), side * (SHEET_BOTTOM - SHEET_TOP)),
            cornerRadius = CornerRadius(side * SHEET_RADIUS),
            style = Stroke(width = side * STROKE_RATIO, cap = StrokeCap.Round, join = StrokeJoin.Round),
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

/** Шестерёнка — вкладка «Настройки» (ОВ-91). */
@Composable
fun GearGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.IconPlateGlyph) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawCircle(
            color = color,
            radius = side * CORE_RADIUS,
            center = center,
            style = Stroke(width = side * STROKE_RATIO, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
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

/**
 * Знак «Склянки» плоской редукцией, заданной краской.
 *
 * Отличие от [SklyankaMark] — в том, кто решает про цвет. Знак «сам по себе» стоит
 * в среде и красится средой; знак-иллюстрация (пустое состояние, плашка
 * «О приложении», диагностика часов) подчинён тону экрана и красится ролью, которую
 * задаёт место: `switchOff` в пустом состоянии, `textTertiary` в «О приложении» (§7).
 * Поэтому цвет здесь параметр, а не вывод из среды.
 *
 * Ступень редукции при этом всё равно выбирается размером, а не вручную: правило
 * одно на весь знак ([markVariantFor]). На 92 dp пустого состояния это «два кольца
 * и точка», на 40 dp плашки — та же пара колец потолще, на 18 dp — кольцо и точка.
 */
@Composable
fun FlatMark(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.EmptyStateMark) {
    // Стекло сюда не попадает даже на 92 dp: иллюстрация одной краской — это
    // требование §7, а не следствие размера. Ступень берётся ближайшая плоская.
    val variant = markVariantFor(size).takeUnless { it == MarkVariant.Glass } ?: MarkVariant.FlatRingsAndDot
    Canvas(modifier.size(size)) { drawFlatVariant(variant, color) }
}
