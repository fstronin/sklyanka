package ru.stohastika.sklyanka.acceptance.report

import android.content.Context
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.history.HistoryWindow
import ru.stohastika.sklyanka.domain.history.ReportPreset
import ru.stohastika.sklyanka.feature.history.HistoryIntent
import ru.stohastika.sklyanka.feature.history.HistoryScreen
import ru.stohastika.sklyanka.feature.history.HistoryStates
import ru.stohastika.sklyanka.feature.history.HistoryUiState
import ru.stohastika.sklyanka.feature.history.R
import ru.stohastika.sklyanka.feature.history.ReportOutcome
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_CONTENT
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_DATA_NOTE
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_END
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_PROFILE
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SHEET
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_START
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SUCCESS
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SUCCESS_MARK
import ru.stohastika.sklyanka.ui.component.TAG_BANNER
import ru.stohastika.sklyanka.ui.component.TAG_BOTTOM_SHEET_SCRIM
import ru.stohastika.sklyanka.ui.component.TAG_BOTTOM_SHEET_SURFACE
import ru.stohastika.sklyanka.ui.component.TAG_DATE_TIME_PICKER
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.time.LocalDate

/**
 * Экранные шаги SKL-101: шторка отчёта врачу, период, пояснение и исходы (ОВ-125).
 *
 * Каждый шаг собирает экран «Истории» настоящей композицией на заготовках `HistoryStates`
 * (состав — реальный, собранный композером). Шаги о составе PDF и имени файла живут
 * в `ReportScreenSteps` (SKL-33) и здесь не повторяются.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class Sk101ReportSheetScreenSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private var state: HistoryUiState = HistoryStates.withReport()

    // --- Предыстория -------------------------------------------------------------

    @Допустим("^шторка отчёта открыта на стенде SKL-101$")
    fun theSheetIsOpen() {
        state = HistoryStates.withReport()
    }

    /** Отчёт за диапазон: имя файла — по месяцу начала, период — полными датами. */
    @Допустим("^отчёт врачу собран за август—октябрь 2026$")
    fun theReportIsBuiltForTheRange() {
        world.history.buildReport(HistoryWindow(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 10, 31)))
    }

    // --- Действия ----------------------------------------------------------------

    @Когда("^человек выбирает пресет «([^»]+)»$")
    fun thePersonChoosesThePreset(label: String) {
        state = state.copy(report = requireNotNull(state.report).copy(preset = presetOf(label)))
    }

    @Когда("^исход передачи — «([^»]+)»$")
    fun theOutcomeIs(label: String) {
        state = state.copy(reportOutcome = outcomeOf(label))
    }

    @Когда("^человек нажимает системную «Назад»$")
    fun thePersonPressesBack() {
        val owner = RecordingBackOwner()
        var current by mutableStateOf(state)
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalOnBackPressedDispatcherOwner provides owner) {
                    SklyankaTheme {
                        HistoryScreen(
                            state = current,
                            onIntent = { intent ->
                                if (intent == HistoryIntent.ReportDismissed) current = current.copy(report = null)
                            },
                        )
                    }
                }
            }
            owner.onBackPressedDispatcher.onBackPressed()
            waitForIdle()
        }
        state = current
    }

    // --- Чипы и карточка ---------------------------------------------------------

    @Тогда("^в шторке видны чипы периода$")
    fun thePresetChipsAreVisible() = render {
        listOf(
            R.string.history_report_period_this_month,
            R.string.history_report_period_previous_month,
            R.string.history_report_period_last_three_months,
            R.string.history_report_period_custom,
        ).forEach { id ->
            onNodeWithText(context.getString(id), useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Тогда("^чип «([^»]+)» выбран$")
    fun theChipIsSelected(label: String) = render {
        onNodeWithText(label).assertIsSelected()
    }

    @Тогда("^состав отчёта показан карточкой со строками «([^»]+)», «([^»]+)», «([^»]+)», «([^»]+)»$")
    fun theCompositionIsACard(first: String, second: String, third: String, fourth: String) = render {
        onNodeWithTag(TAG_REPORT_CONTENT, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(TAG_REPORT_PROFILE, useUnmergedTree = true).assertIsDisplayed()
        listOf(first, second, third, fourth).forEach { label ->
            onNodeWithText(label, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    @Тогда("^в карточке состава есть строки «С» и «По»$")
    fun theCustomRowsAreVisible() = render {
        onNodeWithTag(TAG_REPORT_START, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(TAG_REPORT_END, useUnmergedTree = true).assertIsDisplayed()
    }

    @Тогда("^строка «С» открывает диалог даты «([^»]+)»$")
    fun theStartRowOpensTheDateDialog(title: String) {
        runComposeUiTest {
            setContent { Content() }
            onNodeWithTag(TAG_REPORT_START, useUnmergedTree = true).performClick()
            waitForIdle()
            onNodeWithTag(TAG_DATE_TIME_PICKER, useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText(title, useUnmergedTree = true).assertIsDisplayed()
        }
    }

    // --- Пояснение и слой --------------------------------------------------------

    @Тогда("^тихое пояснение о медицинских данных стоит под карточкой$")
    fun theNoteIsBelowTheCard() = render {
        val note = onNodeWithTag(TAG_REPORT_DATA_NOTE, useUnmergedTree = true).fetchSemanticsNode()
        val card = onNodeWithTag(TAG_REPORT_CONTENT, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("the note must stand below the card", note.boundsInRoot.top >= card.boundsInRoot.bottom)
        onNodeWithText(context.getString(R.string.history_report_data_note), useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Тогда("^предупреждающей плашки в шторке нет$")
    fun thereIsNoWarningBanner() = render {
        onAllNodesWithTag(TAG_BANNER, useUnmergedTree = true).assertCountEquals(0)
    }

    @Тогда("^полотно шторки ниже экрана, а история видна под затемнением$")
    fun theSheetIsNotFullscreen() = render {
        val root = onNodeWithTag(ru.stohastika.sklyanka.feature.history.TAG_HISTORY_SCREEN).fetchSemanticsNode()
        val surface = onNodeWithTag(TAG_BOTTOM_SHEET_SURFACE, useUnmergedTree = true).fetchSemanticsNode()
        assertTrue("the sheet must not fill the screen", surface.size.height < root.size.height)
        onNodeWithTag(TAG_BOTTOM_SHEET_SCRIM, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(ru.stohastika.sklyanka.feature.history.TAG_HISTORY_SCREEN).assertIsDisplayed()
    }

    @Тогда("^ширина полотна равна ширине экрана$")
    fun theSheetSpansTheScreen() = render {
        val root = onNodeWithTag(ru.stohastika.sklyanka.feature.history.TAG_HISTORY_SCREEN).fetchSemanticsNode()
        val surface = onNodeWithTag(TAG_BOTTOM_SHEET_SURFACE, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(root.size.width, surface.size.width)
    }

    @Тогда("^шторка отчёта закрыта, а экран «История» остался$")
    fun theSheetIsDismissedByBack() = render {
        onNodeWithTag(TAG_REPORT_SHEET, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag(ru.stohastika.sklyanka.feature.history.TAG_HISTORY_SCREEN).assertIsDisplayed()
    }

    // --- Исходы ------------------------------------------------------------------

    @Тогда("^в шторке видно сообщение «([^»]+)»$")
    fun theMessageIsVisible(message: String) = render {
        onNodeWithText(message, useUnmergedTree = true).assertIsDisplayed()
    }

    @Тогда("^состав отчёта остаётся виден$")
    fun theCompositionStaysVisible() = render {
        onNodeWithTag(TAG_REPORT_CONTENT, useUnmergedTree = true).assertIsDisplayed()
    }

    @Тогда("^в шторке видно «([^»]+)» и «([^»]+)»$")
    fun twoPhrasesAreVisible(first: String, second: String) = render {
        onNodeWithTag(TAG_REPORT_SUCCESS, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(first, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText(second, useUnmergedTree = true).assertIsDisplayed()
    }

    @Тогда("^видна контурная галочка$")
    fun theCheckGlyphIsVisible() = render {
        onNodeWithTag(TAG_REPORT_SUCCESS_MARK, useUnmergedTree = true).assertIsDisplayed()
    }

    @Тогда("^чипов периода и карточки состава в шторке нет$")
    fun theContentIsReplaced() = render {
        onNodeWithTag(TAG_REPORT_CONTENT, useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText(context.getString(R.string.history_report_period_this_month), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Тогда("^тихая кнопка шапки называется «([^»]+)»$")
    fun theHeaderQuietButtonIs(label: String) = render {
        onNodeWithText(label).assertIsDisplayed()
    }

    // --- Обвязка -----------------------------------------------------------------

    private fun render(question: ComposeUiTest.() -> Unit) = runComposeUiTest {
        setContent { Content() }
        question(this)
    }

    @Composable
    private fun Content() {
        SklyankaTheme { HistoryScreen(state = state, onIntent = {}) }
    }

    private fun presetOf(label: String): ReportPreset = when (label) {
        context.getString(R.string.history_report_period_this_month) -> ReportPreset.THIS_MONTH
        context.getString(R.string.history_report_period_previous_month) -> ReportPreset.PREVIOUS_MONTH
        context.getString(R.string.history_report_period_last_three_months) -> ReportPreset.LAST_THREE_MONTHS
        context.getString(R.string.history_report_period_custom) -> ReportPreset.CUSTOM
        else -> error("unknown preset: $label")
    }

    private fun outcomeOf(label: String): ReportOutcome = when (label) {
        context.getString(R.string.history_report_cancelled) -> ReportOutcome.CANCELLED
        context.getString(R.string.history_report_share_failed) -> ReportOutcome.FAILED_SHARE
        context.getString(R.string.history_report_save_failed) -> ReportOutcome.FAILED_SAVE
        context.getString(R.string.history_report_success_shared) -> ReportOutcome.SHARED
        context.getString(R.string.history_report_success_saved) -> ReportOutcome.SAVED
        else -> error("unknown outcome: $label")
    }
}

/**
 * Владелец диспетчера системной «Назад» без Activity (ОВ-125).
 *
 * Настоящий `BackHandler` шторки регистрируется на владельце, которого даёт композиция;
 * стенд подставляет свой, и нажатие идёт через обработчик продукта, а не через пересказ
 * шага. Владельцем жизненного цикла он же и является: обработчик привязывается к нему
 * и обязан быть в состоянии `RESUMED` — иначе он выключен.
 */
private class RecordingBackOwner :
    OnBackPressedDispatcherOwner,
    LifecycleOwner {

    override val onBackPressedDispatcher = OnBackPressedDispatcher()

    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = registry

    init {
        registry.currentState = Lifecycle.State.RESUMED
    }
}
