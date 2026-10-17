package ru.stohastika.sklyanka.ui.component

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.util.concurrent.atomic.AtomicReference

/**
 * Общая нижняя панель на три вкладки (§4.13, ОВ-91, SKL-71).
 *
 * Проверяется состав панели и то, что каждая вкладка названа для чтения с экрана;
 * вид активной вкладки стерегут снимки корневых экранов.
 */
@RunWith(AndroidJUnit4::class)
class BottomBarTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun threeTabsAreNamedForTheScreenReader() {
        show(selected = BottomBarTab.Today)

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.tab_today)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.tab_meds)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.tab_settings)).assertIsDisplayed()
    }

    @Test
    fun anUnselectedTabReportsItsChoice() {
        val chosen = AtomicReference<BottomBarTab?>(null)
        show(selected = BottomBarTab.Today, onSelect = { chosen.set(it) })

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.tab_meds)).performClick()
        assertEquals(BottomBarTab.Meds, chosen.get())
    }

    @Test
    fun theSelectedTabDoesNotReopenItself() {
        val chosen = AtomicReference<BottomBarTab?>(null)
        show(selected = BottomBarTab.Settings, onSelect = { chosen.set(it) })

        composeTestRule.onNodeWithContentDescription(context.getString(R.string.tab_settings)).performClick()
        assertEquals(null, chosen.get())
    }

    @Test
    fun theThreeTabsSplitTheBarEqually() {
        show(selected = BottomBarTab.Today)

        val widths = labels().map { label ->
            val node = composeTestRule.onNodeWithContentDescription(label).fetchSemanticsNode()
            with(composeTestRule.density) { node.size.width.toDp() }
        }
        assertEquals("the tabs must be equal: $widths", widths.distinct().size, 1)
    }

    @Test
    fun theMiddleTabStandsOnTheAxis() {
        show(selected = BottomBarTab.Today)

        val root = composeTestRule.onRoot().fetchSemanticsNode()
        val middle = composeTestRule.onNodeWithContentDescription(context.getString(R.string.tab_meds))
            .fetchSemanticsNode()
        val rootCentre = root.size.width / 2f
        val middleCentre = middle.boundsInRoot.left + middle.size.width / 2f
        assertTrue(
            "the middle tab must stand on the screen axis: $middleCentre vs $rootCentre",
            kotlin.math.abs(middleCentre - rootCentre) <= 1f,
        )
    }

    /**
     * Подписи вкладок живут при системном масштабе 2.0, а панель растёт (§6.3, Р-87 п. 4).
     *
     * До правки подпись выше масштаба 1,3 скрывалась, и человек с крупным шрифтом терял
     * подписи-ориентиры (RT-3): значок остаётся названным для чтения с экрана, но глаз
     * ищет слово, а не рисунок.
     */
    @Test
    fun theLabelsSurviveTheDoubleFontScaleAndTheBarGrows() {
        var fontScale by mutableStateOf(1f)
        composeTestRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                SklyankaTheme {
                    BottomBar(selected = BottomBarTab.Today, onSelect = {})
                }
            }
        }
        val normalHeight = composeTestRule.onNodeWithTag(TAG_BOTTOM_BAR).fetchSemanticsNode().size.height

        fontScale = 2f
        composeTestRule.waitForIdle()
        labels().forEach { label ->
            composeTestRule.onNodeWithText(label).assertIsDisplayed()
        }
        val largeHeight = composeTestRule.onNodeWithTag(TAG_BOTTOM_BAR).fetchSemanticsNode().size.height
        assertTrue("the bar must grow with the font: $normalHeight -> $largeHeight", largeHeight > normalHeight)
    }

    private fun labels(): List<String> = listOf(
        context.getString(R.string.tab_today),
        context.getString(R.string.tab_meds),
        context.getString(R.string.tab_settings),
    )

    private fun show(selected: BottomBarTab, onSelect: (BottomBarTab) -> Unit = {}) {
        composeTestRule.setContent {
            SklyankaTheme {
                BottomBar(selected = selected, onSelect = onSelect)
            }
        }
    }
}
