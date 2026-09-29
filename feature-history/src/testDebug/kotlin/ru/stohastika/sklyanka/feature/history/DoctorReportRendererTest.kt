package ru.stohastika.sklyanka.feature.history

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
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
 * Документ отчёта врачу.
 *
 * Идёт на стенде, а не на JVM: формулировки живут в ресурсах модуля, и проверять их
 * подстановкой значило бы проверять подстановку. Состав отчёта проверяется в домене
 * (`DoctorReportBuilderTest`), здесь — текст, который получит врач.
 */
@RunWith(AndroidJUnit4::class)
class DoctorReportRendererTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val renderer = TextDoctorReportRenderer(context)

    @Test
    fun theDocumentNamesThePeriodTheProfileAndTheMomentItWasBuilt() {
        val document = renderer.render(report(), ZONE)

        assertTrue(document.content.startsWith(context.getString(R.string.report_document_title)))
        assertTrue(document.content.contains(context.getString(R.string.report_document_profile, PROFILE_NAME)))
        assertTrue(document.content.contains(PERIOD_LINE))
        assertTrue(document.content.contains(GENERATED_LINE))
    }

    @Test
    fun theDocumentCarriesTheSummaryNumbers() {
        val document = renderer.render(report(), ZONE)

        assertTrue(document.content.contains(context.getString(R.string.report_document_summary)))
        assertTrue(document.content.contains(DAYS_WITHOUT_MISSES_LINE))
        assertTrue(document.content.contains(MISSES_LINE))
    }

    /** ОВ-4: доля принятого вовремя не определена — в отчёте стоит прочерк, а не число. */
    @Test
    fun theOnTimeShareIsADashInTheDocumentToo() {
        val document = renderer.render(report(), ZONE)

        assertTrue(
            document.content.contains(
                context.getString(
                    R.string.report_document_on_time,
                    context.getString(R.string.history_value_dash),
                ),
            ),
        )
    }

    @Test
    fun theDocumentListsEveryMedicationWithItsShareAndSchedule() {
        val document = renderer.render(report(), ZONE)

        assertTrue(document.content.contains(MEDICATION_LINE))
        assertTrue(document.content.contains(DOSE_LINE))
        assertTrue(document.content.contains(STRENGTH_LINE))
        assertTrue(document.content.contains(SCHEDULE_LINE))
        assertTrue(document.content.contains(COURSE_LINE))
    }

    /** ПР-11: архивированный курс в отчёте есть и помечен как архивный. */
    @Test
    fun anArchivedCourseIsMarkedInTheDocument() {
        val document = renderer.render(report(), ZONE)

        assertTrue(document.content.contains("med-c"))
        assertTrue(document.content.contains(context.getString(R.string.report_document_archived)))
    }

    /**
     * Пропуски разделены по природе, и даты перечислены.
     *
     * Врачу разница говорит разное: отметка человека — о поведении, автоматический
     * пропуск может говорить о выключенном телефоне (`TDD.md §5.3`).
     */
    @Test
    fun theDocumentSeparatesTheMarksOfThePersonFromTheAutomaticOnes() {
        val document = renderer.render(report(), ZONE)

        assertTrue(document.content.contains(MISSES_DETAIL_LINE))
        assertTrue(document.content.contains(MISSED_DATES_LINE))
    }

    /**
     * **Отчёт не даёт рекомендаций.**
     *
     * Приложение не медицинское изделие (`CLAUDE.md`), и это ограничение проверяется,
     * а не держится на памяти ревьюера: ни совета, ни оценки поведения, ни побуждения
     * к действию в документе быть не может. Список слов подобран по формулировкам,
     * которые `task.md` называет недопустимыми прямо.
     */
    @Test
    fun theDocumentGivesNoAdviceAndNoJudgement() {
        val document = renderer.render(report(), ZONE)

        FORBIDDEN.forEach { word ->
            assertFalse(
                "the report must not contain advice or judgement: $word",
                document.content.contains(word, ignoreCase = true),
            )
        }
    }

    @Test
    fun theFileIsNamedByThePeriodAndCarriesItsMediaType() {
        val document = renderer.render(report(), ZONE)

        assertEquals("sklyanka-report-2026-09.txt", document.fileName)
        assertEquals("text/plain", document.mediaType)
    }

    @Test
    fun aMonthWithoutRecordsSaysSoInsteadOfShowingAnEmptyTable() {
        val empty = DoctorReportBuilder.build(
            period = AdherenceCalculator().of(HistorySlice.empty(PROFILE_ID), WINDOW, TODAY, ZONE),
            slice = HistorySlice.empty(PROFILE_ID),
            profileName = PROFILE_NAME,
            generatedAt = GENERATED_AT,
            zone = ZONE,
        )

        val document = renderer.render(empty, ZONE)

        assertTrue(document.content.contains(context.getString(R.string.report_document_empty)))
    }

    private fun report(): DoctorReport {
        val slice = HistorySlice(
            profileId = PROFILE_ID,
            intakes = intakes(),
            medications = listOf(active(), archived()),
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

    private fun active() = medication(id = MEDICATION_ID, name = "med-a")

    private fun archived() = medication(
        id = ARCHIVED_ID,
        name = "med-c",
        archivedAt = LocalDate.of(2026, 9, 10).atTime(18, 0).atZone(ZONE).toInstant(),
    )

    private fun medication(id: String, name: String, archivedAt: Instant? = null) = Medication(
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

        /** Строки, которые обязаны появиться в документе дословно. */
        const val PERIOD_LINE = "Период: 1 сентября 2026 — 30 сентября 2026"
        const val GENERATED_LINE = "Сформирован: 30 сентября 2026, 10:00"
        const val DAYS_WITHOUT_MISSES_LINE = "Дней без пропусков: 1"
        const val MISSES_LINE = "Пропусков за период: 3"
        const val MEDICATION_LINE = "med-a — принято 1 из 4"
        const val DOSE_LINE = "Доза: по 1 таблетке"
        const val STRENGTH_LINE = "Дозировка: 50 мг"
        const val SCHEDULE_LINE = "Приём: 08:00"
        const val COURSE_LINE = "Курс: 1 сентября 2026 — 1 октября 2026"
        const val MISSES_DETAIL_LINE = "3 пропуска (отмечено человеком: 1, без отметки: 2)"
        const val MISSED_DATES_LINE = "Даты пропусков: 2 сентября 2026, 3 сентября 2026, 4 сентября 2026"

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
