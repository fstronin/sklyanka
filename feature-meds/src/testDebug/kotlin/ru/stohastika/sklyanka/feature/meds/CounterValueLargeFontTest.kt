package ru.stohastika.sklyanka.feature.meds

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/**
 * Значение счётчика приёмов при системном шрифте 200 % (ОВ-117).
 *
 * Минимум 22 dp держит ритм ряда, но не режет «10»: число растёт по значению,
 * остаётся одной строкой, а ряд с кнопками не разъезжается.
 */
@RunWith(AndroidJUnit4::class)
class CounterValueLargeFontTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theValueStaysOneLineAndGrowsAtTwoHundredPercent() {
        composeRule.setContent {
            Scaled(SCALE_200) {
                SklyankaTheme {
                    CounterCard(
                        labels = CounterLabels(
                            title = "Приёмов в сутки",
                            caption = "Время задаётся ниже",
                            decrease = "Уменьшить",
                            increase = "Увеличить",
                        ),
                        value = "10",
                        onDecrease = {},
                        onIncrease = {},
                    )
                }
            }
        }

        val value = composeRule.onNodeWithText("10", useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        value.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        val layout = requireNotNull(results.firstOrNull())

        assertEquals("the counter value must be one line", 1, layout.lineCount)
        assertTrue("the counter value must not be cut", !layout.didOverflowHeight)
        assertTrue(
            "the value must grow past the 22 dp rhythm minimum",
            value.size.width > with(composeRule.density) { SklyankaSizes.CounterValue.roundToPx() },
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
