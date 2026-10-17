package ru.stohastika.sklyanka.acceptance.steps.sk154

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment
import ru.stohastika.sklyanka.ui.R
import ru.stohastika.sklyanka.ui.component.BottomBar
import ru.stohastika.sklyanka.ui.component.BottomBarTab
import ru.stohastika.sklyanka.ui.component.TAG_BOTTOM_BAR
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme

/**
 * Экранные шаги приёмки SKL-154: нижняя панель при системном шрифте 200 % (RT-3, Р-87 п. 4, §6.3).
 *
 * До правки подпись вкладки скрывалась выше масштаба 1,3 и человек с крупным шрифтом терял
 * подписи-ориентиры: оставались только пиктограммы. Масштаб задаётся среде прогона тем же
 * способом, что на стендах SKL-90 (`Sk90TimePickerScreenSteps`) и SKL-93, —
 * `RuntimeEnvironment.setFontScale`, обратимо.
 */
@OptIn(ExperimentalTestApi::class)
class Sk154BottomBarSteps {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    /** Подписи, найденные на панели при крупном шрифте, и высоты панели при двух масштабах. */
    private var visibleLabels: List<String> = emptyList()
    private var largeHeight = 0
    private var normalHeight = 0

    @Когда("^нижняя панель собрана на стенде SKL-154 при системном шрифте 200 %$")
    fun theBottomBarIsOnTheStandAtDoubleFontScale() {
        visibleLabels = labels().filter { label ->
            composeAt(LARGE_SCALE) { onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty() }
        }
        largeHeight = barHeightAt(LARGE_SCALE)
        normalHeight = barHeightAt(NORMAL_SCALE)
    }

    @Тогда("^подписи всех трёх вкладок видимы$")
    fun allThreeTabLabelsAreVisible() {
        assertEquals(
            "крупный шрифт не обязан отнимать подписи-ориентиры (Р-87 п. 4)",
            labels(),
            visibleLabels,
        )
    }

    @Тогда("^панель выше, чем при системном шрифте 100 %$")
    fun theBarIsTallerThanAtTheNormalFont() {
        assertTrue(
            "панель обязана расти вместе с подписью: $normalHeight -> $largeHeight",
            largeHeight > normalHeight,
        )
    }

    private fun barHeightAt(scale: Float): Int = composeAt(scale) {
        onNodeWithTag(TAG_BOTTOM_BAR).fetchSemanticsNode().size.height
    }

    /** Панель собирается в отдельном замере: масштаб задаётся среде и возвращается обратно. */
    private fun <T> composeAt(scale: Float, question: ComposeUiTest.() -> T): T {
        val previous = RuntimeEnvironment.getFontScale()
        RuntimeEnvironment.setFontScale(scale)
        try {
            var answer: T? = null
            runComposeUiTest {
                setContent { SklyankaTheme { BottomBar(selected = BottomBarTab.Today, onSelect = {}) } }
                answer = question(this)
            }
            @Suppress("UNCHECKED_CAST")
            return answer as T
        } finally {
            RuntimeEnvironment.setFontScale(previous)
        }
    }

    private fun labels(): List<String> = listOf(
        context.getString(R.string.tab_today),
        context.getString(R.string.tab_meds),
        context.getString(R.string.tab_settings),
    )

    private companion object {
        const val NORMAL_SCALE = 1f
        const val LARGE_SCALE = 2f
    }
}
