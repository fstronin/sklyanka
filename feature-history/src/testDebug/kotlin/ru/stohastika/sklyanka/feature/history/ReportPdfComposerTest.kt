package ru.stohastika.sklyanka.feature.history

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.history.AdherenceCalculator
import ru.stohastika.sklyanka.domain.history.DoctorReport
import ru.stohastika.sklyanka.domain.history.DoctorReportBuilder
import ru.stohastika.sklyanka.domain.history.HistorySlice
import ru.stohastika.sklyanka.domain.history.HistoryWindow
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Intake
import ru.stohastika.sklyanka.domain.model.IntakeSource
import ru.stohastika.sklyanka.domain.model.IntakeStatus
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * Состав документа отчёта врачу (решение дизайна по ОВ-15).
 *
 * Идёт на стенде, а не на JVM: формулировки и склонения живут в ресурсах модуля, и
 * проверять их подстановкой значило бы проверять подстановку. Настоящий PDF здесь
 * не рисуется: `PdfDocument` работает только на платформе, байты проверяет
 * инструментальная проверка устройства (`PdfDoctorReportRendererDeviceTest`).
 *
 * Локаль закреплена русской: документ обязан читаться так же, как экран (НФТ-43),
 * а месяц в списке дат — стоять в родительном падеже.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU")
class ReportPdfComposerTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val composer = ReportPdfComposer(context)

    @Test
    fun `the document is a PDF named after the first month of the period`() {
        val content = content()

        assertEquals("sklyanka-report-2026-09.pdf", content.fileName)
        assertEquals("application/pdf", content.mediaType)
    }

    @Test
    fun `the header names the title the profile the period and the moment`() {
        val text = content().documentText()

        assertTrue("the title must open the document: $text", text.startsWith("Отчёт о приёме лекарств"))
        assertTrue("the profile must be named (ОВ-57)", text.contains("Склянка · профиль «profile-a»"))
        assertTrue("the period must be compact inside one month", text.contains("Период: 1 — 30 сентября 2026"))
        assertTrue("the moment of formation must be printed", text.contains("Сформирован: 30 сентября 2026, 10:00"))
    }

    /** Числа сводки те же, что на экране: строка — сумма строк лекарств (`TDD.md §5.2`). */
    @Test
    fun `the summary repeats the numbers of the screen`() {
        val report = report()
        val content = content(report)

        assertEquals(
            percent(2, 5, 40),
            content.summary.first { it.label == "Принято:" }.value,
        )
        assertEquals("Принято вовремя: —", content.summary[1].let { "${it.label} ${it.value}" })
        assertEquals(report.period.daysWithoutMisses.toString(), content.summary[2].value)
        assertEquals(report.period.missesInPeriod.toString(), content.summary[3].value)
    }

    /** Порог «вовремя» не согласован (ОВ-4): в документе прочерк, а не выдуманное число. */
    @Test
    fun `the on time share is a dash while the threshold is not agreed`() {
        val text = content().documentText()

        assertTrue(text.contains("Принято вовремя: —"))
    }

    @Test
    fun `a medication block carries the name the dose the schedule and its share`() {
        val text = content().documentText()

        assertTrue("the dose must decline with its amount", text.contains("med-a · 1 таблетка · 50 мг · 08:00"))
        assertTrue("the share must stand in the block: $text", text.contains(share(1, 4, 25)))
    }

    @Test
    fun `a course without an end is printed as open`() {
        val medication = medication(id = MEDICATION_ID, name = "med-a").copy(endDate = null)
        val text = composer.compose(report(medication = medication), ZONE).documentText()

        assertTrue("the open course must be named by words: $text", text.contains("с 1 сентября 2026, бессрочно"))
    }

    /**
     * Пропуск человека и автоматический пропуск различаются и здесь.
     *
     * Даты группируются по месяцу — месяц печатается один раз, а не у каждой даты;
     * природа пропуска остаётся: для врача это разные вещи (ПР-1).
     */
    @Test
    fun `the misses list the dates and keep the person apart from the automatic ones`() {
        val text = content().documentText()

        assertTrue(
            "dates and natures must be printed together: $text",
            text.contains("Пропуски: 2, 3, 4 сентября; из них 1 отмечен человеком, 2 — без отметки"),
        )
    }

    /** ПР-11: архивированный курс в документе есть, помечен и напечатан как любой другой. */
    @Test
    fun `an archived course is marked and keeps its own numbers`() {
        val block = content().medicationBlocks.single { it.lines.first().text.startsWith("med-c") }

        assertTrue("the course must be marked by words", block.lines.any { it.text.contains("Курс в архиве") })
        assertEquals("the course must carry its own planned count", share(1, 1, 100), block.right)
        assertTrue("a course without misses must say so", block.lines.any { it.text.contains("Пропусков нет") })
    }

    @Test
    fun `a month without records says so and prints no medication block`() {
        val empty = DoctorReportBuilder.build(
            period = AdherenceCalculator().of(HistorySlice.empty(PROFILE_ID), WINDOW, TODAY, ZONE),
            slice = HistorySlice.empty(PROFILE_ID),
            profileName = PROFILE_NAME,
            generatedAt = GENERATED_AT,
            zone = ZONE,
        )

        val content = composer.compose(empty, ZONE)

        assertNull("the medication block must not be printed", content.medicationsTitle)
        assertTrue(content.medicationBlocks.isEmpty())
        assertEquals("За период нет ни одной записи о приёме", content.emptyNote?.text)
    }

    /** Оговорка — часть состава документа, а не украшение: её печатает каждая страница. */
    @Test
    fun `the document carries the medical disclaimer`() {
        assertEquals("Не является медицинским документом", content().disclaimer)
    }

    /**
     * **Отчёт не даёт рекомендаций.**
     *
     * Приложение не медицинское изделие (`CLAUDE.md`), и ограничение проверяется, а не
     * держится на памяти ревьюера: список слов подобран по формулировкам, которые
     * `task.md` называет недопустимыми прямо.
     */
    @Test
    fun `the document gives no advice and no judgement`() {
        val text = content().documentText()

        FORBIDDEN.forEach { word ->
            assertFalse(
                "the report must not contain advice or judgement: $word",
                text.contains(word, ignoreCase = true),
            )
        }
    }

    /** Состав шторки: профиль, период и число принятых приёмов — без текста документа. */
    @Test
    fun `the composition names the profile the period and the taken count`() {
        assertEquals(
            "Профиль: profile-a\nПериод: 1 — 30 сентября 2026\nПринято: " + percent(2, 5, 40),
            composer.composition(report()),
        )
    }

    /**
     * Ожидаемые числа и доли — через те же ресурсы, что у документа.
     *
     * Литералом, а не пересказом: русская локаль Android ставит перед знаком процента
     * неразрывный пробел, и переписанное в тест «(40 %)» разошлось бы с настоящим текстом
     * на один невидимый знак — ровно та ошибка, которую такой тест и должен ловить.
     */
    private fun percent(taken: Int, planned: Int, share: Int): String = context.resources.getQuantityString(
        R.plurals.report_document_intakes_of_total,
        planned,
        taken,
        planned,
        context.getString(R.string.history_percent, share),
    )

    private fun share(taken: Int, planned: Int, percent: Int): String = context.getString(
        R.string.report_document_medication_share,
        taken,
        planned,
        context.getString(R.string.history_percent, percent),
    )

    private fun content(report: DoctorReport = report()): ReportPdfContent = composer.compose(report, ZONE)

    private fun report(medication: Medication = medication()): DoctorReport {
        val slice = HistorySlice(
            profileId = PROFILE_ID,
            intakes = intakes(),
            medications = listOf(medication, archived()),
            historyStartsAt = LocalDate.of(2026, 9, 1),
        )
        return DoctorReportBuilder.build(
            period = AdherenceCalculator().of(slice, WINDOW, TODAY, ZONE),
            slice = slice,
            profileName = PROFILE_NAME,
            generatedAt = GENERATED_AT,
            zone = ZONE,
        )
    }

    private fun intakes(): List<Intake> = listOf(
        intake(LocalDate.of(2026, 9, 1).atTime(MORNING), IntakeStatus.TAKEN),
        intake(LocalDate.of(2026, 9, 2).atTime(MORNING), IntakeStatus.SKIPPED),
        intake(LocalDate.of(2026, 9, 3).atTime(MORNING), IntakeStatus.MISSED),
        intake(LocalDate.of(2026, 9, 4).atTime(MORNING), IntakeStatus.MISSED),
        intake(LocalDate.of(2026, 9, 1).atTime(MORNING), IntakeStatus.TAKEN, ARCHIVED_ID),
    )

    private fun intake(at: LocalDateTime, status: IntakeStatus, medicationId: String = MEDICATION_ID) = Intake(
        id = "intake-$medicationId-$at-$status",
        medicationId = medicationId,
        scheduledAt = at,
        status = status,
        actedAt = at.atZone(ZONE).toInstant(),
        source = if (status == IntakeStatus.MISSED) IntakeSource.AUTO else IntakeSource.PHONE,
        actorProfileId = null,
    )

    private fun archived() = medication(
        id = ARCHIVED_ID,
        name = "med-c",
        archivedAt = LocalDate.of(2026, 9, 10).atTime(18, 0).atZone(ZONE).toInstant(),
    )

    private fun medication(id: String = MEDICATION_ID, name: String = "med-a", archivedAt: Instant? = null) =
        Medication(
            id = id,
            profileId = PROFILE_ID,
            name = name,
            form = MedicationForm.TABLET,
            shape = null,
            doseAmount = BigDecimal("1"),
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
            archivedAt = archivedAt,
            archiveReason = archivedAt?.let { ArchiveReason.COURSE_FINISHED },
        )

    private companion object {
        const val PROFILE_ID = "11111111-1111-4111-8111-111111111111"
        const val MEDICATION_ID = "33333333-3333-4333-8333-333333333333"
        const val ARCHIVED_ID = "55555555-5555-4555-8555-555555555555"
        const val PROFILE_NAME = "profile-a"

        val ZONE: ZoneId = ZoneId.of("Europe/Moscow")
        val TODAY: LocalDate = LocalDate.of(2026, 9, 30)
        val MORNING: LocalTime = LocalTime.of(8, 0)
        val WINDOW: HistoryWindow = HistoryWindow.of(YearMonth.of(2026, 9))
        val GENERATED_AT: Instant = LocalDate.of(2026, 9, 30).atTime(10, 0).atZone(ZONE).toInstant()

        /**
         * Чего в отчёте быть не может.
         *
         * Слова взяты из `task.md`: «перенесите приём», «вы плохо соблюдаете назначение»,
         * «обратитесь к врачу» названы там недопустимыми прямо.
         */
        val FORBIDDEN = listOf(
            "рекоменд",
            "советуем",
            "перенесите",
            "обратитесь",
            "следует",
            "плохо",
            "нарушен",
            "риск",
        )
    }
}
