package ru.stohastika.sklyanka.feature.history

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.stohastika.sklyanka.domain.history.AdherenceCalculator
import ru.stohastika.sklyanka.domain.history.DoctorReport
import ru.stohastika.sklyanka.domain.history.DoctorReportBuilder
import ru.stohastika.sklyanka.domain.history.DoctorReportTransfer
import ru.stohastika.sklyanka.domain.history.ExportHistoryCsvUseCase
import ru.stohastika.sklyanka.domain.history.ExtendedReportSections
import ru.stohastika.sklyanka.domain.history.HistoryCsvRow
import ru.stohastika.sklyanka.domain.history.HistoryCsvSaveOutcome
import ru.stohastika.sklyanka.domain.history.HistoryCsvStatus
import ru.stohastika.sklyanka.domain.history.HistoryCsvTable
import ru.stohastika.sklyanka.domain.history.HistorySlice
import ru.stohastika.sklyanka.domain.history.HistoryWindow
import ru.stohastika.sklyanka.domain.history.MissedPatternBlock
import ru.stohastika.sklyanka.domain.history.MonthAdherence
import ru.stohastika.sklyanka.domain.history.MonthDynamics
import ru.stohastika.sklyanka.domain.history.OnTimeRule
import ru.stohastika.sklyanka.domain.history.OnTimeShare
import ru.stohastika.sklyanka.domain.history.PeriodComparison
import ru.stohastika.sklyanka.domain.history.PeriodSummary
import ru.stohastika.sklyanka.domain.history.ReportDocument
import ru.stohastika.sklyanka.domain.history.ReportShareLauncher
import ru.stohastika.sklyanka.domain.history.ReportTarget
import ru.stohastika.sklyanka.domain.history.ReportTransferOutcome
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.repository.ActiveProfileRepository
import ru.stohastika.sklyanka.domain.transfer.ExportTarget
import ru.stohastika.sklyanka.domain.transfer.ImportSource
import ru.stohastika.sklyanka.domain.transfer.SafetyCopy
import ru.stohastika.sklyanka.domain.transfer.TransferFiles
import java.io.File
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

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

    /**
     * Расширенный состав на настоящем документе (SKL-119, §5.15).
     *
     * Проверяются три вещи, и ни одной из них на JVM не видно: страниц стало больше ровно
     * на разделы (первый «Про»-раздел начинает новую страницу), байты читаются как PDF той же
     * A4, и каждая страница действительно **нарисована** — растровые кадры ложатся файлами
     * рядом, чтобы визуальная сверка прочитала их глазами (чужих данных и пустого раздела
     * в кадрах быть не должно).
     *
     * Разделы заданы фикстурой, а не расчётом домена: предмет этой проверки — рисование
     * документа (`PdfDocument` живёт только на платформе), а расчёт разделов держат
     * `ExtendedReportSectionsTest` и сценарная приёмка. Доступ «Про» документ не знает вовсе:
     * состав выбирает поверхность (Р-22), и рендер получает то, что ей выдали.
     */
    @Test
    fun theExtendedReportAddsPagesAndEveryPageIsPainted() {
        val basePages = layoutPages()
        val extendedPages = layoutPages(sections = extendedSections())
        assertTrue(
            "the 'Pro' sections must add pages to the base document, base=${basePages.size}",
            extendedPages.size > basePages.size,
        )

        withReportFile { file ->
            file.writeBytes(renderer.render(report(), ZONE, extendedSections()).bytes)
            assertEquals(
                "the painted file must hold every page of the extended layout",
                extendedPages.size,
                rendererPageCount(file),
            )
            assertEveryPageIsA4(file)
            val baseFrames = exportFrames(file, FRAME_PREFIX_BASE, limit = basePages.size)
            val extendedFrames = exportFrames(file, FRAME_PREFIX_EXTENDED, limit = extendedPages.size)
            assertEquals("every base page must be laid out as a frame", basePages.size, baseFrames.size)
            assertEquals("every extended page must be exported as a frame", extendedPages.size, extendedFrames.size)
            extendedFrames.forEach { frame ->
                assertTrue("the frame must carry the painted page: ${frame.name}", frame.length() > 0)
            }
        }
    }

    /**
     * Файл выгрузки истории — на устройстве (SKL-119, Р-35).
     *
     * Имя, тип, шапка и колонки берутся у **продукта**: документ собирает настоящий
     * `HistoryCsvComposer`, записывает — настоящий `ExportHistoryCsvUseCase`, а читается файл
     * с диска: имя `sklyanka-history-ГГГГ-ММ-ДД.csv`, тип `text/csv`, четыре строки шапки
     * и восемь колонок нормы (§5.5). Литералы шага здесь подтвердили бы себя, а не файл.
     */
    @Test
    fun theExportedHistoryFileCarriesTheHeaderAndTheColumns() {
        val window = WINDOW
        val table = HistoryCsvTable(
            profileName = PROFILE_NAME,
            window = window,
            rows = listOf(
                HistoryCsvRow(
                    date = LocalDate.of(2026, 9, 1),
                    plannedTime = MORNING,
                    medication = "med-1",
                    form = MedicationForm.TABLET,
                    dose = BigDecimal.ONE,
                    doseUnit = "tablet",
                    strength = BigDecimal("50"),
                    strengthUnit = "mg",
                    status = HistoryCsvStatus.TAKEN,
                    actedAt = LocalDate.of(2026, 9, 1).atTime(MORNING),
                    onTime = true,
                ),
            ),
        )
        val document = ru.stohastika.sklyanka.feature.history.HistoryCsvComposer(context).compose(table)
        assertEquals(
            "the file must keep its own name (Р-35)",
            "sklyanka-history-${window.from.format(DateTimeFormatter.ISO_LOCAL_DATE)}.csv",
            document.fileName,
        )
        assertEquals("the file must be a table", "text/csv", document.mediaType)
        assertEquals("the header must name the profile, the period and both notes", 4, document.header.size)
        assertEquals("the file must carry the columns of the norm (§5.5)", 8, document.columns.size)

        val files = FilesInCache(context)
        val written = runBlocking {
            ExportHistoryCsvUseCase(
                files = files,
                transfer = NoShare,
                activeProfile = ActiveProfile,
                log = SilentLog,
            ).invoke(document, ExportTarget(document.fileName))
        }
        assertTrue("the export must be saved: $written", written is HistoryCsvSaveOutcome.Saved)

        val file = files.writtenFile()
        assertNotNull("the file must be on the disk: ${file?.path}", file)
        val lines = requireNotNull(file).readLines()
        assertEquals("the name of the file is the name of the document", document.fileName, file.name)
        // Строки разбираются с учётом кавычек: продукт экранирует значение с запятой
        // (колонка «Дозировка препарата, если задана»), и сравнение сырых слов считало бы
        // верное поведение ошибкой. Разбор проверяет и экранирование, и порядок колонок.
        assertEquals(
            "the file must open with the header of the document",
            document.header,
            lines.take(document.header.size).map { line -> parseCsvLine(line).single() },
        )
        val columns = parseCsvLine(lines[document.header.size])
        assertEquals("the columns must follow the header", document.columns, columns)
        assertEquals(
            "the record must follow the columns",
            document.rows.single(),
            parseCsvLine(lines[document.header.size + 1]),
        )
    }

    /**
     * Разбор строки CSV — тем же правилом, что у построителя.
     *
     * Значение с запятой или кавычкой стоит в кавычках, кавычка внутри удваивается: разбор
     * обязан вернуть ровно то значение, которое положил продукт, — иначе проверка сравнивала бы
     * экранированный текст с сырым словом (находка прогона на эмуляторе, API 35).
     */
    private fun parseCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == QUOTE && quoted && line.getOrNull(index + 1) == QUOTE -> {
                    cell.append(QUOTE)
                    index++
                }
                char == QUOTE -> quoted = !quoted
                char == SEPARATOR && !quoted -> {
                    cells += cell.toString()
                    cell.clear()
                }
                else -> cell.append(char)
            }
            index++
        }
        cells += cell.toString()
        return cells
    }

    // --- Фикстура --------------------------------------------------------------

    private fun reportBytes(): ByteArray = renderer.render(report(), ZONE, ExtendedReportSections.NONE).bytes

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

    /**
     * 12 лекарств с двумя приёмами в сутки за два месяца: блоки не помещаются на один лист,
     * а у раздела динамики есть не меньше двух месяцев с наступившими приёмами (§5.15).
     *
     * Два времени приёма обязательны правилу паттерна (без них оно молчит), а пропуски
     * сосредоточены на утреннем времени так, чтобы **лидер проходил пороги**: восемь утренних
     * пропусков против четырёх вечерних — это и минимум в четыре пропуска, и доля 0,67 при
     * пороге 0,6, и два времени в периоде, и единственный лидер (Р-85 п. 4). Каждый утренний
     * пропуск — один приём на своей дате, поэтому перечень дат раздела и число пропусков
     * называют одно и то же, а не спорят друг с другом.
     */
    private fun slice(): HistorySlice {
        val medications = (1..MEDICATIONS).map { index -> medication("med-$index") }
        val dates = MONTHS.flatMap { month -> (1..DAYS_WITH_INTAKES).map(month::atDay) }
        val morningMisses = dates.filterIndexed { index, _ -> index % GAP_TO_MISS == 0 }.take(PATTERN_MISSES)
        val eveningMisses = dates.filterIndexed { index, _ -> index % GAP_TO_EVENING_MISS == 0 }.take(EVENING_MISSES)
        val intakes = medications.flatMapIndexed { index, medication ->
            dates.flatMap { date ->
                TIMES.map { time ->
                    val missed = when (time) {
                        MORNING -> date in morningMisses && index == MORNING_MISS_OWNER
                        else -> date in eveningMisses && index == EVENING_MISS_OWNER
                    }
                    Intake(
                        id = "intake-${medication.id}-$date-$time",
                        medicationId = medication.id,
                        scheduledAt = date.atTime(time),
                        status = if (missed) IntakeStatus.MISSED else IntakeStatus.TAKEN,
                        actedAt = date.atTime(time).atZone(ZONE).toInstant(),
                        source = if (missed) IntakeSource.AUTO else IntakeSource.PHONE,
                        actorProfileId = null,
                    )
                }
            }
        }
        return HistorySlice(PROFILE_ID, intakes, medications, dates.first())
    }

    /** Приверженность одного месяца фикстуры: числа динамики берутся из её же приёмов. */
    private fun monthAdherence(month: YearMonth): MonthAdherence {
        val ofMonth = slice().intakes.filter { YearMonth.from(it.scheduledAt) == month }
        return MonthAdherence(
            month = month,
            taken = ofMonth.count { it.status == IntakeStatus.TAKEN },
            planned = ofMonth.size,
        )
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
        times = TIMES,
        startDate = LocalDate.of(2026, 9, 1),
        endDate = LocalDate.of(2026, 10, 1),
        stockLeft = null,
        stockThresholdDays = null,
        archivedAt = null,
        archiveReason = null,
    )

    // --- Чтение настоящего файла ------------------------------------------------

    private fun layoutPages(sections: ExtendedReportSections = ExtendedReportSections.NONE): List<PdfReportPage> {
        val content = ReportPdfComposer(context).compose(report(), ZONE, sections)
        return PdfReportLayout(AndroidPdfTextMetrics(context), PdfPageGeometry()).paginate(content)
    }

    /**
     * Разделы «Про» фикстуры: сравнение периодов, динамика по месяцам и паттерн пропусков.
     *
     * Числа текущего периода берутся **из того же отчёта**, что печатает разделы: иначе кадром
     * нельзя было бы увидеть сходимость разделов со сводкой (находка V-71 — сравнение называло
     * 12 из 28 при сводке 48 из 336). Предыдущего периода в фикстуре нет вовсе, и его числа —
     * образец **той же длины и формы** (тридцать суток, те же двенадцать курсов), названный
     * вслух: предмет этой проверки — вёрстка разделов, а расчёт держат доменные тесты и приёмка.
     * Динамика — месяц периода фикстуры: их один, и строк ровно столько же.
     */
    private fun extendedSections(): ExtendedReportSections {
        val report = report()
        return ExtendedReportSections(
            comparison = PeriodComparison(
                current = PeriodSummary(
                    from = report.period.from,
                    to = report.period.to,
                    taken = report.takenTotal,
                    planned = report.plannedTotal,
                    onTimeShare = report.period.onTimeShare,
                    daysWithoutMisses = report.period.daysWithoutMisses,
                    misses = report.period.missesInPeriod,
                    due = report.plannedTotal,
                ),
                // Предыдущий период — **такой же длины**, что отчётный (то же правило, что
                // у `BuildExtendedSectionsUseCase.previousWindow`): фикстура называет образец
                // той же длины и формы, а не «август целиком».
                previous = PeriodSummary(
                    from = report.period.from.minusDays(previousLength()),
                    to = report.period.from.minusDays(1),
                    taken = PREVIOUS_TAKEN,
                    planned = report.plannedTotal,
                    onTimeShare = OnTimeShare.Value(onTime = PREVIOUS_TAKEN, due = report.plannedTotal),
                    daysWithoutMisses = PREVIOUS_DAYS_WITHOUT_MISSES,
                    misses = report.plannedTotal - PREVIOUS_TAKEN,
                    due = report.plannedTotal,
                ),
            ),
            // Динамика — **месяцы периода**: их два, и строк столько же (§5.15).
            dynamics = MonthDynamics(months = MONTHS.map(::monthAdherence)),
            pattern = pattern(report),
        )
    }

    /**
     * Паттерн пропусков фикстуры: перечень дат и число называют **одно и то же**.
     *
     * Пропусков у лидера восемь из двенадцати — минимум в четыре пропуска, доля 0,67 при пороге
     * 0,6, два времени приёма в периоде и единственный лидер: правило говорит, как в продукте.
     * Утверждение фикстуры проверяется `check`: если пропуски разъедутся с перечнем, стенд
     * упадёт здесь, а не покажет кадром раздел, которого продукт не напечатал бы.
     */
    private fun pattern(report: DoctorReport): MissedPatternBlock {
        val missed = slice().intakes.filter { intake ->
            intake.scheduledAt.toLocalTime() == MORNING &&
                (intake.status == IntakeStatus.SKIPPED || intake.status == IntakeStatus.MISSED)
        }
        val dates = missed.map { it.scheduledAt.toLocalDate() }.distinct().sorted()
        check(dates.size == missed.size) {
            "the fixture must keep one missed morning per listed date: ${dates.size} dates, ${missed.size} misses"
        }
        check(missed.size >= PATTERN_MISSES) { "the leader must pass the minimum of $PATTERN_MISSES misses" }
        check(missed.size * SHARE_DENOMINATOR >= SHARE_NUMERATOR * report.period.missesInPeriod) {
            "the leader must carry at least 0.6 of the misses: ${missed.size} of ${report.period.missesInPeriod}"
        }
        // Природа пропуска — по статусу приёма (ПР-1), а не «все отмечены человеком»: один и тот же
        // пропуск не имеет права быть и «отмеченным человеком» в разделе, и «без отметки» в базовой
        // строке того же документа. Природа сверяется с базовой строкой ниже.
        val skippedByPerson = missed.count { it.status == IntakeStatus.SKIPPED }
        val missedWithoutMark = missed.count { it.status == IntakeStatus.MISSED }
        val ownerId = missed.first().medicationId
        val owner = requireNotNull(
            report.medications.firstOrNull { row -> row.medicationId == ownerId },
        ) { "the fixture must carry the row of the missed course" }
        check(skippedByPerson == owner.skippedByPerson && missedWithoutMark == owner.missedWithoutMark) {
            "the nature of the misses must match the base row: " +
                "$skippedByPerson/$missedWithoutMark versus ${owner.skippedByPerson}/${owner.missedWithoutMark}"
        }
        return MissedPatternBlock(
            time = MORNING,
            missedDates = dates,
            skippedByPerson = skippedByPerson,
            missedWithoutMark = missedWithoutMark,
        )
    }

    /** Длина предыдущего периода: сутки отчётного периода включительно (как в домене). */
    private fun previousLength(): Long = ChronoUnit.DAYS.between(WINDOW.from, WINDOW.to) + 1

    /**
     * Кадры страниц документа растром.
     *
     * Каталог — **media-каталог приложения** (`/sdcard/Android/media/<pkg>/report-frames/`):
     * он доступен без разрешений и не стирается вместе с приложением. Прогон
     * `connectedDebugAndroidTest` удаляет приложение после себя — кадры, положенные
     * в `getExternalFilesDir`, к моменту просмотра исчезали (находка прогона на эмуляторе).
     * Имя файла называет состав и номер страницы: визуальная сверка забирает кадры с устройства
     * и смотрит их глазами — кадр артефакт проверки, а не эталон.
     */
    private fun exportFrames(file: File, prefix: String, limit: Int): List<File> {
        val directory = File(mediaDirectory(), FRAME_DIRECTORY).apply { mkdirs() }
        return withRenderer(file) { pdf ->
            (0 until limit).map { index ->
                pdf.openPage(index).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val frame = File(directory, "$prefix${index + 1}.png")
                    frame.outputStream().use { output ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, FRAME_QUALITY, output)
                    }
                    bitmap.recycle()
                    frame
                }
            }
        }
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

    /**
     * Каталог, переживающий удаление приложения: media-каталог, а при его отсутствии —
     * внутренний. Им же пользуется записанный файл выгрузки: его читает сверка на устройстве.
     */
    private fun mediaDirectory(): File = context.getExternalMediaDirs().firstOrNull { it != null } ?: context.filesDir

    private fun <T> withRenderer(file: File, block: (PdfRenderer) -> T): T =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use(block)
        }

    private companion object {
        const val PROFILE_ID = "11111111-1111-4111-8111-111111111111"
        const val PROFILE_NAME = "profile-a"
        const val REPORT_FILE_NAME = "device-report.pdf"

        /** Куда ложатся кадры страниц: их забирает визуальная сверка. */
        const val FRAME_DIRECTORY = "report-frames"
        const val FRAME_PREFIX_BASE = "base-page-"
        const val FRAME_PREFIX_EXTENDED = "extended-page-"
        const val FRAME_QUALITY = 100
        const val MEDICATIONS = 12
        const val DAYS_WITH_INTAKES = 28
        const val TAKEN_EVERY = 7
        const val SKIPPED_EVERY = 3

        /** Предыдущий период фикстуры: числа образца той же длины и формы (V-71). */
        const val PREVIOUS_TAKEN = 300
        const val PREVIOUS_DAYS_WITHOUT_MISSES = 26

        /**
         * Чей курс пропускает утро и вечер: пропуск принадлежит **одному** курсу на дату.
         *
         * Иначе утренних пропусков было бы в двенадцать раз больше перечисленных дат,
         * и строка чисел раздела разошлась бы со своим перечнем дат.
         */
        const val MORNING_MISS_OWNER = 0
        const val EVENING_MISS_OWNER = 1

        /** Пропуски лидера и добавка к ним: восемь утренних против четырёх вечерних (Р-85 п. 4). */
        const val PATTERN_MISSES = 8
        const val EVENING_MISSES = 4
        const val GAP_TO_MISS = 7
        const val GAP_TO_EVENING_MISS = 11

        /** Порог доли лидера: 0,6 (Р-85 п. 4) — тем же счётом, что у правила. */
        const val SHARE_NUMERATOR = 6
        const val SHARE_DENOMINATOR = 10

        const val SEPARATOR = ','
        const val QUOTE = '"'

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

        /** Второе время приёма фикстуры: без него правило паттерна молчит (Р-85 п. 4). */
        val EVENING: LocalTime = LocalTime.of(20, 0)
        val TIMES: List<LocalTime> = listOf(MORNING, EVENING)

        /** Два месяца периода фикстуры: динамике нужен не один (§5.15). */
        val MONTHS: List<YearMonth> = listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9))

        val WINDOW: HistoryWindow = HistoryWindow(MONTHS.first().atDay(1), MONTHS.last().atEndOfMonth())
        val GENERATED_AT: Instant = LocalDate.of(2026, 9, 30).atTime(10, 0).atZone(ZONE).toInstant()
    }
}

