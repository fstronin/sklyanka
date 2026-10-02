package ru.stohastika.sklyanka.ui.component

import androidx.compose.ui.geometry.Rect
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.model.TabletShape

/**
 * Оптический бокс силуэтов «Вид таблетки» (ОВ-121).
 *
 * Проверяется отрисовка, а не константы: `drawTabletShape` рисует силуэт в `ImageBitmap`,
 * и по чернилам измеряется габарит. Все пять силуэтов ряда обязаны иметь одну ширину
 * по внешнему контуру (0,84 стороны поля, с обводкой ≤0,91) и общий центр по обеим осям;
 * круг — диаметр, овал и продолговатая — ширину, треугольник — основание.
 */
@RunWith(AndroidJUnit4::class)
class MedicationGlyphGeometryTest {

    @Test
    fun allTabletShapesShareOneOuterWidth() {
        val widths = TabletShape.entries.associateWith { bounds(it).width }
        val distinct = widths.values.map { it.toInt() }.distinct()
        assertTrue("the five silhouettes must be one width: $widths", distinct.size == 1)
        assertEquals(
            "the contour width must be 0,91 of the field (0,84 + stroke)",
            EXPECTED_OUTER_WIDTH.toDouble(),
            distinct.single().toDouble(),
            TOLERANCE.toDouble(),
        )
    }

    @Test
    fun allTabletShapesShareOneCentre() {
        TabletShape.entries.forEach { shape ->
            val box = bounds(shape)
            assertTrue(
                "the silhouette $shape must be centred: $box",
                kotlin.math.abs(box.center.x - SIDE / 2f) <= TOLERANCE &&
                    kotlin.math.abs(box.center.y - SIDE / 2f) <= TOLERANCE,
            )
        }
    }

    @Test
    fun theCircleIsADiameterAndTheTriangleABaseOfTheSameWidth() {
        val circle = bounds(TabletShape.ROUND)
        val triangle = bounds(TabletShape.TRIANGULAR)
        assertEquals(
            "the circle must be a circle",
            circle.width.toDouble(),
            circle.height.toDouble(),
            TOLERANCE.toDouble(),
        )
        assertEquals(
            "the triangle base must equal the circle diameter",
            circle.width.toDouble(),
            triangle.width.toDouble(),
            TOLERANCE.toDouble(),
        )
        assertTrue(
            "the triangle must keep the shape's proportion, not fill the box",
            triangle.height < circle.height,
        )
    }

    @Test
    fun theScoredCircleKeepsItsScoreInsideTheBox() {
        val plain = bounds(TabletShape.ROUND)
        val scored = bounds(TabletShape.ROUND_SCORED)
        assertEquals(plain.width.toDouble(), scored.width.toDouble(), TOLERANCE.toDouble())
        assertEquals(plain.height.toDouble(), scored.height.toDouble(), TOLERANCE.toDouble())
    }

    private fun bounds(shape: TabletShape): Rect {
        val ink = requireNotNull(InkProbe.shapeBounds(shape, SIDE)) { "the silhouette $shape must have ink" }
        return Rect(ink.left.toFloat(), ink.top.toFloat(), ink.right + 1f, ink.bottom + 1f)
    }

    private companion object {
        const val SIDE = 64

        /** 0,91 · 64 ≈ 58,2; округление пикселей оставляет полтора пикселя запаса. */
        const val EXPECTED_OUTER_WIDTH = 58
        const val TOLERANCE = 1
    }
}
