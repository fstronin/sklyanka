package ru.stohastika.sklyanka.ui.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.stohastika.sklyanka.ui.token.SklyankaMotion

class MotionTest {

    @Test
    fun `every phase keeps the duration and the curve of the motion specification`() {
        val expected = mapOf(
            MotionPhase.SecondLevelEnter to MotionTiming(300, SklyankaMotion.EasingEnter),
            MotionPhase.SecondLevelExit to MotionTiming(300, SklyankaMotion.EasingExit),
            MotionPhase.AdjacentEnter to MotionTiming(200, SklyankaMotion.EasingEnter),
            MotionPhase.AdjacentExit to MotionTiming(200, SklyankaMotion.EasingExit),
            MotionPhase.ReminderEnter to MotionTiming(300, SklyankaMotion.EasingEnter),
            MotionPhase.ReminderExit to MotionTiming(200, SklyankaMotion.EasingExit),
            MotionPhase.SnackbarEnter to MotionTiming(200, SklyankaMotion.EasingEnter),
            MotionPhase.SnackbarExit to MotionTiming(150, SklyankaMotion.EasingExit),
            MotionPhase.SheetEnter to MotionTiming(300, SklyankaMotion.EasingEnter),
            MotionPhase.SheetExit to MotionTiming(200, SklyankaMotion.EasingExit),
        )

        expected.forEach { (phase, timing) ->
            assertEquals("duration of $phase", timing, motionTiming(phase))
        }
    }

    @Test
    fun `the phases cover the whole motion table`() {
        assertEquals(10, MotionPhase.entries.size)
    }

    @Test
    fun `no transition lasts longer than the medium duration`() {
        MotionPhase.entries.forEach { phase ->
            assertTrue(
                "the $phase duration must not exceed DurationMedium",
                motionTiming(phase).durationMillis <= SklyankaMotion.DurationMedium,
            )
        }
    }

    @Test
    fun `the crossing phases take the enter and the exit curves of the token set`() {
        assertEquals(SklyankaMotion.EasingEnter, motionTiming(MotionPhase.AdjacentEnter).easing)
        assertEquals(SklyankaMotion.EasingExit, motionTiming(MotionPhase.AdjacentExit).easing)
    }
}
