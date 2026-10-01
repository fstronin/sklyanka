package ru.stohastika.sklyanka.feature.onboarding

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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/**
 * Графика экрана разрешений.
 *
 * Рисуется, а не подключается набором, по той же причине, что графика «Сегодня» и лекарств:
 * продуктового набора иконок в проекте нет. Пропорции вынесены в константы файла — число,
 * оставленное в вызове рисования, такой же неподменяемый размер, как любой другой (НФТ-47).
 *
 * Формы взяты из макета (`design/screens/Onboarding.dc.html`): колокольчик у уведомлений,
 * будильник у точных будильников, молния у автозапуска.
 */

private const val STROKE_RATIO = 0.085f
private const val MIDDLE = 0.5f

/** Колокольчик: карточка «Уведомления». */
private const val BELL_TOP = 0.16f
private const val BELL_SIDE = 0.20f
private const val BELL_BASE = 0.70f
private const val BELL_CLAPPER_TOP = 0.78f
private const val BELL_CLAPPER_SIDE = 0.12f

/** Будильник: циферблат со стрелками и двумя «ушками». */
private const val DIAL_RADIUS = 0.33f
private const val DIAL_CENTER_Y = 0.56f
private const val HAND_TOP = 0.34f
private const val HAND_RIGHT = 0.66f
private const val EAR_OUTER = 0.18f
private const val EAR_INNER = 0.30f
private const val EAR_TOP = 0.10f

/** Молния: карточка «Автозапуск без ограничений». */
private const val BOLT_TOP_X = 0.56f
private const val BOLT_TOP_Y = 0.12f
private const val BOLT_LEFT_X = 0.24f
private const val BOLT_MID_Y = 0.56f
private const val BOLT_NOTCH_X = 0.46f
private const val BOLT_BOTTOM_X = 0.44f
private const val BOLT_BOTTOM_Y = 0.88f
private const val BOLT_RIGHT_X = 0.76f
private const val BOLT_NOTCH_Y = 0.44f

/** Кружок с восклицательным знаком: плашка-пояснение и косвенные признаки. */
private const val INFO_RADIUS = 0.38f
private const val INFO_STEM_TOP = 0.42f
private const val INFO_STEM_BOTTOM = 0.70f
private const val INFO_DOT = 0.30f

/** Колокольчик (`Onboarding.dc.html`, карточка «Уведомления»). */
@Composable
internal fun BellGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.IconPlateGlyph) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawPath(
            path = Path().apply {
                moveTo(side * BELL_SIDE, side * BELL_BASE)
                lineTo(side * (1f - BELL_SIDE), side * BELL_BASE)
                lineTo(side * (1f - BELL_SIDE - BELL_CLAPPER_SIDE), side * BELL_TOP)
                lineTo(side * (BELL_SIDE + BELL_CLAPPER_SIDE), side * BELL_TOP)
                close()
            },
            color = color,
            style = stroke(side),
        )
        drawPath(
            path = Path().apply {
                moveTo(side * (MIDDLE - BELL_CLAPPER_SIDE), side * BELL_CLAPPER_TOP)
                lineTo(side * (MIDDLE + BELL_CLAPPER_SIDE), side * BELL_CLAPPER_TOP)
            },
            color = color,
            style = stroke(side),
        )
    }
}

/** Будильник (`Onboarding.dc.html`, карточка «Точные будильники»). */
@Composable
internal fun AlarmClockGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.IconPlateGlyph) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        val dialCenter = Offset(side * MIDDLE, side * DIAL_CENTER_Y)
        drawCircle(color, radius = side * DIAL_RADIUS, center = dialCenter, style = stroke(side))
        drawPath(
            path = Path().apply {
                moveTo(side * MIDDLE, side * HAND_TOP)
                lineTo(side * MIDDLE, side * DIAL_CENTER_Y)
                lineTo(side * HAND_RIGHT, side * DIAL_CENTER_Y)
            },
            color = color,
            style = stroke(side),
        )
        listOf(EAR_OUTER to EAR_INNER, (1f - EAR_OUTER) to (1f - EAR_INNER)).forEach { (outer, inner) ->
            drawPath(
                path = Path().apply {
                    moveTo(side * outer, side * EAR_TOP)
                    lineTo(side * inner, side * (EAR_TOP + EAR_TOP))
                },
                color = color,
                style = stroke(side),
            )
        }
    }
}

