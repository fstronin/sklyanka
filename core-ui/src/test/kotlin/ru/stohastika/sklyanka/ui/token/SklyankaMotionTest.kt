package ru.stohastika.sklyanka.ui.token

import androidx.compose.animation.core.CubicBezierEasing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SklyankaMotionTest {

    @Test
    fun `the durations keep the values of the motion specification`() {
        assertEquals(150, SklyankaMotion.DurationFast)
        assertEquals(200, SklyankaMotion.DurationShort)
        assertEquals(300, SklyankaMotion.DurationMedium)
    }

    @Test
    fun `the curves are the cubic beziers of the motion specification`() {
        assertTrue(SklyankaMotion.EasingEnter is CubicBezierEasing)
        assertTrue(SklyankaMotion.EasingExit is CubicBezierEasing)
        assertNotEquals(
            "the enter and exit curves must differ",
            SklyankaMotion.EasingEnter.transform(0.25f),
            SklyankaMotion.EasingExit.transform(0.25f),
        )
    }
}
