package ru.heuri.sklyanka.ui.token

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Правило выбора варианта знака по размеру (`TDD.md` §4, `task.md` критерий 2).
 *
 * Проверяются именно границы, а не «типичные» размеры: правило из пяти ступеней
 * ошибается ровно на стыках, и ошибка эта тихая — знак просто выглядит не так,
 * как задумано, и никто не может сказать, где именно.
 *
 * Числа границ — из таблицы `task.md`: 90 и выше стекло, 45–89, 29–44, 19–28
 * и 18 с меньшим — три ступени редукции и предельная.
 */
class MarkVariantTest {

    @Test
    fun `the ladder picks the variant declared for every boundary size`() {
        val expected = mapOf(
            170 to MarkVariant.Glass,
            92 to MarkVariant.Glass,
            90 to MarkVariant.Glass,
            89 to MarkVariant.FlatRingsAndDot,
            72 to MarkVariant.FlatRingsAndDot,
            45 to MarkVariant.FlatRingsAndDot,
            44 to MarkVariant.FlatRingsAndDotBold,
            29 to MarkVariant.FlatRingsAndDotBold,
            28 to MarkVariant.FlatTwoRings,
            19 to MarkVariant.FlatTwoRings,
            18 to MarkVariant.FlatRingAndLargeDot,
            12 to MarkVariant.FlatRingAndLargeDot,
        )
        expected.forEach { (size, variant) ->
            assertEquals("size $size", variant, markVariantFor(size.dp))
        }
    }

    @Test
    fun `the four sizes of the mock-up land on four different steps`() {
        // SklyankaScale.dc.html показывает 72, 44, 28 и 18 — и это четыре разные
        // картинки, а не одна в четырёх масштабах.
        val steps = listOf(72, 44, 28, 18).map { markVariantFor(it.dp) }
        assertEquals(steps.size, steps.distinct().size)
        assertTrue("every step of the mock-up is flat", steps.all { it.isFlat })
    }

    @Test
    fun `line thickness grows as the mark shrinks`() {
        val outerStrokes = FLAT_LADDER.map { FLAT_MARK_SHAPES.getValue(it).outerStroke }
        assertEquals(outerStrokes.sorted(), outerStrokes)
        assertEquals(outerStrokes.distinct(), outerStrokes)
    }

    @Test
    fun `the number of elements falls as the mark shrinks`() {
        val counts = FLAT_LADDER.map { FLAT_MARK_SHAPES.getValue(it).elementCount }
        assertEquals("elements may repeat but never grow", counts.sortedDescending(), counts)
        assertTrue("the last step keeps at least a ring and a dot", counts.last() >= MIN_ELEMENTS)
    }

    @Test
    fun `at twenty eight pixels the centre dot is gone and at eighteen the inner ring is`() {
        val twentyEight = FLAT_MARK_SHAPES.getValue(markVariantFor(28.dp))
        assertNull("the dot merges with the inner ring at 28", twentyEight.dotRadius)
        assertNotNull(twentyEight.innerRadius)

        val eighteen = FLAT_MARK_SHAPES.getValue(markVariantFor(18.dp))
        assertNull("only one ring is left at 18", eighteen.innerRadius)
        assertNotNull("and the dot comes back, large", eighteen.dotRadius)
        assertTrue((eighteen.dotRadius ?: 0f) > twentyEight.outerStroke)
    }

    @Test
    fun `no step draws outside the two hundred field`() {
        // Обводка выходит за радиус на половину толщины; вылезшая за поле линия
        // обрезалась бы краем холста, и кольцо превратилось бы в скобку.
        FLAT_MARK_SHAPES.forEach { (variant, shape) ->
            val outermost = shape.outerRadius + shape.outerStroke / 2f
            assertTrue("$variant reaches $outermost", outermost <= SklyankaMarkTokens.FIELD / 2f)
        }
        val glassOutermost = SklyankaMarkTokens.RIM_RADIUS + SklyankaMarkTokens.RIM_STROKE / 2f
        assertTrue(glassOutermost <= SklyankaMarkTokens.FIELD / 2f)
    }

    @Test
    fun `every flat step of the ladder has a drawn shape`() {
        MarkVariant.entries.filter { it.isFlat }.forEach { variant ->
            assertNotNull("no geometry for $variant", FLAT_MARK_SHAPES[variant])
        }
        assertEquals(MarkVariant.entries.count { it.isFlat }, FLAT_MARK_SHAPES.size)
    }

    @Test
    fun `glass keeps all five traits of the mark`() {
        // Пять признаков из критерия 3: кольца, серп, тёплая дуга, каустика, точка.
        assertEquals(RING_COUNT, SklyankaMarkTokens.RING_RADII.size)
        assertEquals(RING_COUNT, SklyankaMarkTokens.RING_STROKES.size)
        assertEquals(RING_COUNT, SklyankaMarkTokens.RING_ALPHAS_ON_DARK.size)
        assertEquals(RING_COUNT, SklyankaMarkTokens.RING_ALPHAS_ON_LIGHT.size)
        assertTrue(SklyankaMarkTokens.CRESCENT_SWEEP > 0f)
        assertTrue(SklyankaMarkTokens.WARM_ARC_SWEEP > 0f)
        assertTrue(SklyankaMarkTokens.CAUSTIC_RADIUS_X > SklyankaMarkTokens.CAUSTIC_RADIUS_Y)
        assertTrue(SklyankaMarkTokens.DOT_RADIUS > 0f)
    }

    @Test
    fun `rings thin out and thicken in density towards the centre`() {
        val radii = SklyankaMarkTokens.RING_RADII
        assertEquals("rings run from the rim inwards", radii.sortedDescending(), radii)
        val alphas = SklyankaMarkTokens.RING_ALPHAS_ON_DARK
        assertEquals("density grows towards the centre", alphas.sorted(), alphas)
    }

    @Test
    fun `the warm arc is the same in both environments`() {
        // Дуга — то, что связывает тёмную и светлую версии (критерий 4).
        val accent = darkColors().accent
        val dark = glassMarkColors(MarkEnvironment.Dark, accent)
        val light = glassMarkColors(MarkEnvironment.Light, accent)
        assertEquals(dark.warmArc, light.warmArc)
        assertNotNull("the dark rendering keeps the reflection", dark.reflection)
        assertNull("the light one has none — a highlight on light reads as dirt", light.reflection)
    }

    private companion object {
        /** Ступени редукции сверху вниз: от самой крупной к предельной. */
        val FLAT_LADDER = listOf(
            MarkVariant.FlatRingsAndDot,
            MarkVariant.FlatRingsAndDotBold,
            MarkVariant.FlatTwoRings,
            MarkVariant.FlatRingAndLargeDot,
        )
        const val RING_COUNT = 3
        const val MIN_ELEMENTS = 2
    }
}
