package ru.stohastika.sklyanka.acceptance.steps.sk90

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.component.BottomBar
import ru.stohastika.sklyanka.ui.component.BottomBarTab
import ru.stohastika.sklyanka.ui.component.tabTint
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import kotlin.math.abs

@OptIn(ExperimentalTestApi::class)
class Sk90BottomBarScreenSteps {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Допустим("^нижняя панель собрана на стенде SKL-90$")
    fun theBottomBarIsOnTheStand() = Unit

    @Тогда("^три вкладки занимают равные доли ширины$")
    fun theTabsSplitTheBarEqually() {
        runComposeUiTest {
            setContent { SklyankaTheme { BottomBar(selected = BottomBarTab.Today, onSelect = {}) } }
            val widths = labels().map { label ->
                val node = onNodeWithContentDescription(label).fetchSemanticsNode()
                with(density) { node.size.width.toDp() }
            }
            assertEquals("the tabs must be equal: $widths", 1, widths.distinct().size)
        }
    }

    @И("^содержимое каждой вкладки центрировано в своей доле$")
    fun theTabsAreCentredInTheirShares() {
        runComposeUiTest {
            setContent { SklyankaTheme { BottomBar(selected = BottomBarTab.Today, onSelect = {}) } }
            val root = onRoot().fetchSemanticsNode()
            val middle = onNodeWithContentDescription(context.getString(R.string.tab_meds)).fetchSemanticsNode()
            val rootCentre = root.size.width / 2f
            val middleCentre = middle.boundsInRoot.left + middle.size.width / 2f
            assertTrue(
                "the middle tab must stand on the axis: $middleCentre vs $rootCentre",
                abs(middleCentre - rootCentre) <= 1f,
            )
        }
    }

    @Тогда("^нажатая вкладка «Лекарства» отличается от себя ненажатой$")
    fun thePressedTabDiffersFromTheIdleOne() {
        var base: Color? = null
        runComposeUiTest {
            setContent { SklyankaTheme { base = SklyankaTheme.colors.textMuted } }
        }
        assertNotEquals(
            "the pressed tab must take its own tint",
            tabTint(base = requireNotNull(base), pressed = false).target,
            tabTint(base = requireNotNull(base), pressed = true).target,
        )
    }

    private fun labels(): List<String> = listOf(
        context.getString(R.string.tab_today),
        context.getString(R.string.tab_meds),
        context.getString(R.string.tab_settings),
    )
}
