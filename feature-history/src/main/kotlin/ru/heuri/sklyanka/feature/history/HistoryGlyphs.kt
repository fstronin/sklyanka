package ru.heuri.sklyanka.feature.history

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import ru.heuri.sklyanka.ui.modifier.minTouchTarget
import ru.heuri.sklyanka.ui.token.SklyankaSizes

/**
 * Знаки экрана истории: шевроны переключателя месяца.
 *
 * Рисуются здесь, а не берутся из `core-ui`: в каталоге компонентов шеврона нет, а
 * `feature-*` друг от друга не зависят — общий знак пришлось бы завести в дизайн-системе,
 * то есть расширить её из экранной задачи. Геометрия задана долями стороны, а не
 * пикселями: размер приходит токеном (НФТ-47).
 */
@Composable
internal fun ChevronGlyph(color: Color, pointsLeft: Boolean, size: Dp = SklyankaSizes.MonthArrowGlyph) {
    Canvas(Modifier.size(size)) {
        val side = this.size.minDimension
        val near = if (pointsLeft) CHEVRON_FAR else CHEVRON_NEAR
        val far = if (pointsLeft) CHEVRON_NEAR else CHEVRON_FAR
        drawPath(
            path = Path().apply {
                moveTo(side * near, side * CHEVRON_TOP)
                lineTo(side * far, side * MIDDLE)
                lineTo(side * near, side * CHEVRON_BOTTOM)
            },
            color = color,
            style = Stroke(width = side * STROKE, cap = StrokeCap.Round),
        )
    }
}

/**
 * Круглая кнопка с одним знаком: стрелки месяца и стрелка «Назад».
 *
 * Подпись для чтения с экрана обязательна: видимого текста у кнопки нет вовсе (НФТ-30).
 * Цель нажатия доводится до 44 dp модификатором, а не размером рисунка: макет рисует
 * стрелки 32×32, и увеличение рисунка сломало бы плотность (НФТ-29, ОВ-17).
 */
@Composable
internal fun GlyphButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    glyph: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .minTouchTarget()
            .size(SklyankaSizes.MonthArrow)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        glyph()
    }
}

private const val MIDDLE = 0.5f
private const val CHEVRON_NEAR = 0.34f
private const val CHEVRON_FAR = 0.66f
private const val CHEVRON_TOP = 0.2f
private const val CHEVRON_BOTTOM = 0.8f
private const val STROKE = 0.1f
