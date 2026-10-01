package ru.stohastika.sklyanka.acceptance.steps.sk90

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
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment
import ru.stohastika.sklyanka.ui.component.DateTimePickerDialog
import ru.stohastika.sklyanka.ui.component.TAG_TIME_PICKER
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalTime

@OptIn(ExperimentalTestApi::class)
class Sk90TimePickerScreenSteps {

    private var scale: Float = 1f

    @Допустим("^диалог «Время приёма» открыт на стенде SKL-90$")
    fun theTimeDialogIsOnTheStand() = Unit

    @И("^системный шрифт диалога увеличен до (\\d+) %$")
    fun theDialogFontScale(percent: Int) {
        scale = percent / PERCENT
    }

    @Тогда("^цифры времени того же размера, что при системном шрифте 100 %$")
    fun theTimeDigitsKeepTheirSize() {
        assertEquals(digitHeight(1f), digitHeight(scale))
    }

    @Тогда("^заголовок «Время приёма» крупнее, чем при системном шрифте 100 %$")
    fun theDialogTitleGrows() {
        assertTrue(
            "the title must grow with the system font",
            titleHeight(scale) > titleHeight(1f),
        )
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
        const val PERCENT = 100f
        const val TITLE = "Время приёма"
    }
}
