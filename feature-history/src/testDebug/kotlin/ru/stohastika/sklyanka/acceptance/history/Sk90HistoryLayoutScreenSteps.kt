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
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.feature.history.HistoryScreen
import ru.stohastika.sklyanka.feature.history.HistoryStates
import ru.stohastika.sklyanka.feature.history.HistoryUiState
import ru.stohastika.sklyanka.feature.history.R
import ru.stohastika.sklyanka.feature.history.TAG_BREAKDOWN
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_ACTION
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_CONTENT
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_DATA_NOTE
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SAVE
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SHARE
import ru.stohastika.sklyanka.feature.history.TAG_SUMMARY
import ru.stohastika.sklyanka.ui.component.TAG_SCREEN_HEADER
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import ru.stohastika.sklyanka.ui.token.SklyankaSizes
import ru.stohastika.sklyanka.ui.R as UiR

@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class Sk90HistoryLayoutScreenSteps {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private var scale: Float = 1f

    @Допустим("^экран «История» собран на стенде SKL-90$")
    fun theHistoryScreenIsOnTheStand() = Unit

    @Тогда("^верхний отступ шапки «Истории» взят один раз$")
    fun theTopPaddingIsAppliedOnce() = probe(HistoryStates.september()) {
        // Крупный отступ шапка берёт один раз и с самой верхней грани (§4, §7): закреплённый
        // ряд — слой над прокруткой, до порога его не видно и места в потоке он не держит.
        // Полоса ряда перед заголовком означала бы второй отступ. Первая строка шапки —
        // ряд возврата: действия в шапке «Истории» нет (Р-88).
        val header = onNodeWithTag(TAG_SCREEN_HEADER).fetchSemanticsNode()
        val back = onNodeWithContentDescription(context.getString(UiR.string.cd_back)).fetchSemanticsNode()
        val gap = with(density) { (back.boundsInRoot.top - header.boundsInRoot.top).toDp() }
        val padding = with(density) { SklyankaSizes.HeaderTopPadding }
        assertTrue(
            "the header must take its top padding once ($padding), but its return row starts $gap below the top",
            gap <= padding + TOLERANCE,
        )
    }

    @И("^между рядом возврата и заголовком «История» зазор не больше 12 dp$")
    fun theTitleFollowsTheReturnRow() = probe(HistoryStates.september()) {
        // Ряд возврата стоит в прокручиваемой шапке — так нарисован макет «История — покой»;
        // заголовок следует за рядом. Действия в ряду нет: «Отчёт врачу» живёт в конце
        // содержимого (Р-88), поэтому нижняя граница ряда — шеврон в цели 44 dp (§7).
        val back = onNodeWithContentDescription(context.getString(UiR.string.cd_back)).fetchSemanticsNode()
        val title = onNodeWithText(context.getString(R.string.history_title)).fetchSemanticsNode()
        val gap = with(density) { (title.boundsInRoot.top - back.boundsInRoot.bottom).toDp() }
        assertTrue(
            "the title must follow the return row closely, but the gap is $gap " +
                "(title=${title.boundsInRoot}, back=${back.boundsInRoot})",
            gap in MIN_GAP..MAX_GAP,
        )
    }

    @Тогда("^в шапке «Истории» нет действия «Отчёт врачу»$")
    fun theHeaderHasNoReportAction() = probe(HistoryStates.september()) {
        val header = onNodeWithTag(TAG_SCREEN_HEADER).fetchSemanticsNode()
        val action = onNodeWithTag(TAG_REPORT_ACTION).fetchSemanticsNode()
        assertTrue(
            "the report action must stand below the header, but it is ${action.boundsInRoot} " +
                "inside ${header.boundsInRoot}",
            action.boundsInRoot.top >= header.boundsInRoot.bottom,
        )
    }

    @И("^кнопка «Отчёт врачу» стоит ниже блока «По лекарствам»$")
    fun theReportActionFollowsTheBreakdown() = probe(HistoryStates.september()) {
        val breakdown = onNodeWithTag(TAG_BREAKDOWN).fetchSemanticsNode()
        val action = onNodeWithTag(TAG_REPORT_ACTION).fetchSemanticsNode()
        assertTrue(
            "the report action must follow the breakdown, but it is ${action.boundsInRoot} " +
                "above ${breakdown.boundsInRoot}",
            action.boundsInRoot.top >= breakdown.boundsInRoot.bottom,
        )
    }

    @И("^кнопка «Отчёт врачу» занимает ширину содержимого$")
    fun theReportActionSpansTheContentWidth() = probe(HistoryStates.september()) {
        // Ширина содержимого — по ряду плиток: у карточки разбивки тег стоит внутри её поля,
        // и её габарит уже на два поля (§5.4).
        val summary = onNodeWithTag(TAG_SUMMARY).fetchSemanticsNode()
        val action = onNodeWithTag(TAG_REPORT_ACTION).fetchSemanticsNode()
        assertEquals(
            "the report action must span the content width: ${action.size.width} vs ${summary.size.width}",
            summary.size.width,
            action.size.width,
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

    @Тогда("^пояснение о медицинских данных стоит ниже карточки состава$")
    fun theNoteStandsBelowTheCard() = probe(HistoryStates.withReport()) {
        val note = onNodeWithTag(TAG_REPORT_DATA_NOTE).fetchSemanticsNode()
        val card = onNodeWithTag(TAG_REPORT_CONTENT).fetchSemanticsNode()
        assertTrue("the note must stand below the card", note.boundsInRoot.top >= card.boundsInRoot.bottom)
    }

    @И("^состав отчёта назван карточкой: профиль, период, число приёмов и формат «PDF»$")
    fun theCompositionIsACard() = probe(HistoryStates.withReport()) {
        val lines = textsOf(onNodeWithTag(TAG_REPORT_CONTENT).fetchSemanticsNode())
        assertTrue("the card must label the profile: $lines", lines.contains("Профиль"))
        assertTrue("the card must name the profile: $lines", lines.contains("profile-a"))
        assertTrue("the card must label the period: $lines", lines.contains("Период"))
        assertTrue("the card must label the count: $lines", lines.contains("Принято"))
        assertTrue("the card must name the format: $lines", lines.contains("Формат"))
        assertTrue("the card must say the format is PDF: $lines", lines.contains("PDF"))
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
