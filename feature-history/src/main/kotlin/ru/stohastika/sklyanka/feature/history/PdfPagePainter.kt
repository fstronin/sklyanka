package ru.stohastika.sklyanka.feature.history

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import ru.stohastika.sklyanka.ui.token.SklyankaColors
import ru.stohastika.sklyanka.ui.token.lightColors
import java.io.ByteArrayOutputStream
import ru.stohastika.sklyanka.ui.R as UiR

/**
 * Метрики настоящего текста: `Paint` с шрифтами приложения.
 *
 * Шрифты берутся из `res/font` (`core-ui`), а не из сети и не из системных: у продукта
 * нет сети вовсе (НФТ-14, НФТ-15), а pdf обязан набирать ту же кириллицу, что экран.
 * `Resources.getFont` доступен с API 26 — минимума продукта, и лишней зависимости
 * ради одного вызова не заводится.
 *
 * Цвета — роли светлой темы: профиль документ не перекрашивает (решение дизайна),
 * а хардкода цвета в файле нет (НФТ-47).
 */
internal class AndroidPdfTextMetrics(
    private val context: Context,
    private val colors: SklyankaColors = lightColors(),
) : PdfTextMetrics {

    private val paints = mutableMapOf<ReportPdfRole, Paint>()

    fun paint(role: ReportPdfRole): Paint = paints.getOrPut(role) { createPaint(role) }

    override fun lineHeight(role: ReportPdfRole): Float =
        if (role.isRule) RULE_HEIGHT else paint(role).fontSpacing * LEADING

    override fun measure(text: String, role: ReportPdfRole): Float =
        if (text.isEmpty()) 0f else paint(role).measureText(text)

    override fun wrap(text: String, role: ReportPdfRole, width: Float): List<String> {
        if (text.isEmpty()) return emptyList()
        val paint = paint(role)
        val lines = mutableListOf<String>()
        var current = ""
        text.split(' ').forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && paint.measureText(candidate) > width) {
                lines += current
                current = word
            } else {
                current = candidate
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private fun createPaint(role: ReportPdfRole): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (role.isRule) {
            color = if (role == ReportPdfRole.HEADER_RULE) colors.accent.toArgb() else colors.line.toArgb()
            strokeWidth = RULE_STROKE
            style = Paint.Style.STROKE
        } else {
            typeface = typefaceFor(role)
            textSize = sizeFor(role)
            color = colorFor(role)
            strokeWidth = 0f
        }
    }

    /**
     * Пары «шрифт — место»: Literata 600 — заголовки и числа (§2.3), Golos Text —
     * весь остальной текст. Обратного смешения нет и здесь.
     */
    private fun typefaceFor(role: ReportPdfRole): Typeface = when (role) {
        ReportPdfRole.TITLE, ReportPdfRole.SUMMARY_VALUE -> literata
        ReportPdfRole.SUBTITLE, ReportPdfRole.SECTION -> golosSemibold
        else -> golosRegular
    }

    private fun sizeFor(role: ReportPdfRole): Float = when (role) {
        ReportPdfRole.TITLE -> TITLE_SIZE
        ReportPdfRole.SUMMARY_VALUE, ReportPdfRole.SUBTITLE -> SUBTITLE_SIZE
        ReportPdfRole.SECTION -> SECTION_SIZE
        ReportPdfRole.FOOTER -> FOOTER_SIZE
        else -> BODY_SIZE
    }

    private fun colorFor(role: ReportPdfRole): Int = when (role) {
        ReportPdfRole.META, ReportPdfRole.BODY_MUTED, ReportPdfRole.SECTION, ReportPdfRole.FOOTER ->
            colors.textMuted.toArgb()
        else -> colors.text.toArgb()
    }

    private val literata: Typeface by lazy { context.resources.getFont(UiR.font.literata_semibold) }
    private val golosSemibold: Typeface by lazy { context.resources.getFont(UiR.font.golos_text_semibold) }
    private val golosRegular: Typeface by lazy { context.resources.getFont(UiR.font.golos_text_regular) }

    private companion object {
        const val LEADING = 1.35f
        const val RULE_HEIGHT = 1f
        const val RULE_STROKE = 0.7f
        const val TITLE_SIZE = 20f
        const val SUBTITLE_SIZE = 12f
        const val SECTION_SIZE = 11f
        const val BODY_SIZE = 10f
        const val FOOTER_SIZE = 8f
    }
}

/**
 * Рисование страниц платформенным `PdfDocument` (`TDD.md §5.5`).
 *
 * Живёт отдельно от раскладки: [PdfReportLayout] уже разложил строки, painter только
 * ставит их на полотно A4, потому что `PdfDocument` работает лишь на настоящей
 * платформе и на JVM не проверяется ничем (см. `TDD.md §12`). Шрифты встраиваются
 * в файл самим `PdfDocument`: Skia сохраняет подмножества использованных глифов.
 */
internal class PdfPagePainter(
    private val metrics: AndroidPdfTextMetrics,
    private val geometry: PdfPageGeometry = PdfPageGeometry(),
    private val colors: SklyankaColors = lightColors(),
) {

    fun paint(pages: List<PdfReportPage>): ByteArray {
        val document = PdfDocument()
        try {
            pages.forEach { page ->
                val size = geometry.width.toInt() to geometry.height.toInt()
                val info = PdfDocument.PageInfo.Builder(size.first, size.second, page.pageNumber).create()
                val pdfPage = document.startPage(info)
                drawPage(pdfPage.canvas, page)
                document.finishPage(pdfPage)
            }
            return ByteArrayOutputStream().use { output ->
                document.writeTo(output)
                output.toByteArray()
            }
        } finally {
            document.close()
        }
    }

    private fun drawPage(canvas: Canvas, page: PdfReportPage) {
        // Белый фон — решение дизайна: документ печатают и пересылают, бумага белая.
        canvas.drawColor(colors.surface.toArgb())
        var y = geometry.marginTop
        page.body.forEach { line ->
            y += line.gapBefore
            val paint = metrics.paint(line.role)
            if (line.role.isRule) {
                canvas.drawLine(geometry.marginSide, y, geometry.width - geometry.marginSide, y, paint)
            } else {
                val baseline = y - paint.fontMetrics.ascent
                canvas.drawText(line.text, geometry.marginSide, baseline, paint)
                line.value?.let { value ->
                    val labelWidth = paint.measureText(line.text)
                    canvas.drawText(
                        value,
                        geometry.marginSide + labelWidth + VALUE_GAP,
                        baseline,
                        metrics.paint(ReportPdfRole.SUMMARY_VALUE),
                    )
                }
                line.right?.let { right ->
                    val rightPaint = metrics.paint(ReportPdfRole.BODY)
                    val rightWidth = rightPaint.measureText(right)
                    canvas.drawText(right, geometry.width - geometry.marginSide - rightWidth, baseline, rightPaint)
                }
            }
            y += metrics.lineHeight(line.role)
        }
        drawFooter(canvas, page)
    }

    /** Колонтитул каждой страницы: оговорка слева, «Склянка · стр. N» справа. */
    private fun drawFooter(canvas: Canvas, page: PdfReportPage) {
        val paint = metrics.paint(ReportPdfRole.FOOTER)
        val baseline = geometry.height - geometry.marginBottom
        canvas.drawText(page.disclaimer, geometry.marginSide, baseline, paint)
        val brandWidth = paint.measureText(page.brand)
        canvas.drawText(page.brand, geometry.width - geometry.marginSide - brandWidth, baseline, paint)
    }

    private companion object {
        const val VALUE_GAP = 4f
    }
}
