package ru.stohastika.sklyanka.feature.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Раскладка документа по страницам — на JVM, без Android (`TDD.md §5.4`).
 *
 * Здесь проверяются обязательные требования решения дизайна, которые иначе нечем
 * проверить на JVM: оговорка на каждой странице, шапка только на первой, блок
 * лекарства не разрывается. Настоящий PDF рисуется `PdfDocument` и проверяется
 * только на устройстве.
 */
class PdfReportLayoutTest {

    private val metrics = FixedMetrics()
    private val layout = PdfReportLayout(metrics, Page)

    @Test
    fun `the header is printed on the first page only`() {
        val pages = layout.paginate(content(blocks = 6))

        assertTrue(pages.size > 1)
        assertTrue("the title must open the document", pages.first().body.any { it.role == ReportPdfRole.TITLE })
        pages.drop(1).forEach { page ->
            assertFalse(
                "page ${page.pageNumber} must not repeat the header",
                page.body.any { it.role == ReportPdfRole.TITLE },
            )
        }
    }

    @Test
    fun `every page carries the disclaimer and its own number`() {
        val pages = layout.paginate(content(blocks = 6))

        assertTrue(pages.size > 1)
        pages.forEach { page ->
            assertEquals("page ${page.pageNumber} must carry the disclaimer", DISCLAIMER, page.disclaimer)
            assertTrue(
                "page ${page.pageNumber} must be numbered: ${page.brand}",
                page.brand.contains(page.pageNumber.toString()),
            )
        }
    }

    @Test
    fun `a medication block is not split between pages`() {
        val pages = layout.paginate(content(blocks = 8, blockLines = 2))

        assertTrue("the fixture must spill over one page", pages.size > 1)
        (1..8).forEach { index ->
            val holding = pages.filter { page -> page.body.any { it.text == "med-$index line-1" } }
            assertEquals("the block of med-$index must lie on exactly one page", 1, holding.size)
            assertTrue(
                "both lines of med-$index must stay together",
                holding.single().body.any { it.text == "med-$index line-2" },
            )
        }
    }

    @Test
    fun `a document without records says so on a single page and prints no block`() {
        // A4: пустой документ обязан поместиться на один лист — так его и увидят.
        val pages = PdfReportLayout(metrics).paginate(content(blocks = 0))

        assertEquals(1, pages.size)
        assertTrue(pages.single().body.any { it.text == EMPTY_NOTE })
        assertFalse(
            "no medication title may be printed without records",
            pages.single().body.any { it.text == MEDICATIONS_TITLE },
        )
        assertFalse(
            "no summary may be printed without records",
            pages.single().body.any { it.text == SUMMARY_TITLE },
        )
        assertFalse(
            "no divider may separate absent sections",
            pages.single().body.any { it.role == ReportPdfRole.DIVIDER },
        )
    }

    /**
     * Исключение, записанное вслух: блок выше целой страницы разбивается между своими
     * строками. Отказать документу в сборке из-за одного длинного перечня дат хуже.
     */
    @Test
    fun `a block taller than a page is broken between its lines without losing them`() {
        val content = content(blocks = 1, blockLines = 30)
        val pages = layout.paginate(content)

        assertTrue("the oversized block must spill over several pages", pages.size > 1)
        val printed = pages.flatMap { it.body }.map { it.text }
        (1..30).forEach { line ->
            assertTrue("line-$line of the block must be printed", printed.contains("med-1 line-$line"))
        }
    }

    /**
     * Правый столбец «N из M (P %)» резервируется единой колонкой (33-I): первая строка
     * блока переносится в остатке ширины, вторая занимает документ целиком. Без этого
     * текст первой строки наезжал бы на долю.
     */
    @Test
    fun `the first line of a block reserves the right column`() {
        val recording = RecordingMetrics()
        PdfReportLayout(recording, Page).paginate(content(blocks = 1, blockLines = 2))

        val firstWidth = recording.widthOf("med-1 line-1")
        val secondWidth = recording.widthOf("med-1 line-2")
        assertTrue("the first line must reserve the right column: $firstWidth", firstWidth < secondWidth)
    }

    private fun content(blocks: Int, blockLines: Int = 2): ReportPdfContent = ReportPdfContent(
        fileName = "sklyanka-report-2026-09.pdf",
        mediaType = "application/pdf",
        header = listOf(
            ReportPdfLine("Report on medication intake", ReportPdfRole.TITLE),
            ReportPdfLine("Sklyanka - profile", ReportPdfRole.SUBTITLE),
        ),
        summaryTitle = if (blocks == 0) null else ReportPdfLine(SUMMARY_TITLE, ReportPdfRole.SECTION),
        summary = if (blocks == 0) emptyList() else listOf(ReportPdfSummaryLine("Taken:", "12 of 15")),
        medicationsTitle = if (blocks == 0) null else ReportPdfLine(MEDICATIONS_TITLE, ReportPdfRole.SECTION),
        medicationBlocks = (1..blocks).map { index ->
            ReportPdfBlock(
                lines = (1..blockLines).map { line -> ReportPdfLine("med-$index line-$line", ReportPdfRole.BODY) },
                right = "$index of $index",
            )
        },
        emptyNote = if (blocks == 0) ReportPdfLine(EMPTY_NOTE, ReportPdfRole.BODY_MUTED) else null,
        disclaimer = DISCLAIMER,
        brandPage = { number -> "Sklyanka · page $number" },
    )

    /** Метрики фиксированы: раскладка проверяется арифметикой, а не шрифтом. */
    private class FixedMetrics : PdfTextMetrics {

        override fun lineHeight(role: ReportPdfRole): Float = if (role.isRule) RULE else LINE

        override fun measure(text: String, role: ReportPdfRole): Float = text.length * CHAR_WIDTH

        override fun wrap(text: String, role: ReportPdfRole, width: Float): List<String> =
            if (text.isEmpty()) emptyList() else listOf(text)
    }

    /** Запоминает ширину, с которой переносилась каждая строка: по ней видно резерв 33-I. */
    private class RecordingMetrics : PdfTextMetrics {

        private val widths = mutableMapOf<String, Float>()

        override fun lineHeight(role: ReportPdfRole): Float = if (role.isRule) RULE else LINE

        override fun measure(text: String, role: ReportPdfRole): Float = text.length * CHAR_WIDTH

        override fun wrap(text: String, role: ReportPdfRole, width: Float): List<String> {
            widths[text] = width
            return if (text.isEmpty()) emptyList() else listOf(text)
        }

        fun widthOf(text: String): Float = requireNotNull(widths[text]) { "no wrap recorded for $text" }
    }

    private companion object {
        /** Страница маленькая намеренно: переносы видны на нескольких блоках, а не на сотне. */
        val Page = PdfPageGeometry(
            width = 300f,
            height = 200f,
            marginSide = 20f,
            marginTop = 20f,
            marginBottom = 20f,
            footerGap = 10f,
        )
        const val LINE = 20f
        const val RULE = 1f
        const val CHAR_WIDTH = 10f
        const val DISCLAIMER = "Not a medical document"
        const val EMPTY_NOTE = "No records in the period"
        const val SUMMARY_TITLE = "SUMMARY"
        const val MEDICATIONS_TITLE = "BY MEDICATION"
    }
}
