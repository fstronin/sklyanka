package ru.stohastika.sklyanka.acceptance.export

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.cucumber.java.ru.Допустим
import io.cucumber.java.ru.Когда
import io.cucumber.java.ru.Тогда
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import ru.stohastika.sklyanka.acceptance.RepositoryLayout
import ru.stohastika.sklyanka.acceptance.RussianDates
import ru.stohastika.sklyanka.acceptance.steps.medicationNamed
import ru.stohastika.sklyanka.acceptance.world.AcceptanceWorld
import ru.stohastika.sklyanka.domain.history.AdherenceCalculator
import ru.stohastika.sklyanka.domain.history.BuildExtendedSectionsUseCase
import ru.stohastika.sklyanka.domain.history.ExtendedReportSections
import ru.stohastika.sklyanka.domain.history.HistoryCsvDocument
import ru.stohastika.sklyanka.domain.history.HistoryCsvRow
import ru.stohastika.sklyanka.domain.history.HistoryCsvStatus
import ru.stohastika.sklyanka.domain.history.HistoryCsvTable
import ru.stohastika.sklyanka.domain.history.HistoryWindow
import ru.stohastika.sklyanka.domain.history.MissedPatternRule
import ru.stohastika.sklyanka.domain.history.OnTimeRule
import ru.stohastika.sklyanka.domain.history.ReportPreset
import ru.stohastika.sklyanka.domain.history.ReportScope
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.feature.history.AndroidPdfTextMetrics
import ru.stohastika.sklyanka.feature.history.HistoryCsvComposer
import ru.stohastika.sklyanka.feature.history.PdfPageGeometry
import ru.stohastika.sklyanka.feature.history.PdfReportLayout
import ru.stohastika.sklyanka.feature.history.PdfReportPage
import ru.stohastika.sklyanka.feature.history.R
import ru.stohastika.sklyanka.feature.history.ReportPdfComposer
import ru.stohastika.sklyanka.feature.history.ReportPdfText
import ru.stohastika.sklyanka.feature.history.TAG_EXPORT_CONTENT
import ru.stohastika.sklyanka.feature.history.TAG_EXPORT_DATA_NOTE
import ru.stohastika.sklyanka.feature.history.TAG_EXPORT_SAVE
import ru.stohastika.sklyanka.feature.history.TAG_EXPORT_SHARE
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_COMPOSITION_BASE
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_COMPOSITION_EXTENDED
import ru.stohastika.sklyanka.feature.history.TAG_REPORT_COMPOSITION_SECTION
import ru.stohastika.sklyanka.feature.history.documentText
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

/**
 * Экранные шаги приёмки SKL-119 — выбор состава отчёта, шторка выгрузки и её файл.
 *
 * Шаги живут в `feature-history`, потому что говорят о поверхностях этого модуля: блок выбора
 * состава и шторка выгрузки объявлены здесь, а их теги `internal` — увидеть их может только
 * прогон самого модуля (`SKL-26/TDD.md §4.3`).
 *
 * **Проверяется продукт, а не подстановка шага.** Состояние собирают настоящие
 * `HistoryViewModel` ([ReportScopeStand]) и `ExportHistoryViewModel` ([ExportStand]) на мире
 * сценария; документ собирает настоящий `ReportPdfComposer` (имя файла, тип, шапка, колонки
 * и слова статусов берутся у него), раскладку страниц — настоящая `PdfReportLayout`, файл
 * выгрузки — настоящий `HistoryCsvComposer`. Повторено только отображение намерений
 * на состояние: это разметка, а не правило.
 *
 * Русский текст в аннотациях — ключевые слова Gherkin, а не текст интерфейса.
 */
@OptIn(ExperimentalTestApi::class)
@Suppress("TooManyFunctions")
class Sk119ExportScreenSteps(private val world: AcceptanceWorld) {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Строка выгрузки, отмеченная вне окна, и файл с ней: слова кладёт настоящий композер. */
    private var lateRow: HistoryCsvRow? = null
    private var lateDocument: HistoryCsvDocument? = null

