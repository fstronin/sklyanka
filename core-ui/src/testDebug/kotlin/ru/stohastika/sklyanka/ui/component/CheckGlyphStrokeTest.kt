package ru.stohastika.sklyanka.ui.component

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import kotlin.math.sqrt

/**
 * Толщина штриха галочки: успех шторки — отдельная от общей доли.
 *
 * ОВ-125 задаёт у 72-й галочки штрих ровно 6 dp, тогда как отметки «Есть» и прочие
 * места живут на общей доле [defaultCheckStroke]. Проверка рисует оба варианта
 * в поле 72 dp и измеряет чернила: ширина штриха на 45° даёт вертикальный след
 * `ширина · √2`, поэтому меряется он, а не константа.
 */
@RunWith(AndroidJUnit4::class)
class CheckGlyphStrokeTest {

    @Test
    fun theSuccessMarkStrokeIsSixOfSeventyTwo() {
        assertEquals("the success mark stroke is a named token", 6.dp, SklyankaSizes.BottomSheetSuccessMarkStroke)
    }

    @Test
    fun theSuccessMarkStrokeIsThinnerThanTheSharedGlyphRatio() {
        val success = strokeThickness(SklyankaSizes.BottomSheetSuccessMarkStroke)
        val shared = strokeThickness(defaultCheckStroke(SklyankaSizes.BottomSheetSuccessMark))

        assertEquals("6 dp at 45°", 6.0 * SQRT_TWO, success.toDouble(), TOLERANCE)
        assertEquals("the shared 0,13 ratio keeps its width", 9.36 * SQRT_TWO, shared.toDouble(), TOLERANCE)
        assertTrue("the success mark must be thinner: $success vs $shared", success < shared)
    }

    /** Самый длинный вертикальный след чернил в колонке, пересекающей только нижнюю ногу. */
    private fun strokeThickness(strokeWidth: Dp): Int {
        val bitmap = ImageBitmap(SIDE, SIDE)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap),
            size = Size(SIDE.toFloat(), SIDE.toFloat()),
        ) {
            drawCheckGlyph(Color.Black, strokeWidth)
        }
        val pixels = bitmap.toPixelMap()
        var thickest = 0
        LEG_SAMPLE_COLUMNS.forEach { x ->
            var run = 0
            repeat(SIDE) { y ->
                run = if (pixels[x, y].alpha > INK_ALPHA) run + 1 else 0
                if (run > thickest) thickest = run
            }
        }
        return thickest
    }

    private companion object {
        const val SIDE = 72
        const val INK_ALPHA = 0.5f
        const val TOLERANCE = 2.0

        /** Колонки, где чернила даёт только нога «вниз-вправо», без скруглений концов. */
        val LEG_SAMPLE_COLUMNS = 17..23

        val SQRT_TWO = sqrt(2.0)
    }
}
