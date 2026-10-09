package ru.stohastika.sklyanka.feature.history

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.history.AdherenceCalculator
import ru.stohastika.sklyanka.domain.history.DoctorReport
import ru.stohastika.sklyanka.domain.history.DoctorReportBuilder
import ru.stohastika.sklyanka.domain.history.HistorySlice
import ru.stohastika.sklyanka.domain.history.HistoryWindow
import ru.stohastika.sklyanka.domain.history.OnTimeRule
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Байты отчёта врачу — на устройстве (`SKL-41/TDD.md` §5).
 *
 * `PdfDocument` работает только на платформе (`SKL-33/TDD.md` §12), поэтому на JVM
 * проверяются состав и раскладка, а настоящий файл — здесь: сигнатура `%PDF`, признак
 * конца `%%EOF`, число листов, встроенные шрифты и нарисованный колонтитул каждой
 * страницы. Отчёт рисуется тем же [PdfDoctorReportRenderer], что и в продукте.
 *
 * Месяц фикстуры многосоставный: 12 лекарств с пропусками не помещаются на один лист,
 * и документ обязан стать двухстраничным — иначе проверять «больше одной страницы» не на чем.
 */
@RunWith(AndroidJUnit4::class)
class PdfDoctorReportRendererDeviceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val renderer = PdfDoctorReportRenderer(context)

    @Test
    fun theReportStartsWithThePdfSignature() {
        val bytes = reportBytes()

        assertEquals(
            "the file must open with the PDF signature (SKL-41)",
            PDF_SIGNATURE,
            String(bytes, 0, PDF_SIGNATURE.length, Charsets.ISO_8859_1),
        )
    }

    @Test
    fun theReportEndsWithTheEndOfFileMarker() {
        val tail = reportText().trimEnd()

        assertTrue(
            "the file must close with the end of file marker, tail=${tail.takeLast(TAIL_PREVIEW)}",
            tail.endsWith(PDF_END_OF_FILE),
        )
    }

    @Test
    fun aManyPartMonthOpensAsAPdfWithMoreThanOneA4Page() {
        withReportFile { file ->
            val pageCount = rendererPageCount(file)
            assertTrue(
                "the multi-part month must need more than one sheet, got $pageCount (SKL-41)",
                pageCount > 1,
            )
            assertEveryPageIsA4(file)
        }
    }

    @Test
    fun theTypefacesAreEmbeddedAsTrueTypePrograms() {
        val text = reportText()
        val programs = Regex(FONT_FILE_PATTERN).findAll(text).count()

        assertTrue("the file must embed its typefaces, found $programs /FontFile2", programs > 0)
        EMBEDDED_FAMILIES.forEach { family ->
            assertTrue(
                "$family must be embedded, not substituted by a system font: /BaseFont is not named",
                text.contains(family),
            )
        }
    }

    @Test
    fun everyPageCarriesTheDisclaimerInItsFooter() {
        val pages = layoutPages()
        val disclaimer = context.getString(R.string.report_document_disclaimer)

        withReportFile { file ->
            assertEquals(
                "the painted file must hold every page of the layout",
                pages.size,
                rendererPageCount(file),
            )
            pages.forEach { page ->
                assertEquals("page ${page.pageNumber} must carry the disclaimer", disclaimer, page.disclaimer)
            }
            footerInk(file).forEachIndexed { index, ink ->
                assertTrue(
                    "page ${index + 1} must have its footer painted, ink pixels in the band=$ink",
                    ink > 0,
                )
            }
        }
    }

    // --- Фикстура --------------------------------------------------------------

    private fun reportBytes(): ByteArray = renderer.render(report(), ZONE).bytes

    private fun reportText(): String = String(reportBytes(), Charsets.ISO_8859_1)

    private fun report(): DoctorReport {
        val slice = slice()
        return DoctorReportBuilder.build(
            period = AdherenceCalculator(OnTimeRule.TwoHours).of(slice, WINDOW, TODAY, ZONE),
            slice = slice,
            profileName = PROFILE_NAME,
            generatedAt = GENERATED_AT,
            zone = ZONE,
        )
    }

    /** 12 лекарств с приёмом каждый день месяца: блоки не помещаются на один лист. */
    private fun slice(): HistorySlice {
        val medications = (1..MEDICATIONS).map { index -> medication("med-$index") }
        val intakes = medications.flatMap { medication ->
            (1..DAYS_WITH_INTAKES).map { day ->
                val date = LocalDate.of(2026, 9, day)
                val status = when {
                    day % TAKEN_EVERY == 0 -> IntakeStatus.TAKEN
                    day % SKIPPED_EVERY == 0 -> IntakeStatus.SKIPPED
                    else -> IntakeStatus.MISSED
                }
                Intake(
                    id = "intake-${medication.id}-$day",
                    medicationId = medication.id,
                    scheduledAt = date.atTime(MORNING),
                    status = status,
                    actedAt = date.atTime(MORNING).atZone(ZONE).toInstant(),
                    source = if (status == IntakeStatus.MISSED) IntakeSource.AUTO else IntakeSource.PHONE,
                    actorProfileId = null,
                )
            }
        }
        return HistorySlice(PROFILE_ID, intakes, medications, LocalDate.of(2026, 9, 1))
    }

    private fun medication(id: String) = Medication(
        id = id,
        profileId = PROFILE_ID,
        name = id,
        form = MedicationForm.TABLET,
        shape = null,
        doseAmount = BigDecimal.ONE,
        doseUnit = "tablet",
        strength = BigDecimal("50"),
        strengthUnit = "mg",
        foodRelation = FoodRelation.ANY,
        scheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay = null,
        times = listOf(MORNING),
        startDate = LocalDate.of(2026, 9, 1),
        endDate = LocalDate.of(2026, 10, 1),
        stockLeft = null,
        stockThresholdDays = null,
        archivedAt = null,
        archiveReason = null,
    )

    // --- Чтение настоящего файла ------------------------------------------------

    private fun layoutPages(): List<PdfReportPage> {
        val content = ReportPdfComposer(context).compose(report(), ZONE)
        return PdfReportLayout(AndroidPdfTextMetrics(context), PdfPageGeometry()).paginate(content)
    }

    /** Байты на диске: `PdfRenderer` читает файл, а не память. */
    private fun withReportFile(block: (File) -> Unit) {
        val file = File(context.cacheDir, REPORT_FILE_NAME)
        try {
            file.writeBytes(reportBytes())
            block(file)
        } finally {
            file.delete()
        }
    }

    private fun rendererPageCount(file: File): Int = withRenderer(file) { it.pageCount }

    private fun assertEveryPageIsA4(file: File) = withRenderer(file) { pdf ->
        for (index in 0 until pdf.pageCount) {
            pdf.openPage(index).use { page ->
                assertEquals("page ${index + 1} must be A4 wide", A4_WIDTH, page.width)
                assertEquals("page ${index + 1} must be A4 tall", A4_HEIGHT, page.height)
            }
        }
    }

    /**
     * Краска в левой полосе колонтитула каждой страницы.
     *
     * Текста оговорки в байтах нет — потоки сжаты, текст набран глифами CID-шрифта;
     * поэтому проверяется, что painter нарисовал колонтитул на каждом листе, а не только
     * на первом. Правая часть полосы не читается: там подпись «Склянка · стр. N»,
     * и её краска оговорку не подтверждает.
     */
    private fun footerInk(file: File): List<Int> = withRenderer(file) { pdf ->
        (0 until pdf.pageCount).map { index ->
            pdf.openPage(index).use { page ->
                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val ink = countInk(bitmap)
                bitmap.recycle()
                ink
            }
        }
    }

    private fun countInk(bitmap: Bitmap): Int {
        var ink = 0
        for (y in FOOTER_TOP until FOOTER_BOTTOM) {
            for (x in FOOTER_LEFT until FOOTER_RIGHT) {
                if (bitmap.getPixel(x, y) != Color.WHITE) ink++
            }
        }
        return ink
    }

    private fun <T> withRenderer(file: File, block: (PdfRenderer) -> T): T =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use(block)
        }

    private companion object {
        const val PROFILE_ID = "11111111-1111-4111-8111-111111111111"
        const val PROFILE_NAME = "profile-a"
        const val REPORT_FILE_NAME = "device-report.pdf"
        const val MEDICATIONS = 12
        const val DAYS_WITH_INTAKES = 28
        const val TAKEN_EVERY = 7
        const val SKIPPED_EVERY = 3

        const val PDF_SIGNATURE = "%PDF"
        const val PDF_END_OF_FILE = "%%EOF"
        const val FONT_FILE_PATTERN = "/FontFile2"
        const val TAIL_PREVIEW = 40

        /** A4 книжной ориентации в точках: 210×297 мм при 72 pt на дюйм. */
        const val A4_WIDTH = 595
        const val A4_HEIGHT = 842

        /**
         * Полоса колонтитула — из той же геометрии, что у painter'а
         * (`PdfPageGeometry.marginBottom = 45`, baseline = высота − 45 = 797).
         */
        const val FOOTER_TOP = 777
        const val FOOTER_BOTTOM = 803
        const val FOOTER_LEFT = 51
        const val FOOTER_RIGHT = 400

        val EMBEDDED_FAMILIES = listOf("Literata", "GolosText")

        val ZONE: ZoneId = ZoneId.of("Europe/Moscow")
        val TODAY: LocalDate = LocalDate.of(2026, 9, 30)
        val MORNING: LocalTime = LocalTime.of(8, 0)
        val WINDOW: HistoryWindow = HistoryWindow.of(YearMonth.of(2026, 9))
        val GENERATED_AT: Instant = LocalDate.of(2026, 9, 30).atTime(10, 0).atZone(ZONE).toInstant()
    }
}
