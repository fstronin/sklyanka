package ru.stohastika.sklyanka.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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

    private fun show(selected: BottomBarTab, onSelect: (BottomBarTab) -> Unit = {}) {
        composeTestRule.setContent {
            SklyankaTheme {
                BottomBar(selected = selected, onSelect = onSelect)
            }
        }
    }
}
