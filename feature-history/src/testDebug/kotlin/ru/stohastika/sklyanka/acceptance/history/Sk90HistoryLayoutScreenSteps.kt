package ru.stohastika.sklyanka.acceptance.history

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.И
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.feature.history.HistoryScreen
import ru.stohastika.sklyanka.feature.history.HistoryStates
import ru.stohastika.sklyanka.feature.history.HistoryUiState
import ru.stohastika.sklyanka.feature.history.R
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_ACTION
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_CONTENT
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SAVE
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SHARE
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_WARNING
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes

@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class Sk90HistoryLayoutScreenSteps {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private var scale: Float = 1f

    @Допустим("^экран «История» собран на стенде SKL-90$")
    fun theHistoryScreenIsOnTheStand() = Unit

    @Тогда("^верхний отступ шапки «Истории» взят один раз$")
    fun theTopPaddingIsAppliedOnce() = probe(HistoryStates.september()) {
        val action = onNodeWithTag(TAG_REPORT_ACTION).fetchSemanticsNode()
        val top = with(density) { action.boundsInRoot.top.toDp() }
        assertTrue(
            "the header top padding must be applied once, but the action row starts at $top",
            top in SklyankaSizes.HeaderTopPaddingProfile..(SklyankaSizes.HeaderTopPadding + TOLERANCE),
        )
    }

    @И("^между рядом действия и заголовком «История» зазор не больше 12 dp$")
    fun theTitleFollowsTheActionRow() = probe(HistoryStates.september()) {
        val action = onNodeWithTag(TAG_REPORT_ACTION).fetchSemanticsNode()
        val title = onNodeWithText(context.getString(R.string.history_title)).fetchSemanticsNode()
        val gap = with(density) { (title.boundsInRoot.top - action.boundsInRoot.bottom).toDp() }
        assertTrue(
            "the title must follow the action row closely, but the gap is $gap",
            gap in MIN_GAP..MAX_GAP,
        )
    }

    @И("^системный шрифт «Истории» увеличен до (\\d+) %$")
    fun theHistoryFontScale(percent: Int) {
        scale = percent / PERCENT
    }

    @Тогда("^заголовок «История» не разорван переносом по буквам$")
    fun theTitleIsNotBroken() = probe(HistoryStates.september(), scale) {
        val title = onNodeWithText(context.getString(R.string.history_title)).fetchSemanticsNode()
        assertTrue("the title must not be broken inside a word", !brokenInsideWord(title))
    }

    @Допустим("^шторка «Отчёт врачу» открыта на стенде SKL-90$")
    fun theReportSheetIsOpen() = Unit

    @Тогда("^кнопки «Передать» и «Сохранить» одной высоты и одной формы$")
    fun theButtonsShareOneForm() = probe(HistoryStates.withReport()) {
        val share = onNodeWithTag(TAG_REPORT_SHARE).fetchSemanticsNode()
        val save = onNodeWithTag(TAG_REPORT_SAVE).fetchSemanticsNode()
        val shareHeight = with(density) { share.size.height.toDp() }
        val saveHeight = with(density) { save.size.height.toDp() }
        assertTrue("the buttons must be one form: $shareHeight vs $saveHeight", shareHeight == saveHeight)
        assertTrue(
            "the buttons must keep the screen height: $shareHeight",
            shareHeight >= SklyankaSizes.PrimaryButtonHeight,
        )
    }

    @И("^каждая кнопка занимает половину ширины ряда$")
    fun theButtonsSplitTheRow() = probe(HistoryStates.withReport()) {
        val share = onNodeWithTag(TAG_REPORT_SHARE).fetchSemanticsNode()
        val save = onNodeWithTag(TAG_REPORT_SAVE).fetchSemanticsNode()
        val shareWidth = with(density) { share.size.width.toDp() }
        val saveWidth = with(density) { save.size.width.toDp() }
        assertTrue("the buttons must split the row equally: $shareWidth vs $saveWidth", shareWidth == saveWidth)
    }

    @Тогда("^предупреждение о медицинских данных стоит выше карточки состава$")
    fun theWarningStandsAboveTheCard() = probe(HistoryStates.withReport()) {
        val warning = onNodeWithTag(TAG_REPORT_WARNING).fetchSemanticsNode()
        val card = onNodeWithTag(TAG_REPORT_CONTENT).fetchSemanticsNode()
        assertTrue("the warning must stand above the card", warning.boundsInRoot.top < card.boundsInRoot.top)
    }

    @И("^состав отчёта назван карточкой: профиль, период, число приёмов и формат «PDF»$")
    fun theCompositionIsACard() = probe(HistoryStates.withReport()) {
        val card = onNodeWithTag(TAG_REPORT_CONTENT).fetchSemanticsNode()
        val lines = textsOf(card)
        assertTrue("the card must name the profile: $lines", lines.any { it.startsWith("Профиль:") })
        assertTrue("the card must name the period: $lines", lines.any { it.startsWith("Период:") })
        assertTrue("the card must name the count: $lines", lines.any { it.startsWith("Принято:") })
        assertTrue("the card must name the format: $lines", lines.contains("Формат: PDF"))
    }

    @И("^системный шрифт отчёта увеличен до (\\d+) %$")
    fun theReportFontScale(percent: Int) {
        scale = percent / PERCENT
    }

    @Тогда("^подписи кнопок «Передать» и «Сохранить» не перенесены на две строки$")
    fun theButtonLabelsStayOnOneLine() = probe(HistoryStates.withReport(), scale) {
        listOf(R.string.history_report_share, R.string.history_report_save).forEach { id ->
            val node = onNodeWithText(context.getString(id), useUnmergedTree = true).fetchSemanticsNode()
            assertTrue("the label «${context.getString(id)}» must stay on one line", singleLine(node))
        }
    }

    @И("^высота кнопок не меньше 52 dp$")
    fun theButtonsAreTallEnough() = probe(HistoryStates.withReport(), scale) {
        listOf(TAG_REPORT_SHARE, TAG_REPORT_SAVE).forEach { tag ->
            val node = onNodeWithTag(tag).fetchSemanticsNode()
            val height = with(density) { node.size.height.toDp() }
            assertTrue(
                "the button $tag is $height, below the screen height",
                height >= SklyankaSizes.PrimaryButtonHeight,
            )
        }
    }

    private fun probe(state: HistoryUiState, scale: Float = 1f, question: ComposeUiTest.() -> Unit) {
        runComposeUiTest {
            setContent {
                Scaled(scale) {
                    SklyankaTheme { HistoryScreen(state = state, onIntent = {}) }
                }
            }
            question(this)
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

    private fun brokenInsideWord(node: SemanticsNode): Boolean {
        val lines = layoutLines(node) ?: return false
        return lines.indices.drop(1).any { index ->
            val tail = lines[index - 1].lastOrNull()
            val head = lines[index].firstOrNull()
            tail != null && head != null && tail.isLetterOrDigit() && head.isLetterOrDigit()
        }
    }

    private fun singleLine(node: SemanticsNode): Boolean {
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.firstOrNull()?.lineCount == 1
    }

    private fun layoutLines(node: SemanticsNode): List<String>? {
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        val layout = results.firstOrNull() ?: return null
        val text = layout.layoutInput.text.text
        return (0 until layout.lineCount).map { line ->
            text.substring(layout.getLineStart(line), layout.getLineEnd(line))
        }
    }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap(::textsOf)

    private companion object {
        const val PERCENT = 100f
        val TOLERANCE = 1.dp
        val MIN_GAP = 3.5.dp
        val MAX_GAP = 12.5.dp
    }
}
