package ru.stohastika.sklyanka.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalTime

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DateTimePickerDialogFontScaleTest {

    @Test
    fun timeDigitsKeepTheirSizeAtOneHundredThirtyPercent() {
        assertEquals(digitHeight(1f), digitHeight(CAP_SCALE))
    }

    @Test
    fun timeDigitsKeepTheirSizeAtTwoHundredPercent() {
        assertEquals(digitHeight(1f), digitHeight(SCALE_200))
    }

    @Test
    fun theDialogTitleGrowsWithTheSystemFont() {
        assertTrue(titleHeight(SCALE_200) > titleHeight(1f))
    }

    private fun digitHeight(scale: Float): Int = composeAt(scale) {
        onAllNodes(hasAnyAncestor(hasTestTag(TAG_TIME_PICKER)), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { node: SemanticsNode ->
                node.config.getOrNull(SemanticsProperties.Text)?.any { text -> text.text.any(Char::isDigit) } == true
            }
            .maxOfOrNull { node: SemanticsNode -> node.size.height } ?: 0
    }

    private fun titleHeight(scale: Float): Int = composeAt(scale) {
        onNodeWithText(TITLE, useUnmergedTree = true).fetchSemanticsNode().size.height
    }

    private fun <T> composeAt(scale: Float, question: ComposeUiTest.() -> T): T {
        val previous = RuntimeEnvironment.getFontScale()
        RuntimeEnvironment.setFontScale(scale)
        try {
            var answer: T? = null
            runComposeUiTest {
                setContent { Content() }
                answer = question(this)
            }
            @Suppress("UNCHECKED_CAST")
            return answer as T
        } finally {
            RuntimeEnvironment.setFontScale(previous)
        }
    }

    @Composable
    private fun Content() {
        SklyankaTheme {
            DateTimePickerDialog(
                title = TITLE,
                initial = LocalTime.of(8, 0),
                is24Hour = true,
                onDismiss = {},
                onConfirm = {},
            )
        }
    }

    private companion object {
        const val TITLE = "Time"
        const val CAP_SCALE = 1.3f
        const val SCALE_200 = 2f
    }
}
