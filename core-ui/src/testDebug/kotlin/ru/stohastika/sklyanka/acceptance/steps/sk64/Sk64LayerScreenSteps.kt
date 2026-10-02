package ru.stohastika.sklyanka.acceptance.steps.sk64

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import ru.stohastika.sklyanka.ui.motion.AnimatedLayer
import ru.stohastika.sklyanka.ui.motion.LayerMotion
import ru.stohastika.sklyanka.ui.motion.MotionPhase
import ru.stohastika.sklyanka.ui.motion.motionTiming
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

@OptIn(ExperimentalTestApi::class)
class Sk64LayerScreenSteps {

    private var closing = false

    @Допустим("^слой второго уровня собран на стенде SKL-64$")
    fun theSecondLevelLayerIsOnTheStand() = Unit

    @Допустим("^соседние экраны собраны на стенде SKL-64$")
    fun theAdjacentScreensAreOnTheStand() = Unit

    @Когда("^открывается следующий экран$")
    fun theNextScreenOpens() = Unit

    @Когда("^следующий экран закрывается$")
    fun theNextScreenCloses() {
        closing = true
    }

    @Когда("^вкладка сменяется на соседнюю$")
    fun theTabChangesToTheNeighbour() = Unit

    @Тогда("^в середине перехода видны оба экрана$")
    fun bothScreensAreVisibleInTheMiddle() = runComposeUiTest {
        val layer = mutableStateOf(0)
        val motion = mutableStateOf(LayerMotion.Crossfade)
        setContent { layerStand(layer, motion) }
        mainClock.autoAdvance = false
        mainClock.advanceTimeByFrame()
        if (closing) {
            motion.value = LayerMotion.Forward
            layer.value = 1
            startTransition()
            finishTransition()
            mainClock.autoAdvance = false
        }
        motion.value = if (closing) LayerMotion.Back else LayerMotion.Forward
        layer.value = if (closing) 0 else 1
        startTransition()

        assertCount(tag = 0, expected = 1)
        assertCount(tag = 1, expected = 1)
    }

    @Тогда("^предыдущий экран не смещается$")
    fun thePreviousScreenDoesNotMove() = runComposeUiTest {
        val layer = mutableStateOf(0)
        val motion = mutableStateOf(LayerMotion.Crossfade)
        setContent { layerStand(layer, motion) }
        mainClock.autoAdvance = false
        mainClock.advanceTimeByFrame()
        val before = onNodeWithTag(layerTag(0)).fetchSemanticsNode().boundsInRoot

        motion.value = LayerMotion.Forward
        layer.value = 1
        startTransition()
        val during = onNodeWithTag(layerTag(0)).fetchSemanticsNode().boundsInRoot

        assertEquals(before, during)
    }

    @Тогда("^через (\\d+) ms остаётся только новый экран$")
    fun onlyTheNewScreenStays(ms: Int) = runComposeUiTest {
        val layer = mutableStateOf(0)
        val motion = mutableStateOf(LayerMotion.Crossfade)
        setContent { layerStand(layer, motion) }
        mainClock.autoAdvance = false
        mainClock.advanceTimeByFrame()

        motion.value = LayerMotion.Forward
        layer.value = 1
        startTransition()
        finishTransition()

        assertCount(tag = 1, expected = 1)
        assertCount(tag = 0, expected = 0)
        assertEquals(motionTiming(MotionPhase.SecondLevelEnter).durationMillis, ms)
    }

    @Тогда("^через (\\d+) ms остаётся только предыдущий экран$")
    fun onlyThePreviousScreenStays(ms: Int) = runComposeUiTest {
        val layer = mutableStateOf(0)
        val motion = mutableStateOf(LayerMotion.Crossfade)
        setContent { layerStand(layer, motion) }
        mainClock.autoAdvance = false
        mainClock.advanceTimeByFrame()

        motion.value = LayerMotion.Forward
        layer.value = 1
        startTransition()
        finishTransition()
        mainClock.autoAdvance = false

        motion.value = LayerMotion.Back
        layer.value = 0
        startTransition()
        finishTransition()

        assertCount(tag = 0, expected = 1)
        assertCount(tag = 1, expected = 0)
        assertEquals(motionTiming(MotionPhase.SecondLevelExit).durationMillis, ms)
    }

    @Тогда("^в середине перехода видны обе вкладки$")
    fun bothTabsAreVisibleInTheMiddle() = runComposeUiTest {
        val layer = mutableStateOf(0)
        val motion = mutableStateOf(LayerMotion.Crossfade)
        setContent { layerStand(layer, motion) }
        mainClock.autoAdvance = false
        mainClock.advanceTimeByFrame()

        layer.value = 1
        startTransition()

        assertCount(tag = 0, expected = 1)
        assertCount(tag = 1, expected = 1)
    }

    @Тогда("^через (\\d+) ms остаётся только новая вкладка$")
    fun onlyTheNewTabStays(ms: Int) = runComposeUiTest {
        val layer = mutableStateOf(0)
        val motion = mutableStateOf(LayerMotion.Crossfade)
        setContent { layerStand(layer, motion) }
        mainClock.autoAdvance = false
        mainClock.advanceTimeByFrame()

        layer.value = 1
        startTransition()
        finishTransition()

        assertCount(tag = 1, expected = 1)
        assertCount(tag = 0, expected = 0)
        assertEquals(motionTiming(MotionPhase.AdjacentEnter).durationMillis, ms)
    }

    private fun ComposeUiTest.startTransition() {
        waitForIdle()
        mainClock.advanceTimeByFrame()
        waitForIdle()
    }

    private fun ComposeUiTest.finishTransition() {
        mainClock.autoAdvance = true
        waitForIdle()
    }

    private fun ComposeUiTest.assertCount(tag: Int, expected: Int) {
        onAllNodesWithTag(layerTag(tag)).assertCountEquals(expected)
    }
}

@Composable
private fun layerStand(layer: MutableState<Int>, motion: MutableState<LayerMotion>) {
    SklyankaTheme {
        AnimatedLayer(layer = layer.value, motion = motion.value) { shown ->
            Box(Modifier.fillMaxSize().testTag(layerTag(shown)))
        }
    }
}

private fun layerTag(value: Int): String = "sk64-layer-$value"
