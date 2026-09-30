package ru.stohastika.sklyanka.acceptance.report

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.en.Then
import ru.stohastika.sklyanka.acceptance.history.HistoryScreenProbe
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.feature.history.R
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SAVE
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SHARE
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SHEET
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_WARNING

/**
 * Экранные шаги предъявления шторки отчёта врачу (SKL-44, группа `@screen`).
 *
 * Каждый шаг собирает экран в той же обёртке `ProfileChrome`, в какой его собирает корень
 * приложения: дефект SKL-44 жил ровно в разнице между прямой композицией стенда и реальным
 * корнем, и проверка без обёртки его не ловила (`SKL-44/TDD.md` §3). Состав шторки,
 * документ и передача здесь не пересобираются — их держат шаги SKL-33.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
class ReportSheetSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Then("^над экраном истории предъявлена шторка отчёта$")
    fun theSheetIsPresentedOverTheHistory() {
        HistoryScreenProbe.ask(world, chrome = HistoryScreenProbe.chrome(world)) { test ->
            test.onNodeWithTag(TAG_REPORT_SHEET).assertIsDisplayed()
        }
    }

    @Then("^в шторке отчёта видно предупреждение о медицинских данных$")
    fun theWarningIsVisibleInTheSheet() {
        HistoryScreenProbe.ask(world, chrome = HistoryScreenProbe.chrome(world)) { test ->
            test.onNodeWithTag(TAG_REPORT_WARNING).assertIsDisplayed()
            test.onNodeWithText(context.getString(R.string.history_report_warning), useUnmergedTree = true)
                .assertIsDisplayed()
        }
    }

    @Then("^в шторке отчёта видны «Передать» и «Сохранить файлом»$")
    fun bothActionsAreVisibleInTheSheet() {
        HistoryScreenProbe.ask(world, chrome = HistoryScreenProbe.chrome(world)) { test ->
            test.onNodeWithTag(TAG_REPORT_SHARE).assertIsDisplayed()
            test.onNodeWithTag(TAG_REPORT_SAVE).assertIsDisplayed()
            test.onNodeWithText(context.getString(R.string.history_report_share), useUnmergedTree = true)
                .assertIsDisplayed()
            test.onNodeWithText(context.getString(R.string.history_report_save), useUnmergedTree = true)
                .assertIsDisplayed()
        }
    }
}
