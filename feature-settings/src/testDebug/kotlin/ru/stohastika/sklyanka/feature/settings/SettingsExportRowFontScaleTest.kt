package ru.stohastika.sklyanka.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.ui.component.TAG_PRO_MARK
import ru.stohastika.sklyanka.ui.locale.withInterfaceLocale
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.util.Locale
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Метка «Про» на строке «Экспорт истории» при системном шрифте 200 % (SKL-119, §5.9, §6.3, V-66).
 *
 * Норма требует, чтобы метка росла вместе со словом, а строка настроек разрешала перенос подписи.
 * Дефект был не в метке, а в сборке строки: она отдавала метке остаток ширины после подписи,
 * и при 200 % от метки оставалась плашка без слова — молча, потому что снимок ловит изменение,
 * а не смысл. Сторож проверяет именно **слово**: оно должно быть нарисовано внутри плашки.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h3000dp-mdpi")
class SettingsExportRowFontScaleTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theMarkStandsRightBehindTheLabelAtOneHundredPercent() {
        val gap = measureGap(SCALE_100)
        assertTrue("the mark must stand right behind the label (§5.16): $gap px", gap in MIN_GAP..MAX_GAP)
    }

    @Test
    fun theMarkKeepsItsWordAtTwoHundredPercent() {
        val word = context.withInterfaceLocale(RUSSIAN).getString(UiR.string.pro_mark)
        composeRule.setContent {
            Scaled(SCALE_200) {
                SklyankaTheme {
                    SettingsScreen(state = SettingsUiState(version = VERSION), onIntent = {})
                }
            }
        }

        val mark = composeRule.onNodeWithTag(TAG_PRO_MARK, useUnmergedTree = true).fetchSemanticsNode()
        val text = composeRule.onNodeWithText(word, useUnmergedTree = true).fetchSemanticsNode()
        val label = composeRule.onNodeWithText(
            context.withInterfaceLocale(RUSSIAN).getString(R.string.settings_export_history),
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        val gap = mark.positionInRoot.x - (label.positionInRoot.x + label.size.width)
        val layouts = mutableListOf<TextLayoutResult>()
        text.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(layouts)
        val complaints = layouts.filter { layout ->
            layout.didOverflowHeight || (0 until layout.lineCount).any { layout.isLineEllipsized(it) }
        }

        // Замер кадра: плашка и слово внутри неё (величины печатаются для разбора находок V-66/V-69).
        println("pro mark at 200%: plate=${mark.size.width}px word=${text.size.width}px gap=$gap px")
        assertTrue("the word of the mark must be drawn, not clipped to a plate", text.size.width > 0)
        assertTrue("the plate must be wider than its word", mark.size.width > text.size.width)
        assertEquals(
            "the word must lay out without overflow or ellipsis: $complaints",
            emptyList<TextLayoutResult>(),
            complaints,
        )
    }

    /** Зазор между концом подписи и началом метки: у нормы он ~10 px, а не «через полстроки». */
    private fun gapAt(scale: Float): Float {
        var gap = 0f
        composeRule.setContent {
            Scaled(scale) {
                SklyankaTheme { SettingsScreen(state = SettingsUiState(version = VERSION), onIntent = {}) }
            }
        }
        val title = composeRule.onNodeWithText(
            context.withInterfaceLocale(RUSSIAN).getString(R.string.settings_export_history),
            useUnmergedTree = true,
        ).fetchSemanticsNode()
        val mark = composeRule.onNodeWithTag(TAG_PRO_MARK, useUnmergedTree = true).fetchSemanticsNode()
        gap = mark.positionInRoot.x - (title.positionInRoot.x + title.size.width)
        println(
            "pro mark gap at ${scale}x: $gap px " +
                "(label ends ${title.positionInRoot.x + title.size.width}, mark starts ${mark.positionInRoot.x})",
        )
        return gap
    }

    private fun measureGap(scale: Float): Float = gapAt(scale)

    @Composable
    private fun Scaled(scale: Float, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = density.density, fontScale = scale),
            content = content,
        )
    }

    private val context: android.content.Context = androidx.test.core.app.ApplicationProvider.getApplicationContext()

    private companion object {
        const val SCALE_100 = 1.0f
        const val SCALE_200 = 2.0f

        /** Макеты дают 10 px; допуск — собственный зазор слота и погрешность меры. */
        const val MIN_GAP = 1f
        const val MAX_GAP = 24f
        const val VERSION = "1.0"

        val RUSSIAN: Locale = Locale.forLanguageTag("ru")
    }
}