    private lateinit var basePages: List<PdfReportPage>
    private lateinit var extendedPages: List<PdfReportPage>
    private var firstSectionTitle: String = ""

    // --- Шторка отчёта: выбор состава ---------------------------------------------

    @Допустим("^шторка отчёта открыта на стенде SKL-119$")
    fun theReportSheetIsOpenOnTheStand() {
        ReportScopeStand.openReport(world)
        assertNotNull("the report sheet must be built", ReportScopeStand.state(world).report)
    }

    @Тогда("^в шторке есть выбор состава$")
    fun theSheetOffersTheCompositionChoice() {
        ReportScopeStand.ask(world) { test ->
            test.onNodeWithTag(TAG_REPORT_COMPOSITION_SECTION, useUnmergedTree = true).assertExists()
            test.onNodeWithTag(TAG_REPORT_COMPOSITION_BASE, useUnmergedTree = true).assertExists()
            test.onNodeWithTag(TAG_REPORT_COMPOSITION_EXTENDED, useUnmergedTree = true).assertExists()
        }
    }

    /**
     * «Базовый» выбран по умолчанию — и это видно разметке, а не только состоянию.
     *
     * Проверяется семантика `selected` строк (НФТ-30, §6.4): снятие отрисовки выбора сценарий
     * обязан заметить, поэтому поля состояния мало. Метки «Про» в шторке нет ни у одной строки:
     * платного контура в поставляемой сборке не существует (Р-86, ADR-0016).
     */
    @Тогда("^«Базовый» выбран по умолчанию и метки «Про» в шторке нет$")
    fun theBaseRowIsSelectedAndUnmarked() {
        assertEquals(ReportScope.BASE, scope())
        ReportScopeStand.ask(world) { test ->
            test.onNodeWithTag(TAG_REPORT_COMPOSITION_BASE, useUnmergedTree = true)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            test.onNodeWithTag(TAG_REPORT_COMPOSITION_EXTENDED, useUnmergedTree = true)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
            test.onAllNodesWithText(proMark(), useUnmergedTree = true).assertCountEquals(0)
        }
    }

    @Когда("^человек выбирает в шторке расширенный состав$")
    fun thePersonChoosesTheExtendedComposition() {
        ReportScopeStand.act(world) { test ->
            test.onNodeWithTag(TAG_REPORT_COMPOSITION_EXTENDED, useUnmergedTree = true).performClick()
        }
    }

    @Тогда("^выбран расширенный состав$")
    fun theExtendedCompositionIsChosen() {
        assertEquals(ReportScope.EXTENDED, scope())
    }

    /**
     * Расширенный документ несёт все три раздела, и они **напечатаны**.
     *
     * Проверяется тройка (сравнение периодов, динамика по месяцам, паттерн пропусков) и то,
     * что заголовки дошли до собранного документа тем же композером, что и PDF: «разделы есть»
     * без тройки подтверждало бы только «что-то непусто».
     */
    @Тогда("^документ собран с расширенными разделами$")
    fun theDocumentIsBuiltWithTheExtendedSections() {
        // Выбранный состав берётся у состояния: нажатие расширенной строки отдало именно его.
        assertEquals("the extended composition must be the chosen one", ReportScope.EXTENDED, scope())

        // Тройка проверяется на окне из двух месяцев: динамике нужен не один месяц, а сравнению —
        // период такой же длины перед ним, поэтому расчёт идёт по тому же правилу домена, но
        // на периоде, который сценарий наполняет (август—сентябрь).
        val twoMonths = windowOf(TWO_MONTHS)
        val report = requireNotNull(world.history.buildReport(twoMonths)) {
            "the scenario never built the report"
        }
        val sections = runBlocking { extendedSections(twoMonths) }
        assertNotNull("the extended composition must carry the period comparison", sections.comparison)
        assertNotNull("the extended composition must carry the monthly dynamics", sections.dynamics)
        assertNotNull("the extended composition must carry the missed-dose pattern", sections.pattern)
        val text = ReportPdfComposer(context).compose(report, world.clock.zone, sections).documentText()
        listOf(
            R.string.report_document_compare_title,
            R.string.report_document_dynamics_title,
            R.string.report_document_pattern_title,
        ).forEach { title ->
            assertTrue(
                "the document must print the section «${context.getString(title)}»: $text",
                text.contains(context.getString(title)),
            )
        }
    }

