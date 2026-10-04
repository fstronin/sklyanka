package ru.stohastika.sklyanka.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

/**
 * Многострочное поле и нейтральный глиф диагностики (SKL-102, §4.4/§4.6).
 *
 * Проверяется то, что обещает дизайн-система: поле не опускается ниже 112 dp, растёт
 * по содержимому и не показывает ошибок у пустого необязательного значения; глиф
 * берёт габарит из токена, а не по месту (НФТ-47).
 */
@RunWith(AndroidJUnit4::class)
class MultilineFieldTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun anEmptyMultilineFieldKeepsItsTokenHeightWithoutErrors() {
        composeRule.setContent {
            SklyankaTheme {
                Column {
                    Box(Modifier.size(SklyankaSizes.MultilineFieldMinHeight).testTag("reference"))
                    SklyankaTextField(value = "", onValueChange = {}, hint = "hint", multiline = true)
                }
            }
        }

        val minPx = with(composeRule.density) { SklyankaSizes.MultilineFieldMinHeight.toPx() }
        val field = composeRule.onNodeWithTag(TAG_TEXT_FIELD).fetchSemanticsNode()
        val reference = composeRule.onNodeWithTag("reference").fetchSemanticsNode()

        composeRule.onNodeWithTag(TAG_TEXT_FIELD).assertIsDisplayed()
        assertTrue(
            "the field must not fall below 112 dp: h=${field.size.height} ref=${reference.size.height} min=$minPx",
            field.size.height >= minPx.toInt(),
        )
        composeRule.onNodeWithTag(TAG_TEXT_FIELD_ERROR).assertDoesNotExist()
    }

    @Test
    fun aFilledMultilineFieldGrowsWithItsContent() {
        val manyLines = (1..10).joinToString("\n") { "Строка $it" }
        composeRule.setContent {
            SklyankaTheme {
                SklyankaTextField(
                    value = manyLines,
                    onValueChange = {},
                    multiline = true,
                )
            }
        }

        val minPx = with(composeRule.density) { SklyankaSizes.MultilineFieldMinHeight.toPx() }
        val field = composeRule.onNodeWithTag(TAG_TEXT_FIELD).fetchSemanticsNode()
        val layout = field.firstTextLayout()

        assertTrue("the text must wrap to several lines", layout.lineCount > 1)
        assertTrue("the field must grow past 112 dp: ${field.size.height}", field.size.height > minPx.toInt())
    }

    @Test
    fun theInfoGlyphTakesItsSizeFromTheBannerToken() {
        composeRule.setContent {
            SklyankaTheme {
                Box {
                    InfoGlyph(
                        color = SklyankaTheme.colors.textMuted,
                        modifier = Modifier.testTag(TAG_INFO_GLYPH),
                    )
                }
            }
        }

        val sizePx = with(composeRule.density) { SklyankaSizes.BannerIcon.roundToPx() }
        val node = composeRule.onNodeWithTag(TAG_INFO_GLYPH).fetchSemanticsNode()

        assertEquals(sizePx, node.size.width)
        assertEquals(sizePx, node.size.height)
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

    private companion object {
        const val TAG_INFO_GLYPH = "info_glyph"
    }
}
