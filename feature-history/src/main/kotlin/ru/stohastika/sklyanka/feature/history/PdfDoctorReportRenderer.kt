package ru.stohastika.sklyanka.feature.history

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ru.stohastika.sklyanka.domain.history.DoctorReport
import ru.stohastika.sklyanka.domain.history.ExtendedReportSections
import ru.stohastika.sklyanka.domain.history.ReportDocument
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Отрисовка отчёта врачу в PDF (`TDD.md §5.5`).
 *
 * Формат — решение дизайна по ОВ-15: A4 книжной ориентации, локально, без сети.
 * Рендер платформенный (`android.graphics.pdf.PdfDocument`): он встраивает подмножества
 * TrueType-шрифтов и не тянет ни одной зависимости — сторонняя библиотека PDF была бы
 * третьим источником риска (вес, лицензия, загрузка шрифтов) без выигрыша продукту.
 *
 * Состав и раскладка отделены от рисования: [ReportPdfComposer] и [PdfReportLayout]
 * проверяются на JVM, painter остаётся тонким.
 *
 * **Выводов и рекомендаций в документе нет** — по той же причине, что и в строке-выводе
 * экрана: приложение не медицинское изделие (`CLAUDE.md`). Ограничение проверяется
 * тестом состава, а не памятью ревьюера.
 */
@Singleton
class PdfDoctorReportRenderer @Inject constructor(@ApplicationContext private val context: Context) :
    DoctorReportRenderer {

    private val composer = ReportPdfComposer(context)

    override fun render(report: DoctorReport, zone: ZoneId, extended: ExtendedReportSections): ReportDocument {
        val content = composer.compose(report, zone, extended)
        val metrics = AndroidPdfTextMetrics(context)
        val geometry = PdfPageGeometry()
        val pages = PdfReportLayout(metrics, geometry).paginate(content)
        val bytes = PdfPagePainter(metrics, geometry).paint(pages)
        return ReportDocument(fileName = content.fileName, mediaType = content.mediaType, bytes = bytes)
    }

    override fun composition(report: DoctorReport): ReportComposition = composer.composition(report)
}
