package ru.stohastika.sklyanka.acceptance.steps.sk64

import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.ui.motion.MotionPhase
import ru.stohastika.sklyanka.ui.motion.motionTiming
import ru.stohastika.sklyanka.ui.token.SklyankaMotion

class Sk64MotionTimingScreenSteps {

    private var phase: MotionPhase? = null

    @Допустим("^фаза движения — «(.+)»$")
    fun thePhaseIs(name: String) {
        phase = phases.getValue(name)
    }

    @Тогда("^её длительность — (\\d+) ms$")
    fun itsDurationIs(ms: Int) {
        assertEquals(ms, motionTiming(requireNotNull(phase)).durationMillis)
    }

    @И("^её кривая — «(въезд|уход)»$")
    fun itsCurveIs(name: String) {
        val expected = if (name == "въезд") SklyankaMotion.EasingEnter else SklyankaMotion.EasingExit
        assertEquals(expected, motionTiming(requireNotNull(phase)).easing)
    }

    @Допустим("^собраны все фазы движения на стенде SKL-64$")
    fun allPhasesAreOnTheStand() = Unit

    @Тогда("^ни одна длительность не превышает (\\d+) ms$")
    fun noDurationExceeds(ms: Int) {
        MotionPhase.entries.forEach { phase ->
            assertTrue(
                "the $phase duration must not exceed $ms ms",
                motionTiming(phase).durationMillis <= ms,
            )
        }
    }

    private companion object {
        val phases: Map<String, MotionPhase> = mapOf(
            "въезд второго уровня" to MotionPhase.SecondLevelEnter,
            "уход второго уровня" to MotionPhase.SecondLevelExit,
            "приход соседнего слоя" to MotionPhase.AdjacentEnter,
            "уход соседнего слоя" to MotionPhase.AdjacentExit,
            "появление напоминания" to MotionPhase.ReminderEnter,
            "уход напоминания" to MotionPhase.ReminderExit,
            "появление снэкбара" to MotionPhase.SnackbarEnter,
            "уход снэкбара" to MotionPhase.SnackbarExit,
            "выезд шторки" to MotionPhase.SheetEnter,
            "уход шторки" to MotionPhase.SheetExit,
        )
    }
}