/** Молния (`Onboarding.dc.html`, карточка «Автозапуск без ограничений»). */
@Composable
internal fun BoltGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.IconPlateGlyph) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawPath(
            path = Path().apply {
                moveTo(side * BOLT_TOP_X, side * BOLT_TOP_Y)
                lineTo(side * BOLT_LEFT_X, side * BOLT_MID_Y)
                lineTo(side * BOLT_NOTCH_X, side * BOLT_MID_Y)
                lineTo(side * BOLT_BOTTOM_X, side * BOLT_BOTTOM_Y)
                lineTo(side * BOLT_RIGHT_X, side * BOLT_NOTCH_Y)
                lineTo(side * BOLT_NOTCH_X, side * BOLT_NOTCH_Y)
                close()
            },
            color = color,
            style = stroke(side),
        )
    }
}

/** Батарейка: карточка «Энергосбережение» (ОВ-106). */
private const val BATTERY_LEFT = 0.16f
private const val BATTERY_TOP = 0.30f
private const val BATTERY_WIDTH = 0.58f
private const val BATTERY_HEIGHT = 0.40f
private const val BATTERY_RADIUS = 0.08f
private const val BATTERY_CAP_LEFT = 0.76f
private const val BATTERY_CAP_TOP = 0.41f
private const val BATTERY_CAP_WIDTH = 0.10f
private const val BATTERY_CAP_HEIGHT = 0.18f

/** Замок: карточка «Закрепление в недавних» — тот же знак, что появляется в списке недавних. */
private const val LOCK_SHACKLE_LEFT = 0.30f
private const val LOCK_SHACKLE_TOP = 0.12f
private const val LOCK_SHACKLE_SIZE = 0.40f
private const val LOCK_BODY_LEFT = 0.22f
private const val LOCK_BODY_TOP = 0.44f
private const val LOCK_BODY_WIDTH = 0.56f
private const val LOCK_BODY_HEIGHT = 0.42f
private const val LOCK_BODY_RADIUS = 0.10f
private const val LOCK_DOT_RADIUS = 0.05f
private const val LOCK_DOT_Y = 0.64f

/** Батарейка с кружком-клеммой (`Onboarding.dc.html`, карточка «Энергосбережение»). */
@Composable
internal fun BatteryGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.IconPlateGlyph) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawRoundRect(
            color = color,
            topLeft = Offset(side * BATTERY_LEFT, side * BATTERY_TOP),
            size = Size(side * BATTERY_WIDTH, side * BATTERY_HEIGHT),
            cornerRadius = CornerRadius(side * BATTERY_RADIUS),
            style = stroke(side),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(side * BATTERY_CAP_LEFT, side * BATTERY_CAP_TOP),
            size = Size(side * BATTERY_CAP_WIDTH, side * BATTERY_CAP_HEIGHT),
            cornerRadius = CornerRadius(side * LOCK_DOT_RADIUS),
        )
    }
}

/** Замок (`Onboarding.dc.html`, карточка «Закрепление в недавних»). */
@Composable
internal fun PinGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.IconPlateGlyph) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(side * LOCK_SHACKLE_LEFT, side * LOCK_SHACKLE_TOP),
            size = Size(side * LOCK_SHACKLE_SIZE, side * LOCK_SHACKLE_SIZE),
            style = stroke(side),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(side * LOCK_BODY_LEFT, side * LOCK_BODY_TOP),
            size = Size(side * LOCK_BODY_WIDTH, side * LOCK_BODY_HEIGHT),
            cornerRadius = CornerRadius(side * LOCK_BODY_RADIUS),
            style = stroke(side),
        )
        drawCircle(color, radius = side * LOCK_DOT_RADIUS, center = Offset(side * MIDDLE, side * LOCK_DOT_Y))
    }
}

/** Кружок с восклицательным знаком: плашка «Проверить можно сразу…» и косвенные признаки. */
@Composable
internal fun InfoGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.BannerIcon) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawCircle(color, radius = side * INFO_RADIUS, center = center, style = stroke(side))
        drawPath(
            path = Path().apply {
                moveTo(side * MIDDLE, side * INFO_STEM_TOP)
                lineTo(side * MIDDLE, side * INFO_STEM_BOTTOM)
            },
            color = color,
            style = stroke(side),
        )
        drawCircle(color, radius = side * STROKE_RATIO, center = Offset(side * MIDDLE, side * INFO_DOT))
    }
}

private fun stroke(side: Float): Stroke =
    Stroke(width = side * STROKE_RATIO, cap = StrokeCap.Round, join = StrokeJoin.Round)