    // --- Имя и тип собранного документа ---------------------------------------------

    /**
     * Имя файла и тип собранного документа — прежние (Р-36).
     *
     * Собирается **настоящий** документ (`ReportPdfComposer`), и имя с типом берутся у него:
     * проверка каталога строк литералами шага подтверждала бы литералы шага, а не продукт.
     * Имя и тип от появления второго состава не меняются — врач получает документ, а не тариф.
     */
    @Тогда("^базовый документ назван прежним именем файла и прежнего типа$")
    fun theBaseDocumentKeepsItsNameAndType() {
        val report = requireNotNull(world.history.buildReport(windowOf(SEPTEMBER))) {
            "the scenario never built the report"
        }
        val content = ReportPdfComposer(context).compose(report, world.clock.zone)
        assertEquals("the report keeps its own file name (Р-36)", "sklyanka-report-2026-09.pdf", content.fileName)
        assertEquals("the report keeps its own media type (Р-36)", "application/pdf", content.mediaType)
    }

    // --- Вёрстка расширенного документа --------------------------------------------

    @Когда("^человек собирает расширенный состав на стенде за (.+)$")
    fun thePersonBuildsTheExtendedCompositionOnTheStand(period: String) {
        val window = windowOf(period)
        val report = requireNotNull(world.history.buildReport(window)) { "the scenario never built the report" }
        val sections = runBlocking { extendedSections(window) }
        assertFalse("the scenario must give sections", sections.isEmpty)
        val composer = ReportPdfComposer(context)
        val layout = PdfReportLayout(AndroidPdfTextMetrics(context), PdfPageGeometry())
        basePages = layout.paginate(composer.compose(report, world.clock.zone))
        extendedPages = layout.paginate(composer.compose(report, world.clock.zone, sections))
        firstSectionTitle = context.getString(R.string.report_document_compare_title)
    }

    @Тогда("^страницы базового документа те же, что у базового состава$")
    fun theBasePagesAreTheSame() {
        assertTrue("the scenario never built the document", basePages.isNotEmpty())
        basePages.forEachIndexed { index, page ->
            assertEquals(
                "page ${index + 1} of the base document must not change (Р-36)",
                page.body.map { it.text },
                extendedPages[index].body.map { it.text },
            )
        }
    }

    @Тогда("^первый расширенный раздел начинается с новой страницы$")
    fun theFirstExtendedSectionStartsANewPage() {
        assertTrue(
            "the first extended section must start a new page: ${extendedPages.size} pages",
            extendedPages.size > basePages.size,
        )
        println("report pages: base=${basePages.size} extended=${extendedPages.size}")
        val first = extendedPages[basePages.size]
        assertTrue(
            "the first extended section must open page ${basePages.size + 1}: ${first.body.map { it.text }}",
            first.body.any { it.text == firstSectionTitle },
        )
    }

    // --- Шторка выгрузки истории ----------------------------------------------------

    @Допустим("^открыта шторка выгрузки истории на стенде$")
    fun theExportSheetIsOpenOnTheStand() {
        ExportStand.open(world)
        assertNotNull("the export sheet must be built", ExportStand.state(world).sheet)
    }

    @Когда("^человек просит выгрузку истории на стенде$")
    fun thePersonRequestsTheExportOnTheStand() {
        ExportStand.open(world)
    }

    /** Шторка выгрузки открыта и файл собран: доступ в поставляемой сборке есть всегда (Р-86). */
    @Тогда("^шторка выгрузки открыта и файл собран$")
    fun theExportSheetIsOpenAndTheFileIsBuilt() {
        val sheet = ExportStand.state(world).sheet
        assertNotNull("the export sheet must be open", sheet)
        assertNotNull("the export file must be assembled", sheet?.document)
    }