/** Файл выгрузки на диске устройства: запись идёт в каталог, переживающий удаление приложения. */
private class FilesInCache(private val context: Context) : TransferFiles {

    private var target: File? = null

    override fun freeSpaceBytes(): Long = Long.MAX_VALUE

    override suspend fun write(target: ExportTarget, content: String) {
        // Файл ложится туда же, где кадры: прогон удаляет приложение, и файл из внутреннего
        // каталога к моменту сверки исчез бы вместе с ним.
        val file = File(context.getExternalMediaDirs().firstOrNull { it != null } ?: context.filesDir, target.handle)
        file.writeText(content)
        this.target = file
    }

    override suspend fun discard(target: ExportTarget) {
        File(context.getExternalMediaDirs().firstOrNull { it != null } ?: context.filesDir, target.handle).delete()
        this.target = null
    }

    override suspend fun read(source: ImportSource): String = error("the export never reads")

    override suspend fun writeSafetyCopy(content: String): SafetyCopy = error("the export writes no copy")

    override fun safetyCopy(): SafetyCopy? = null

    fun writtenFile(): File? = target?.takeIf { it.isFile }
}

/** Обмен и профиль для проверки записи: она их не трогает (передача проверяется стендом). */
private object NoShare : DoctorReportTransfer {

    override suspend fun share(document: ReportDocument, launcher: ReportShareLauncher): ReportTransferOutcome =
        ReportTransferOutcome.Shared

    override suspend fun save(document: ReportDocument, target: ReportTarget): ReportTransferOutcome =
        ReportTransferOutcome.Saved
}

private object ActiveProfile : ActiveProfileRepository {

    override val activeProfileId: kotlinx.coroutines.flow.Flow<String> =
        kotlinx.coroutines.flow.flowOf("11111111-1111-4111-8111-111111111111")

    override suspend fun current(): String = "11111111-1111-4111-8111-111111111111"

    override suspend fun switchTo(profileId: String) = Unit
}

private object SilentLog : ru.stohastika.sklyanka.domain.log.OperationLog {

    override suspend fun recent(limit: Int): List<ru.stohastika.sklyanka.domain.log.OpLogRecord> = emptyList()

    override suspend fun lastOccurredAt(event: ru.stohastika.sklyanka.domain.log.OpEvent): java.time.Instant? = null

    override suspend fun prune(keepDays: Long, keepRecords: Int) = Unit

    override suspend fun record(
        event: ru.stohastika.sklyanka.domain.log.OpEvent,
        profileId: String?,
        medicationId: String?,
        intakeId: String?,
        before: String?,
        after: String?,
        source: String?,
        detail: String?,
    ) = Unit
}
