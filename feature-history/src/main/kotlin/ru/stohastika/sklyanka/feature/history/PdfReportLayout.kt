package ru.stohastika.sklyanka.feature.history

/**
 * Метрики текста для вёрстки документа.
 *
 * Интерфейс, а не `Paint`: раскладка страниц обязана проверяться на JVM без устройства,
 * а настоящий `Paint` подставляется на Android. Один интерфейс меряет и раскладывает,
 * и рисует: если разметка и painter разойдутся в метриках, блок, поместившийся на
 * странице в проверке, обрежется при печати.
 */
internal interface PdfTextMetrics {

    fun lineHeight(role: ReportPdfRole): Float

    /** Разбивает текст на строки, помещающиеся в [width]; пустой текст — пустой список. */
    fun wrap(text: String, role: ReportPdfRole, width: Float): List<String>
}

/** Физическая строка страницы: после переноса, готовая и к рисованию, и к проверке. */
internal data class PdfPhysicalLine(
    val text: String,
    val role: ReportPdfRole,
    /** Правый столбец строки лекарства — прижат к правому полю. */
    val right: String? = null,
    /** Число сводки: набирается Literata 600 сразу за подписью [text]. */
    val value: String? = null,
    /** Отступ сверху: часть раскладки, поэтому хранится в строке, а не в painter'е. */
    val gapBefore: Float = 0f,
)

/**
 * Готовая страница документа.
 *
 * Оговорка и подпись страницы — поля каждой страницы, а не строка в конце документа:
 * «Не является медицинским документом» обязана стоять на каждом листе, который врач
 * может распечатать или переслать отдельно (решение дизайна).
 */
internal data class PdfReportPage(
    val body: List<PdfPhysicalLine>,
    val pageNumber: Int,
    val disclaimer: String,
    val brand: String,
)

/**
 * Раскладка состава по страницам A4 без Android (`TDD.md §5.4`).
 *
 * Правила решения дизайна: шапка — только на первой странице; колонтитул — на каждой;
 * блок одного лекарства не разрывается между страницами. Блок выше целой страницы
 * (лекарство с очень длинным перечнем дат) разбивается между своими строками: отказать
 * документу в сборке хуже, чем разорвать один блок, и это исключение, а не правило.
 */
internal class PdfReportLayout(
    private val metrics: PdfTextMetrics,
    private val geometry: PdfPageGeometry = PdfPageGeometry(),
) {

    fun paginate(content: ReportPdfContent): List<PdfReportPage> = Pager(content).paginate()

    /**
     * Состояние одной раскладки.
     *
     * Отдельный класс, а не локальные функции: страницы — это состояние (какая
     * заполняется, сколько места занято), и держать его в замыканиях значит прятать
     * от читателя самое важное.
     */
    private inner class Pager(private val content: ReportPdfContent) {

        private val width: Float = geometry.width - geometry.marginSide * 2
        private val bottom: Float = geometry.height - geometry.marginBottom - geometry.footerGap -
            metrics.lineHeight(ReportPdfRole.FOOTER)
        private val sheets = mutableListOf<Sheet>()
        private var sheet = Sheet()

        fun paginate(): List<PdfReportPage> {
            place(content.header.flatMap { physicalLine(it, width) })
            place(listOf(rule(ReportPdfRole.HEADER_RULE)), gapBefore = RULE_GAP)
            // Пустой период: сводки нет вместе с заголовком, вместо неё — emptyNote
            // (решение дизайна, ревизия 33-B SKL-33).
            content.summaryTitle?.let { title ->
                place(listOf(sectionLine(title)), gapBefore = SECTION_GAP)
                content.summary.forEach { line -> place(listOf(summaryLine(line)), gapBefore = SUMMARY_GAP) }
                place(listOf(rule(ReportPdfRole.DIVIDER)), gapBefore = RULE_GAP)
            }
            content.medicationsTitle?.let { title -> place(listOf(sectionLine(title)), gapBefore = SECTION_GAP) }
            content.medicationBlocks.forEach { block -> placeBlock(block) }
            content.emptyNote?.let { note -> place(physicalLine(note, width), gapBefore = SECTION_GAP) }
            flush()
            return sheets.mapIndexed { index, page ->
                PdfReportPage(
                    body = page.body,
                    pageNumber = index + 1,
                    disclaimer = content.disclaimer,
                    brand = content.brandPage(index + 1),
                )
            }
        }

        private fun place(lines: List<PdfPhysicalLine>, gapBefore: Float = 0f) {
            lines.forEachIndexed { index, line ->
                val placed = if (index == 0 && gapBefore > 0f) line.copy(gapBefore = gapBefore) else line
                val height = heightOf(placed) + placed.gapBefore
                if (!fits(height) && sheet.body.isNotEmpty()) flush()
                sheet.body += placed
                sheet.used += height
            }
        }

        private fun placeBlock(block: ReportPdfBlock) {
            val lines = physical(block)
            val height = lines.fold(0f) { sum, line -> sum + heightOf(line) } + BLOCK_GAP
            if (!fits(height) && sheet.body.isNotEmpty()) flush()
            place(lines, gapBefore = BLOCK_GAP)
        }

        private fun fits(height: Float): Boolean = sheet.used + height <= bottom

        private fun flush() {
            sheets += sheet
            sheet = Sheet()
        }

        private fun physical(block: ReportPdfBlock): List<PdfPhysicalLine> = block.lines.flatMapIndexed { index, line ->
            val right = block.right.takeIf { index == 0 }
            metrics.wrap(line.text, line.role, width).mapIndexed { wrapped, text ->
                PdfPhysicalLine(text = text, role = line.role, right = right.takeIf { wrapped == 0 })
            }
        }

        private fun physicalLine(line: ReportPdfLine, wrapWidth: Float): List<PdfPhysicalLine> =
            metrics.wrap(line.text, line.role, wrapWidth).map { text ->
                PdfPhysicalLine(text = text, role = line.role, value = line.value)
            }

        private fun summaryLine(line: ReportPdfSummaryLine): PdfPhysicalLine =
            PdfPhysicalLine(text = line.label, role = ReportPdfRole.BODY, value = line.value)

        private fun sectionLine(line: ReportPdfLine): PdfPhysicalLine =
            PdfPhysicalLine(text = line.text, role = line.role)

        private fun rule(role: ReportPdfRole): PdfPhysicalLine = PdfPhysicalLine(text = "", role = role)

        /** Высота строки: у строки сводки её задаёт число — оно набрано крупнее подписи. */
        private fun heightOf(line: PdfPhysicalLine): Float = if (line.value == null) {
            metrics.lineHeight(line.role)
        } else {
            maxOf(metrics.lineHeight(line.role), metrics.lineHeight(ReportPdfRole.SUMMARY_VALUE))
        }
    }

    private class Sheet {
        val body = mutableListOf<PdfPhysicalLine>()
        var used: Float = 0f
    }

    private companion object {
        const val SECTION_GAP = 14f
        const val RULE_GAP = 12f
        const val BLOCK_GAP = 10f
        const val SUMMARY_GAP = 3f
    }
}
