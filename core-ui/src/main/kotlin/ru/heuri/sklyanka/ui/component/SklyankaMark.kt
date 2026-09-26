package ru.heuri.sklyanka.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import ru.heuri.sklyanka.ui.theme.SklyankaTheme
import ru.heuri.sklyanka.ui.token.FLAT_MARK_SHAPES
import ru.heuri.sklyanka.ui.token.FlatMarkShape
import ru.heuri.sklyanka.ui.token.GlassMarkColors
import ru.heuri.sklyanka.ui.token.MarkEnvironment
import ru.heuri.sklyanka.ui.token.MarkVariant
import ru.heuri.sklyanka.ui.token.SklyankaMarkTokens
import ru.heuri.sklyanka.ui.token.flatMarkColor
import ru.heuri.sklyanka.ui.token.glassMarkColors
import ru.heuri.sklyanka.ui.token.markVariantFor

/**
 * Фирменный знак «Склянки»: донце толстого стекла, вид сверху.
 *
 * Экран просит знак нужного размера и получает правильный вариант — стеклянную
 * отрисовку или одну из трёх ступеней плоской редукции. Выбирать ступень по месту
 * нельзя и не нужно: правило записано один раз в [markVariantFor], и только так знак
 * остаётся одним и тем же во всех местах приложения (`task.md`, критерий 2).
 *
 * Знак рисуется, а не подключается растром: у него одна геометрия на поле 200×200
 * ([SklyankaMarkTokens]), из которой выводятся и эта отрисовка, и векторные ресурсы
 * иконки приложения. Растровых файлов у знака нет вовсе — кольца обязаны остаться
 * кольцами на любой плотности экрана.
 *
 * @param contentDescription описание для чтения с экрана; обязательно (НФТ-30) —
 *   у знака нет видимого текста, и без описания читалка сообщит «изображение».
 */
@Composable
fun SklyankaMark(
    contentDescription: String,
    size: Dp,
    modifier: Modifier = Modifier,
    environment: MarkEnvironment = MarkEnvironment.Dark,
) {
    // Акцент берётся ролью темы, а не константой знака: когда появится палитра
    // профилей (ОВ-10), тёплая дуга обязана будет согласоваться с ней сама.
    val glass = glassMarkColors(environment, SklyankaTheme.colors.accent)
    val flat = flatMarkColor(environment)
    val variant = markVariantFor(size)
    Canvas(
        modifier
            .size(size)
            .semanticsLabel(contentDescription),
    ) {
        if (variant == MarkVariant.Glass) drawGlassVariant(glass) else drawFlatVariant(variant, flat)
    }
}

/** Стеклянная отрисовка в поле знака. */
internal fun DrawScope.drawGlassVariant(colors: GlassMarkColors) = inMarkField { drawGlassMark(colors) }

/** Плоская ступень в поле знака. Ступень [variant] обязана быть плоской. */
internal fun DrawScope.drawFlatVariant(variant: MarkVariant, color: Color) =
    inMarkField { drawFlatMark(FLAT_MARK_SHAPES.getValue(variant), color) }

/**
 * Поле знака внутри поля рисования.
 *
 * Координаты слоёв заданы на поле 200×200, поэтому всё рисование идёт внутри одного
 * преобразования: сдвиг к центру квадратного поля и масштаб «сторона / 200». Толщины
 * линий масштабируются тем же преобразованием — отдельного пересчёта им не нужно,
 * и именно поэтому оно одно на обе ветки.
 */
private inline fun DrawScope.inMarkField(crossinline block: DrawScope.() -> Unit) {
    val side = size.minDimension
    translate(left = (size.width - side) / HALF, top = (size.height - side) / HALF) {
        scale(scale = side / SklyankaMarkTokens.FIELD, pivot = Offset.Zero) { block() }
    }
}

/**
 * Полная стеклянная отрисовка. Порядок слоёв — снизу вверх, как в макете.
 *
 * Каустика первая: она лежит ПОД знаком, световым пятном на полотне; нарисованная
 * сверху, она замутила бы кольца.
 */
