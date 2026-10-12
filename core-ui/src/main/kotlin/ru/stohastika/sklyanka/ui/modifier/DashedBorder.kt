package ru.stohastika.sklyanka.ui.modifier

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/**
 * Пунктирная граница — единственный способ нарисовать в системе пунктир.
 *
 * `Modifier.border` пунктир не умеет, а он несёт смысл в двух местах (§2.1, §4.1):
 * пунктиром помечен блок, которого ещё нет в расписании, — «по потребности» в карточке
 * приёма и кнопка «Добавить время» на форме лекарства. Сплошная граница на месте
 * пунктира читалась бы как уже созданный приём, поэтому реализация живёт в дизайн-системе,
 * а не рядом с одним из экранов: две копии пунктира разошлись бы штрихом и толщиной.
 *
 * Штрих и разрыв — токены [SklyankaSizes.DashOn]/[SklyankaSizes.DashOff], а не литералы:
 * пунктир обязан выглядеть одинаково при любой плотности экрана.
 *
 * Радиус — параметр, а не форма: границы блока «по потребности» (карточная) и кнопки
 * «Добавить время» (средняя) разные, а пунктир у них один.
 */
fun Modifier.dashedBorder(width: Dp, color: Color, radius: Dp): Modifier = this.then(
    Modifier.drawBehind {
        val strokeWidth = width.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(
                width = strokeWidth,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(SklyankaSizes.DashOn.toPx(), SklyankaSizes.DashOff.toPx()),
                ),
            ),
        )
    },
)
