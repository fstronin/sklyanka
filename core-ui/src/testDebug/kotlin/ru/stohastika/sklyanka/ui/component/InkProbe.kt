package ru.stohastika.sklyanka.ui.component

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import ru.stohastika.sklyanka.domain.model.TabletShape

/**
 * Измерение чернил цифр и силуэтов для проверок ОВ-117–ОВ-121.
 *
 * Чернила берутся отрисовкой уже посчитанной разметки (или силуэта) в `ImageBitmap`:
 * проверка видит то, что увидит человек, и не повторяет метрики шрифта числом.
 */
internal object InkProbe {

    /** Границы непрозрачных пикселей надписи. */
    fun inkBounds(layout: TextLayoutResult): IntBounds? {
        val width = layout.size.width
        val height = layout.size.height
        if (width <= 0 || height <= 0) return null
        val bitmap = ImageBitmap(width, height)
        layout.multiParagraph.paint(canvas = Canvas(bitmap), color = Color.Black)
        return pixelBounds(bitmap)
    }

    /** Границы непрозрачных пикселей силуэта вида таблетки. */
    fun shapeBounds(shape: TabletShape, side: Int): IntBounds? = pixelBounds(shapeBitmap(shape, side))

    private fun shapeBitmap(shape: TabletShape, side: Int): ImageBitmap {
        val bitmap = ImageBitmap(side, side)
        androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
            density = androidx.compose.ui.unit.Density(1f),
            layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
            canvas = Canvas(bitmap),
            size = androidx.compose.ui.geometry.Size(side.toFloat(), side.toFloat()),
        ) {
            drawTabletShape(shape, side.toFloat(), Color.Black, thinStroke(side.toFloat()))
        }
        return bitmap
    }

    private fun pixelBounds(bitmap: ImageBitmap): IntBounds? {
        val pixels = bitmap.toPixelMap()
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                if (pixels[x, y].alpha > INK_ALPHA) {
                    left = minOf(left, x)
                    top = minOf(top, y)
                    right = maxOf(right, x)
                    bottom = maxOf(bottom, y)
                }
            }
        }
        return if (left == Int.MAX_VALUE) null else IntBounds(left, top, right, bottom)
    }

    /** Разметка надписи на узле семантики, если она есть. */
    fun textLayout(node: SemanticsNode): TextLayoutResult? {
        val results = mutableListOf<TextLayoutResult>()
        node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.firstOrNull()
    }

    fun hasDigits(node: SemanticsNode): Boolean = textLayout(node)?.layoutInput?.text?.text?.any(Char::isDigit) == true

    private const val INK_ALPHA = 0.5f
}

/** Границы чернил в пикселях, левый-верхний и правый-нижний включительно. */
internal data class IntBounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left + 1
    val height: Int get() = bottom - top + 1
}