@Suppress("LongMethod")
// Метод длинный по составу знака, а не по сложности: девять слоёв подряд, каждый
// в две-три строки. Разрезать его на «кольца» и «дуги» значит спрятать порядок
// наложения, а именно он и делает знак стеклом.
private fun DrawScope.drawGlassMark(colors: GlassMarkColors) {
    val center = Offset(SklyankaMarkTokens.CENTER, SklyankaMarkTokens.CENTER)

    // Каустика: круг радиуса 58, сплющенный по вертикали до 19. Сплющивается
    // вместе с градиентом — в SVG он задан долями описанного прямоугольника
    // и растягивается так же.
    val causticCenter = Offset(SklyankaMarkTokens.CENTER, SklyankaMarkTokens.CAUSTIC_CENTER_Y)
    scale(
        scaleX = 1f,
        scaleY = SklyankaMarkTokens.CAUSTIC_RADIUS_Y / SklyankaMarkTokens.CAUSTIC_RADIUS_X,
        pivot = causticCenter,
    ) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.caustic, colors.caustic.copy(alpha = 0f)),
                center = causticCenter,
                radius = SklyankaMarkTokens.CAUSTIC_RADIUS_X,
            ),
            radius = SklyankaMarkTokens.CAUSTIC_RADIUS_X,
            center = causticCenter,
        )
    }

    // Тело: радиальный градиент, смещённый в верхний левый угол.
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = colors.bodyStops.zip(colors.body).toTypedArray(),
            center = SklyankaMarkTokens.BODY_GRADIENT_CENTER,
            radius = SklyankaMarkTokens.BODY_GRADIENT_RADIUS,
        ),
        radius = SklyankaMarkTokens.RIM_RADIUS,
        center = center,
    )

    // Ободок: линейный градиент наискось — свет идёт из верхнего левого угла.
    drawCircle(
        brush = Brush.linearGradient(
            colorStops = colors.rimStops.zip(colors.rim).toTypedArray(),
            start = SklyankaMarkTokens.RIM_GRADIENT_START,
            end = SklyankaMarkTokens.RIM_GRADIENT_END,
        ),
        radius = SklyankaMarkTokens.RIM_RADIUS,
        center = center,
        style = Stroke(width = SklyankaMarkTokens.RIM_STROKE),
    )

    // Три кольца: толщина убывает к центру, плотность нарастает.
    SklyankaMarkTokens.RING_RADII.forEachIndexed { index, radius ->
        drawCircle(
            color = colors.rings[index],
            radius = radius,
            center = center,
            style = Stroke(width = SklyankaMarkTokens.RING_STROKES[index]),
        )
    }

    // Световой серп по верхнему краю — заливка кольцевого сектора.
    drawPath(path = crescentPath(), color = colors.crescent)

    // Слабое отражение по нижнему левому краю; в светлой среде слоя нет.
    colors.reflection?.let { reflection ->
        drawPath(
            path = reflectionPath(),
            color = reflection,
            style = Stroke(width = SklyankaMarkTokens.REFLECTION_STROKE, cap = StrokeCap.Round),
        )
    }

    // Тёплая дуга преломлённого луча по нижней правой четверти.
    drawPath(
        path = warmArcPath(),
        color = colors.warmArc,
        style = Stroke(width = SklyankaMarkTokens.WARM_ARC_STROKE, cap = StrokeCap.Round),
    )

    drawCircle(color = colors.dot, radius = SklyankaMarkTokens.DOT_RADIUS, center = center)
}

/** Плоская ступень: одна краска, без прозрачности и градиентов. */
private fun DrawScope.drawFlatMark(shape: FlatMarkShape, color: Color) {
    val center = Offset(SklyankaMarkTokens.CENTER, SklyankaMarkTokens.CENTER)
    drawCircle(
        color = color,
        radius = shape.outerRadius,
        center = center,
        style = Stroke(width = shape.outerStroke),
    )
    val innerRadius = shape.innerRadius
    val innerStroke = shape.innerStroke
    if (innerRadius != null && innerStroke != null) {
        drawCircle(color = color, radius = innerRadius, center = center, style = Stroke(width = innerStroke))
    }
    shape.dotRadius?.let { dot -> drawCircle(color = color, radius = dot, center = center) }
}

/**
 * Серп: кольцевой сектор между радиусами 76 и 67.
 *
 * Внешняя дуга идёт по часовой стрелке, внутренняя возвращается назад, контур
 * замыкается — получается «долька» постоянной толщины, а не обводка, которая
 * на маленьком размере слиплась бы с ободком.
 */
private fun crescentPath(): Path = Path().apply {
    arcTo(
        rect = fieldRect(SklyankaMarkTokens.CRESCENT_OUTER_RADIUS),
        startAngleDegrees = SklyankaMarkTokens.CRESCENT_START_ANGLE,
        sweepAngleDegrees = SklyankaMarkTokens.CRESCENT_SWEEP,
        forceMoveTo = true,
    )
    arcTo(
        rect = fieldRect(SklyankaMarkTokens.CRESCENT_INNER_RADIUS),
        startAngleDegrees = SklyankaMarkTokens.CRESCENT_START_ANGLE + SklyankaMarkTokens.CRESCENT_SWEEP,
        sweepAngleDegrees = -SklyankaMarkTokens.CRESCENT_SWEEP,
        // false: от конца внешней дуги к началу внутренней проводится отрезок —
        // это торец серпа, в SVG он записан командой L.
        forceMoveTo = false,
    )
    close()
}

/** Дуга отражения: единственный слой со смещённым центром, см. [SklyankaMarkTokens]. */
private fun reflectionPath(): Path = Path().apply {
    arcTo(
        rect = Rect(center = SklyankaMarkTokens.REFLECTION_CENTER, radius = SklyankaMarkTokens.REFLECTION_RADIUS),
        startAngleDegrees = SklyankaMarkTokens.REFLECTION_START_ANGLE,
        sweepAngleDegrees = SklyankaMarkTokens.REFLECTION_SWEEP,
        forceMoveTo = true,
    )
}

/** Тёплая дуга: обводка по нижней правой четверти ободка. */
private fun warmArcPath(): Path = Path().apply {
    arcTo(
        rect = fieldRect(SklyankaMarkTokens.WARM_ARC_RADIUS),
        startAngleDegrees = SklyankaMarkTokens.WARM_ARC_START_ANGLE,
        sweepAngleDegrees = SklyankaMarkTokens.WARM_ARC_SWEEP,
        forceMoveTo = true,
    )
}

/** Квадрат, описанный вокруг окружности заданного радиуса с центром поля. */
private fun fieldRect(radius: Float): Rect = Rect(
    offset = Offset(SklyankaMarkTokens.CENTER - radius, SklyankaMarkTokens.CENTER - radius),
    size = Size(radius + radius, radius + radius),
)

/** Половина: центрирование квадратного поля знака внутри поля рисования. */
private const val HALF = 2f
