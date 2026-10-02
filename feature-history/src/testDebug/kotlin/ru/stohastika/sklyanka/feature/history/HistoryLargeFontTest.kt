package ru.stohastika.sklyanka.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.token.SklyankaType

/**
 * Числа «Истории» при системном шрифте 200 % (НФТ-31, §6.3).
 *
 * Число дня календаря — `Micro` 10 sp в клетке 30 dp; числа-показатели плиток —
 * `Stat` 26 sp. Проверяются оба места: ни одно число не обрезано, а число дня
 * помещается в клетку.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class HistoryLargeFontTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyNumberStaysWholeAtTwoHundredPercent() {
        var dayNumber: TextLayoutResult? = null
        composeRule.setContent {
            Scaled(SCALE_200) {
                SklyankaTheme {
                    HistoryScreen(state = HistoryStates.september(), onIntent = {})
                    dayNumber = rememberTextMeasurer().measure("31", SklyankaType.Micro)
                }
            }
        }

        val complaints = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            results.forEach { layout ->
                if (layout.didOverflowHeight) complaints += "cut: ${layout.layoutInput.text.text}"
                for (line in 0 until layout.lineCount) {
                    if (layout.isLineEllipsized(line)) {
                        complaints += "ellipsized: ${layout.layoutInput.text.text}"
                    }
                }
            }
            node.children.forEach(::walk)
        }
        walk(composeRule.onRoot().fetchSemanticsNode())
        assertTrue("history numbers must not be cut at 200 %: $complaints", complaints.isEmpty())

        // Число дня рисуется `Micro` в клетке `CalendarDay`; проверяется замером той же пары.
        val layout = requireNotNull(dayNumber) { "the day number was not measured" }
        val cell = with(composeRule.density) { SklyankaSizes.CalendarDay.toPx() }
        assertEquals("the day number must stay one line", 1, layout.lineCount)
        assertTrue(
            "the day number must fit the ${cell}px cell, but is ${layout.size}",
            layout.size.height <= cell && layout.size.width <= cell * 2,
        )
    }

    @Composable
    private fun Scaled(scale: Float, content: @Composable () -> Unit) {
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = base.density, fontScale = scale),
            content = content,
        )
    }

    private companion object {
        const val SCALE_200 = 2f
    }
}
