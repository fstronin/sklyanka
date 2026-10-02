package ru.stohastika.sklyanka.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Поля ввода при системном шрифте 200 % (§6.3, НФТ-31).
 *
 * Обычное поле 50 dp строку `Body` 44,8 dp вмещает; компактное поле числа (42/44 dp)
 * до этой задачи не вмещало и подрезало цифру снизу — теперь высота поля не меньше
 * его строки.
 */
@RunWith(AndroidJUnit4::class)
class TextFieldLargeFontTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theRegularFieldHoldsItsTextAtTwoHundredPercent() {
        show(SCALE_200) {
            SklyankaTextField(value = "120", onValueChange = {}, unitLabel = "шт")
        }

        val field = composeRule.onNodeWithTag(TAG_TEXT_FIELD).fetchSemanticsNode()
        val layout = field.firstTextLayout()
        assertEquals("the value must stay one line", 1, layout.lineCount)
        assertTrue(
            "the field must be at least as high as its line: ${layout.getLineBottom(0)} in ${field.size.height}",
            field.size.height >= layout.getLineBottom(0),
        )
    }

    @Test
    fun theCompactNumberFieldHoldsItsLineAtTwoHundredPercent() {
        show(SCALE_200) {
            SklyankaNumberField(value = "10", onValueChange = {})
        }

        val field = composeRule.onNodeWithTag(TAG_NUMBER_FIELD).fetchSemanticsNode()
        val layout = field.firstTextLayout()
        assertEquals("the number must stay one line", 1, layout.lineCount)
        assertTrue(
            "the field must be at least as high as its line: ${layout.getLineBottom(0)} in ${field.size.height}",
            field.size.height >= layout.getLineBottom(0),
        )
    }

    private fun SemanticsNode.firstTextLayout(): TextLayoutResult {
        val found = mutableListOf<TextLayoutResult>()
        fun walk(node: SemanticsNode) {
            val results = mutableListOf<TextLayoutResult>()
            node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            found += results
            node.children.forEach(::walk)
        }
        walk(this)
        return requireNotNull(found.firstOrNull()) { "the field has no text layout" }
    }

    private fun show(scale: Float, content: @Composable () -> Unit) {
        composeRule.setContent {
            Scaled(scale) { SklyankaTheme { content() } }
        }
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
