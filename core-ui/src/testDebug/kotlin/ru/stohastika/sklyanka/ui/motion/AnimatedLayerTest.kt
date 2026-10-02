package ru.stohastika.sklyanka.ui.motion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h844dp-mdpi")
class AnimatedLayerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val layer: MutableState<Int> = mutableStateOf(0)
    private val motion: MutableState<LayerMotion> = mutableStateOf(LayerMotion.Crossfade)

    private fun show() {
        composeRule.setContent {
            SklyankaTheme {
                AnimatedLayer(layer = layer.value, motion = motion.value) { shown ->
                    Box(
                        Modifier
                            .fillMaxSize()
                            .testTag(tag(shown)),
                    )
                }
            }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `the forward transition keeps the previous layer until the new one arrives`() {
        show()

        composeRule.runOnIdle {
            motion.value = LayerMotion.Forward
            layer.value = 1
        }
        startTransition()

        bothLayersArePresent()
        val previousBounds = composeRule.onNodeWithTag(tag(0)).fetchSemanticsNode().boundsInRoot

        composeRule.mainClock.advanceTimeBy(150)
        composeRule.waitForIdle()
        bothLayersArePresent()
        assertEquals(previousBounds, composeRule.onNodeWithTag(tag(0)).fetchSemanticsNode().boundsInRoot)

        settle()
        composeRule.onNodeWithTag(tag(1)).assertIsDisplayed()
        composeRule.onNodeWithTag(tag(0)).assertDoesNotExist()
    }

    @Test
    fun `the back transition reveals the previous layer and removes the closing one`() {
        show()
        composeRule.runOnIdle {
            motion.value = LayerMotion.Forward
            layer.value = 1
        }
        settle()
        composeRule.onNodeWithTag(tag(0)).assertDoesNotExist()

        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle {
            motion.value = LayerMotion.Back
            layer.value = 0
        }
        startTransition()

        bothLayersArePresent()
        settle()

        composeRule.onNodeWithTag(tag(0)).assertIsDisplayed()
        composeRule.onNodeWithTag(tag(1)).assertDoesNotExist()
    }

    @Test
    fun `the crossfade keeps both layers for the short duration and ends on the new one`() {
        show()

        composeRule.runOnIdle {
            motion.value = LayerMotion.Crossfade
            layer.value = 1
        }
        startTransition()

        bothLayersArePresent()
        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()
        bothLayersArePresent()

        settle()
        composeRule.onNodeWithTag(tag(1)).assertIsDisplayed()
        composeRule.onNodeWithTag(tag(0)).assertDoesNotExist()
    }

    private fun startTransition() {
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
    }

    private fun settle() {
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
    }

    private fun bothLayersArePresent() {
        composeRule.onAllNodesWithTag(tag(0)).assertCountEquals(1)
        composeRule.onAllNodesWithTag(tag(1)).assertCountEquals(1)
    }

    private fun tag(value: Int): String = "layer-$value"
}