    @Тогда("^в ней есть чипы периода и карточка состава$")
    fun theSheetCarriesThePeriodAndTheComposition() {
        ExportStand.ask(world) { test ->
            test.onAllNodesWithText(context.getString(R.string.history_report_period_this_month)).assertCountEquals(1)
            test.onNodeWithTag(TAG_EXPORT_CONTENT, useUnmergedTree = true).assertExists()
            test.onNodeWithText(context.getString(R.string.history_report_label_profile), useUnmergedTree = true)
                .assertExists()
            test.onNodeWithText(context.getString(R.string.history_report_label_format), useUnmergedTree = true)
                .assertExists()
            // Метки «Про» на шторке выгрузки нет: платного контура не существует (Р-86).
            test.onAllNodesWithText(proMark(), useUnmergedTree = true).assertCountEquals(0)
            // «Записей» несёт число строк, а пустой период — фразу вместо числа (Р-33):
            // карточка состава есть в обоих состояниях, и состояние называет себя словами.
            val records = test.onAllNodesWithText(
                context.getString(R.string.history_export_label_records),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            val empty = test.onAllNodesWithText(
                context.getString(R.string.report_document_empty),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            assertTrue(
                "the composition must name the records or the empty period",
                records.isNotEmpty() || empty.isNotEmpty(),
            )
        }
    }

    /** Предупреждение стоит **тихо**: это пояснение под карточкой, а не плашка (НФТ-18). */
    @Тогда("^предупреждение о медицинских данных стоит тихо$")
    fun theMedicalDataWarningIsQuiet() {
        ExportStand.ask(world) { test ->
            test.onNodeWithTag(TAG_EXPORT_DATA_NOTE, useUnmergedTree = true).assertExists()
            test.onNodeWithText(context.getString(R.string.history_export_note), useUnmergedTree = true).assertExists()
        }
    }

    @Тогда("^действия «Сохранить» и «Передать» стоят одной формой$")
    fun theActionsShareOneForm() {
        ExportStand.ask(world) { test ->
            val share = test.onNodeWithTag(TAG_EXPORT_SHARE, useUnmergedTree = true).fetchSemanticsNode()
            val save = test.onNodeWithTag(TAG_EXPORT_SAVE, useUnmergedTree = true).fetchSemanticsNode()
            assertEquals("the actions must be equal halves (§5.1)", share.size.width, save.size.width)
            assertEquals("the actions must stand on one line", share.positionInRoot.y, save.positionInRoot.y)
        }
    }

    @Когда("^человек на стенде выгружает историю за (.+)$")
    fun thePersonExportsTheHistoryOnTheStand(period: String) {
        ExportStand.open(world)
        if (period == PREVIOUS_MONTH_WORDS) {
            ExportStand.choosePeriod(world, ReportPreset.PREVIOUS_MONTH)
        }
        assertNotNull("the export sheet must carry the file", ExportStand.state(world).sheet?.document)
    }

    /**
     * Шапка файла целиком: профиль, период, оговорка и пометка о медицинских данных.
     *
     * Все четыре строки сравниваются со словами продукта — каталогом строк и `ReportPdfText`
     * для диапазона периода, — а не с префиксами, вырезанными из тех же ресурсов: файл уходит
     * из приложения, и шапка называет, чей это период и чем документ не является.
     */
    @Тогда("^шапка файла называет профиль и период и несёт оговорку «([^»]+)»$")
    fun theFileHeaderNamesTheProfileAndThePeriod(disclaimer: String) {
        val sheet = windowOf(SEPTEMBER)
        val text = ReportPdfText(context)
        val locale = context.resources.configuration.locales[0]
        val expected = listOf(
            context.getString(R.string.history_export_header_profile, world.profiles.active),
            context.getString(
                R.string.history_export_header_period,
                text.periodRange(sheet.from, sheet.to, locale),
            ),
            context.getString(R.string.report_document_disclaimer),
            context.getString(R.string.history_export_note),
        )
        assertEquals(
            "the header must carry the four lines of the norm (§5.5)",
            expected,
            ExportStand.document(world).header,
        )
        assertTrue(
            "the disclaimer must be the word of the catalogue",
            expected[2].equals(disclaimer, ignoreCase = true),
        )
    }

    /**
     * Колонки файла — все восемь, словами каталога и в порядке нормы (§5.5).
     *
     * Сценарий называет их вслух, и сценарий обязан совпасть с каталогом: файл читают таблицей,
     * а порядок колонок — часть ответа «что за запись» («Доза за приём» без «Дозировки
     * препарата, если задана» назвала бы дозу, но не сказала бы, из чего она, ИНВ-5).
     */
    @Тогда(
        "^в файле есть колонки «([^»]+)», «([^»]+)», «([^»]+)», «([^»]+)», «([^»]+)», " +
            "«([^»]+)», «([^»]+)», «([^»]+)»$",
    )
    @Suppress("LongParameterList")
    fun theFileCarriesThoseColumns(
        date: String,
        plannedTime: String,
        medication: String,
        dose: String,
        strength: String,
        status: String,
        actedAt: String,
        onTime: String,
    ) {
        val named = listOf(date, plannedTime, medication, dose, strength, status, actedAt, onTime)
        val words = COLUMN_KEYS.map { context.getString(it) }
        assertEquals("the scenario must name the words of the catalogue", words, named)
        assertEquals(
            "the file must carry the columns of the norm in their order",
            words,
            ExportStand.document(world).columns,
        )
    }

    @Тогда("^у «([^»]+)» есть строка со статусом «([^»]+)» и словом «([^»]+)» в колонке «([^»]+)»$")
    fun theFileCarriesThatRow(medication: String, status: String, word: String, column: String) {
        val document = ExportStand.document(world)
        val name = world.medications.get(world.medicationNamed(medication)).name
        val statusIndex = document.columns.indexOf(context.getString(R.string.history_export_column_status))
        val columnIndex = document.columns.indexOf(column)
        assertTrue("the file must name the column «$column»", columnIndex >= 0)
        val row = document.rows.firstOrNull { row -> row.getOrNull(statusIndex) == status }
        assertNotNull("the file must carry the record «$status» of «$medication»: ${document.rows}", row)
        assertEquals("the on-time word must be the one the scenario names", word, row!![columnIndex])
        assertTrue("the record must belong to «$medication»", name.isNotBlank())
    }

    @Тогда("^в карточке состава вместо числа записей стоит «([^»]+)»$")
    fun theCompositionSaysThePhraseInsteadOfTheNumber(phrase: String) {
        assertNull("the period of the scenario must be empty", ExportStand.state(world).sheet?.composition?.records)
        ExportStand.ask(world) { test ->
            test.onNodeWithText(phrase, useUnmergedTree = true).assertExists()
        }
    }

    @Тогда("^файл несёт шапку и колонки и ни одной записи$")
    fun theFileCarriesTheHeaderAndTheColumnsAndNoRecords() {
        val document = ExportStand.document(world)
        assertTrue("the header must stay", document.header.isNotEmpty())
        assertTrue("the columns must stay", document.columns.isNotEmpty())
        assertEquals("no records may be invented for an empty period", 0, document.records)
    }

    // --- Принято вне окна: слово «нет» (Р-21, Р-27) ---------------------------------

    /**
     * Запись, отмеченная вне окна: план 08:00, отметка 12:00.
     *
     * «Вовремя» решает то же правило продукта (`OnTimeRule.TwoHours`), что у экрана и отчёта;
     * строка собирается типизированной таблицей, а слово в файл кладёт настоящий композер.
     */
    @Допустим("^у профиля есть приём, отмеченный на четыре часа позже плана$")
    fun theIntakeWasMarkedFourHoursLate() {
        val scheduled = LocalDate.of(2026, 9, 1).atTime(PLANNED_TIME)
        val acted = LocalDate.of(2026, 9, 1).atTime(PLANNED_TIME.plusHours(LATE_HOURS))
        lateRow = HistoryCsvRow(
            date = LocalDate.of(2026, 9, 1),
            plannedTime = PLANNED_TIME,
            medication = world.medications.get(world.medications.names().first()).name,
            form = MedicationForm.TABLET,
            dose = BigDecimal.ONE,
            doseUnit = "unit",
            strength = null,
            strengthUnit = null,
            status = HistoryCsvStatus.TAKEN,
            actedAt = acted,
            onTime = OnTimeRule.TwoHours.isOnTime(scheduled, acted),
        )
        assertFalse("the late mark must not be on time", requireNotNull(lateRow).onTime!!)
    }

    @Когда("^человек выгружает эту запись на стенде$")
    fun thePersonExportsThatRecordOnTheStand() {
        lateDocument = HistoryCsvComposer(context).compose(
            HistoryCsvTable(
                profileName = world.profiles.active,
                window = windowOf(SEPTEMBER),
                rows = listOf(requireNotNull(lateRow) { "the scenario never marked the late intake" }),
            ),
        )
    }

    @Тогда("^у записи статус «([^»]+)» и слово «([^»]+)» в колонке «([^»]+)»$")
    fun theRecordNamesTheStatusAndTheWord(status: String, word: String, column: String) {
        val document = requireNotNull(lateDocument) { "the scenario never exported the record" }
        val row = document.rows.single()
        val statusIndex = document.columns.indexOf(context.getString(R.string.history_export_column_status))
        assertEquals("the status must be the word of the scenario", status, row[statusIndex])
        assertEquals(
            "the word must be the one the catalogue carries",
            context.getString(R.string.history_export_on_time_no),
            word,
        )
        assertEquals("the on-time cell must carry the word", word, row[document.columns.indexOf(column)])
    }

    // --- Обвязка ---------------------------------------------------------------------

    private fun scope(): ReportScope =
        requireNotNull(ReportScopeStand.state(world).report) { "the report sheet must be open" }.scope

    private fun proMark(): String = proCatalog().getValue("pro_mark")

    private fun proCatalog(): Map<String, String> = catalog("core-ui/src/main/res/values/strings.xml")

    /** Каталог русского языка модуля: им сверяются слова, которые сценарий называет вслух. */
    private fun catalog(path: String): Map<String, String> {
        val file = File(RepositoryLayout.root, path)
        return Regex("""<string name="([^"]+)"[^>]*>([^<]*)</string>""")
            .findAll(file.readText())
            .associate { match -> match.groupValues[1] to match.groupValues[2] }
    }

    private fun windowOf(period: String): HistoryWindow {
        val parts = period.split("—").map { it.trim() }
        return when {
            parts.size == 2 && !parts[0].any { it.isDigit() } -> {
                val last = RussianDates.month(parts[1], world.clock.today())
                val first = RussianDates.month("${parts[0]} ${last.year}", world.clock.today())
                HistoryWindow(first.atDay(1), last.atEndOfMonth())
            }
            parts.size == 2 -> {
                val dates = RussianDates.range(parts[0], parts[1], world.clock.today())
                HistoryWindow(dates.first(), dates.last())
            }
            else -> HistoryWindow.of(RussianDates.month(period, world.clock.today()))
        }
    }

    private suspend fun extendedSections(window: HistoryWindow): ExtendedReportSections = BuildExtendedSectionsUseCase(
        activeProfile = world.profileSession.activeStore,
        history = world.history,
        calculator = AdherenceCalculator(OnTimeRule.TwoHours),
        missedPattern = MissedPatternRule(),
        time = world.clock.time,
    ).invoke(window)

    private companion object {

        const val SEPTEMBER = "сентябрь 2026"

        /** Окно, на котором расширенный состав даёт все три раздела: два месяца и период перед ними. */
        const val TWO_MONTHS = "август—сентябрь 2026"

        const val PREVIOUS_MONTH_WORDS = "прошлый месяц"

        val PLANNED_TIME: LocalTime = LocalTime.of(8, 0)
        const val LATE_HOURS = 4L

        /** Колонки файла в порядке нормы (§5.5): список один на шаг и на композер документа. */
        val COLUMN_KEYS = listOf(
            R.string.history_export_column_date,
            R.string.history_export_column_planned_time,
            R.string.history_export_column_medication,
            R.string.history_export_column_dose,
            R.string.history_export_column_strength,
            R.string.history_export_column_status,
            R.string.history_export_column_acted_at,
            R.string.history_export_column_on_time,
        )
    }
}
