package ru.stohastika.sklyanka.feature.meds

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/**
 * Графика экранов лекарств, оставшаяся после переезда силуэтов в дизайн-систему.
 *
 * Силуэты формы и вида («форма + вид») живут в `core-ui` (`MedicationGlyph`), потому что
 * их носят три поверхности — список, форма и карточка (ОВ-94). Здесь — знаки, принадлежащие
 * только экранам лекарств: плюс, минус, шеврон, стрелка «Назад», крестик и часы.
 *
 * Рисуется, а не подключается набором: продуктового набора иконок в проекте нет.
 * Пропорции вынесены в константы файла (НФТ-47).
 */

/** Толщина штриха относительно стороны — та же, что в графике дизайн-системы. */
private const val STROKE_RATIO = 0.09f

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

/**
 * Стрелка «Назад» в шапке карточки лекарства (`MedCard.dc.html`).
 *
 * Тот же шеврон, отражённый влево. Отдельная функция, а не поворот существующего:
 * поворот в разметке пришлось бы задавать углом по месту, а угол — такое же
 * неподменяемое значение, как размер.
 */
@Composable
internal fun BackGlyph(color: Color, modifier: Modifier = Modifier, size: Dp = SklyankaSizes.IconPlateGlyph) {
    Canvas(modifier.size(size)) {
        val side = this.size.minDimension
        drawPath(
            path = Path().apply {
                moveTo(side * CHEVRON_RIGHT, side * CHEVRON_TOP)
                lineTo(side * CHEVRON_LEFT, side * MIDDLE)
                lineTo(side * CHEVRON_RIGHT, side * CHEVRON_BOTTOM)
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
        drawCircle(color = color, radius = side * DIAL_RADIUS, style = roundStroke(side))
        drawPath(
            path = Path().apply {
                moveTo(side * MIDDLE, side * HAND_TOP)
                lineTo(side * MIDDLE, side * HAND_BOTTOM)
                lineTo(side * HAND_RIGHT, side * HAND_BOTTOM)
            },
            color = color,
            style = roundStroke(side),
        )
    }
}

private fun roundStroke(side: Float) =
    Stroke(width = side * STROKE_RATIO, cap = StrokeCap.Round, join = StrokeJoin.Round)
