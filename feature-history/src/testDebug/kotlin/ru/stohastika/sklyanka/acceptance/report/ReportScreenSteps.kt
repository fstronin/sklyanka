package ru.stohastika.sklyanka.acceptance.report

import android.content.Context
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.history.HistoryScreenProbe
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.history.DoctorReport
import ru.stohastika.sklyanka.domain.history.OnTimeShare
import ru.stohastika.sklyanka.feature.history.AndroidPdfTextMetrics
import ru.stohastika.sklyanka.feature.history.HistoryIntent
import ru.stohastika.sklyanka.feature.history.PdfPageGeometry
import ru.stohastika.sklyanka.feature.history.PdfReportLayout
import ru.stohastika.sklyanka.feature.history.PdfReportPage
import ru.stohastika.sklyanka.feature.history.R
import ru.stohastika.sklyanka.feature.history.ReportPdfComposer
import ru.stohastika.sklyanka.feature.history.ReportPdfContent
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_CONTENT
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SAVE
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_SHARE
import ru.stohastika.sklyanka.feature.history.documentText
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Экранные шаги приёмки отчёта врачу в PDF (SKL-33, группа `@screen`).
 *
 * Шаги живут в `feature-history`, потому что говорят об одном документе этого экрана
 * (`SKL-26/TDD.md §4.3`): состав собирает настоящий [ReportPdfComposer], страницы
 * раскладывает настоящий [PdfReportLayout], а шторка — собранная композиция.
 * Настоящий PDF здесь не рисуется: `PdfDocument` работает только на платформе,
 * и байты проверяет сценарий `@device` (`SKL-33/TDD.md §12`).
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class ReportScreenSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    // --- Предыстория ---------------------------------------------------------------

    /**
     * Документ длиннее страницы: столько лекарств, что блоки заведомо не помещаются
     * на один лист A4. Заводится настоящим хранилищем мира — расчёт и вёрстка видят
     * обычные данные, а не подложенную страницу.
     */
    @Given("^в отчёте за сентябрь 2026 больше блоков лекарств, чем помещается на одну страницу$")
    fun moreBlocksThanFitAPage() {
        repeat(MANY_MEDICATIONS) { index ->
            val name = "course-%02d".format(index + 1)
            world.medications.add(
                profile = world.profiles,
                profileName = world.profiles.active,
                name = name,
                times = listOf(LocalTime.of(8, 0)),
            )
            // У лекарства должен быть приём в месяце, иначе строка отчёта не появится:
            // состав отчёта — месячная картина, а не список курсов.
            world.intakes.markTaken(name, LocalDate.of(2026, 9, 1).atTime(8, 0))
        }
    }

    // --- Документ: формат и состав -------------------------------------------------

    @Then("^отчёт назван файлом «([^»]+)»$")
    fun theReportIsNamedByThatFile(fileName: String) {
        assertEquals("the report must be offered under this name", fileName, documentContent().fileName)
    }

    @Then("^тип отчёта — «([^»]+)»$")
    fun theReportCarriesThatMediaType(mediaType: String) {
        assertEquals("the report must be a file of this type", mediaType, documentContent().mediaType)
    }

    @Then("^в отчёте названы дата и время формирования$")
    fun theReportNamesTheMomentItWasBuilt() {
        val report = report()
        val expected = report.generatedAt.atZone(world.clock.zone).format(
            DateTimeFormatter.ofPattern(context.getString(R.string.history_datetime_pattern), locale()),
        )
        assertTrue(
            "the report must carry the moment of formation: $expected",
            documentText().contains(expected),
        )
    }

    @Then("^числа сводки в отчёте совпадают с числами экрана истории$")
    fun theSummaryNumbersMatchTheScreen() {
        val summary = documentContent().summary
        val screen = HistoryScreenProbe.state(world).period

        val taken = screen.byMedication.sumOf { it.taken }
        val planned = screen.byMedication.sumOf { it.planned }
        assertEquals(
            "the taken line must be the sum of the rows the screen shows",
            context.resources.getQuantityString(
                R.plurals.report_document_intakes_of_total,
                planned,
                taken,
                planned,
                context.getString(R.string.history_percent, Math.round(taken.toDouble() / planned * 100).toInt()),
            ),
            summary.first { it.label == context.getString(R.string.report_document_taken_label) }.value,
        )
        assertEquals(
            "the on time share must be the same as on the screen (ОВ-4)",
            when (val share = screen.onTimeShare) {
                is OnTimeShare.Value -> context.getString(R.string.history_percent, share.percent)
                OnTimeShare.Undefined -> context.getString(R.string.history_value_dash)
            },
            summary.first { it.label == context.getString(R.string.report_document_on_time_label) }.value,
        )
        assertEquals(
            "the days without misses must be the same as on the screen",
            screen.daysWithoutMisses.toString(),
            summary.first { it.label == context.getString(R.string.report_document_days_without_misses_label) }.value,
        )
        assertEquals(
            "the misses of the period must be the same as on the screen",
            screen.missesInPeriod.toString(),
            summary.first { it.label == context.getString(R.string.report_document_misses_label) }.value,
        )
    }

    @Then("^в отчёте у «([^»]+)» написано «([^»]+)»$")
    fun theReportSaysThatOf(medication: String, phrase: String) {
        // Имя сценария — ключ мира; в документе стоит имя записи (НФТ-19).
        val name = world.medications.get(world.medicationNamed(medication)).name
        val block = blockOf(name)
        // Русская локаль Android ставит перед знаком процента неразрывный пробел:
        // в Gherkin он невидим, и сравнение нормализует его, а не подгоняет текст.
        assertEquals(
            "the share of «$name» must be printed next to its block",
            phrase,
            block.right.orEmpty().replace('\u00A0', ' '),
        )
    }

    @Then("^в отчёте пропуск человека отделён от пропуска без отметки$")
    fun theReportKeepsTheMarksApart() {
        val report = report()
        val text = documentText()
        report.medications.filter { it.skippedByPerson > 0 }.forEach { row ->
            assertTrue(
                "the report must name the marks of the person for «${row.name}»",
                text.contains(
                    context.resources.getQuantityString(
                        R.plurals.report_document_missed_by_person,
                        row.skippedByPerson,
                        row.skippedByPerson,
                    ),
                ),
            )
        }
        report.medications.filter { it.missedWithoutMark > 0 }.forEach { row ->
            assertTrue(
                "the report must name the automatic misses of «${row.name}»",
                text.contains(context.getString(R.string.report_document_missed_without_mark, row.missedWithoutMark)),
            )
        }
        assertTrue(
            "the scenario must leave both natures of a miss in the report",
            report.medications.any { it.skippedByPerson > 0 } && report.medications.any { it.missedWithoutMark > 0 },
        )
    }

    @Then("^в отчёте есть строка «([^»]+)»$")
    fun theReportHasTheLine(line: String) {
        assertTrue("no «$line» in the document: ${documentText()}", documentText().contains(line))
    }

    @Then("^блока «По лекарствам» в отчёте нет$")
    fun theMedicationBlockIsAbsent() {
        assertNull("the medication block must not be printed", documentContent().medicationsTitle)
        assertTrue(documentContent().medicationBlocks.isEmpty())
    }

    /**
     * Пустой месяц: сводки нет вместе с заголовком и числами.
     *
     * «Принято: 0 из 0 приёмов (0 %)» — вырожденная дробь, а «Дней без пропусков: 0» —
     * наблюдение, которого не было (решение дизайна, ревизия 33-B SKL-33).
     */
    @Then("^сводки в отчёте нет$")
    fun theSummaryIsAbsent() {
        val content = documentContent()
        assertNull("the summary block must not be printed", content.summaryTitle)
        assertTrue("no summary line may be printed", content.summary.isEmpty())
        val text = content.documentText()
        SUMMARY_LABELS.forEach { label ->
            assertFalse("no «$label» may reach the doctor: $text", text.contains(label))
        }
    }

    @Then("^оговорка «([^»]+)» есть на каждой странице отчёта$")
    fun everyPageCarriesTheDisclaimer(disclaimer: String) {
        val pages = pages()
        assertTrue("the document must have at least one page", pages.isNotEmpty())
        pages.forEach { page ->
            assertEquals("page ${page.pageNumber} must carry the disclaimer", disclaimer, page.disclaimer)
        }
    }

    @Then("^ни один блок лекарства в отчёте не разорван между страницами$")
    fun noMedicationBlockIsSplit() {
        val content = documentContent()
        val metrics = AndroidPdfTextMetrics(context)
        val pages = PdfReportLayout(metrics, Page).paginate(content)
        assertTrue("the scenario must leave more than one page", pages.size > 1)
        val width = Page.width - Page.marginSide * 2
        content.medicationBlocks.forEach { block ->
            // Физические строки блока обязаны стоять на одной странице подряд,
            // а не быть найденными по имени на двух соседних: это и есть «не разорван».
            val physical = block.lines.flatMap { line ->
                metrics.wrap(line.text, line.role, width).map { text -> text to line.role }
            }
            val holding = pages.filter { page ->
                val lines = page.body.map { it.text to it.role }
                lines.windowed(physical.size).any { window -> window == physical }
            }
            assertEquals(
                "the block of «${block.lines.first().text}» must be on exactly one page, not ${holding.map {
                    it.pageNumber
                }}",
                1,
                holding.size,
            )
        }
    }

    @Then("^отчёт назван профилем «([^»]+)» и не содержит лекарств профиля «([^»]+)»$")
    fun theReportNamesOneProfileAndHidesTheOther(profile: String, foreign: String) {
        assertEquals("the scenario speaks about the active profile", profile, world.profiles.active)
        val report = report()
        assertEquals("the document must be built for the active profile", profile, report.profileName)
        val text = documentText()
        assertTrue(
            "the document must name its profile (ОВ-57)",
            text.contains(context.getString(R.string.report_document_profile, profile)),
        )
        val foreignId = world.profiles.get(foreign).id
        world.medications.all()
            .filter { it.profileId == foreignId }
            .forEach { medication ->
                assertFalse(
                    "a medication of «$foreign» must not appear in the document: ${medication.name}",
                    text.contains(medication.name),
                )
            }
    }

    // --- Шторка ---------------------------------------------------------------------

    @Then("^в шторке показан состав отчёта: профиль, период и число принятых приёмов$")
    fun theSheetShowsTheComposition() {
        val report = report()
        val lines = HistoryScreenProbe.ask(world) { test -> sheetTexts(test) }.joinToString("\n")
        assertTrue(
            "the sheet must name the profile: $lines",
            lines.contains(report.profileName),
        )
        assertTrue(
            "the sheet must label the profile: $lines",
            lines.contains(context.getString(R.string.history_report_label_profile)),
        )
        assertTrue(
            "the sheet must label the period: $lines",
            lines.contains(context.getString(R.string.history_report_label_period)),
        )
        assertTrue(
            "the sheet must label the taken count: $lines",
            lines.contains(context.getString(R.string.history_report_label_taken)),
        )
    }

    @Then("^шторка предлагает «Передать» и «Сохранить файлом»$")
    fun theSheetOffersBothActions() {
        val intents = HistoryScreenProbe.act(world) { test ->
            test.onNodeWithTag(TAG_REPORT_SHARE, useUnmergedTree = true).performClick()
            test.onNodeWithTag(TAG_REPORT_SAVE, useUnmergedTree = true).performClick()
        }
        assertTrue("the sheet must offer the system exchange", intents.contains(HistoryIntent.ShareReport))
        assertTrue("the sheet must offer a file", intents.contains(HistoryIntent.SaveRequested))
    }

    /** Шторка пустого месяца: та же фраза, что в документе, вместо вырожденной доли (33-B). */
    @Then("^в шторке вместо числа принятых приёмов стоит «([^»]+)»$")
    fun theSheetSaysThePhraseInsteadOfTheCount(phrase: String) {
        assertTrue("the scenario must speak about an empty month", report().period.isEmpty)
        val lines = HistoryScreenProbe.ask(world) { test -> sheetTexts(test) }.joinToString("\n")
        assertTrue("the sheet must carry the phrase: $lines", lines.contains(phrase))
        assertFalse(
            "no count may be promised in an empty month: $lines",
            lines.contains(context.getString(R.string.history_report_label_taken)),
        )
    }

    // --- Обвязка ---------------------------------------------------------------------

    private fun report(): DoctorReport = requireNotNull(world.history.report) { "the scenario never built a report" }

    private fun documentContent(): ReportPdfContent = ReportPdfComposer(context).compose(report(), world.clock.zone)

    private fun documentText(): String = documentContent().documentText()

    private fun pages(): List<PdfReportPage> =
        PdfReportLayout(AndroidPdfTextMetrics(context), Page).paginate(documentContent())

    private fun blockOf(name: String) =
        requireNotNull(documentContent().medicationBlocks.firstOrNull { it.lines.first().text.startsWith(name) }) {
            "no block of «$name» in the document"
        }

    private fun sheetTexts(test: ComposeUiTest): List<String> = test
        .onAllNodesWithTag(TAG_REPORT_CONTENT, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .flatMap { textsOf(it) }

    private fun textsOf(node: SemanticsNode): List<String> =
        node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } +
            node.children.flatMap { textsOf(it) }

    private fun locale() = context.resources.configuration.locales[0]

    private companion object {
        const val MANY_MEDICATIONS = 40

        /** Геометрия документа: та же, что у painter'а; страницы приёмки считаются по ней. */
        val Page = PdfPageGeometry()

        /** Ярлыки сводки: при пустом периоде в документе не должно быть ни одного. */
        val SUMMARY_LABELS = listOf(
            "СВОДКА",
            "Принято:",
            "Принято вовремя:",
            "Дней без пропусков:",
            "Пропусков за период:",
        )
    }
}
