package ru.stohastika.sklyanka.feature.history

import ru.stohastika.sklyanka.domain.history.DoctorReport
import ru.stohastika.sklyanka.domain.history.ExtendedReportSections
import ru.stohastika.sklyanka.domain.history.ReportDocument
import java.time.ZoneId

/**
 * Отрисовка отчёта врачу.
 *
 * Интерфейс, а не класс, по двум причинам. Первая: формат артефакта подтверждён
 * дизайном (ОВ-15) и подстановка формата обязана быть заменой реализации, а не правкой
 * экрана. Вторая: состояние экрана и лист отчёта проверяются на JVM без настоящего PDF —
 * `PdfDocument` живёт только на платформе (`TDD.md §12`).
 */
interface DoctorReportRenderer {

    /**
     * Собрать документ.
     *
     * @param extended разделы «Про» расширенного состава; по умолчанию их нет — документ
     *   собирается базовым ровно так, как собирался до появления второго состава (Р-36).
     */
    fun render(
        report: DoctorReport,
        zone: ZoneId,
        extended: ExtendedReportSections = ExtendedReportSections.NONE,
    ): ReportDocument

    /**
     * Состав документа для шторки: профиль, период, число принятых приёмов и формат.
     *
     * Тем же композером, что и PDF: двух версий одного содержания в продукте не остаётся,
     * а человек видит, что именно отдаёт, до нажатия «Передать». Часовой пояс составу
     * не нужен: в нём нет момента формирования.
     */
    fun composition(report: DoctorReport): ReportComposition
}
