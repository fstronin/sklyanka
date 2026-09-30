package ru.stohastika.sklyanka.feature.history

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.history.AdherenceCalculator
import ru.stohastika.sklyanka.domain.history.DoctorReport
import ru.stohastika.sklyanka.domain.history.DoctorReportBuilder
import ru.stohastika.sklyanka.domain.history.HistorySlice
import ru.stohastika.sklyanka.domain.history.HistoryWindow
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
 * Отчёт врачу на телефоне с чужим языком системы (SKL-45, НФТ-43а, НФТ-44).
 *
 * Прогон идёт с локалью устройства `en-US` — той самой, на которой дефект нашёлся
 * (находка 44-A SKL-44): `ReportPdfComposer` получает контекст приложения, и его
 * форматтеры печатали «Сформирован: 30 September 2026», а склонения брали английские
 * правила («5 таблетки»). С русским телефоном прогон дефект не видит.
 *
 * Отчёт собирается настоящим композером и с контекстом приложения — тем путём, которым
 * его собирает продукт. Ожидания записаны словами, а не через ресурсы: ожидание,
 * выраженное теми же ресурсами, что и проверяемый документ, совпадает с любым
 * результатом, включая неверный (приём SKL-28).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w390dp-h2400dp-mdpi")
class ReportForeignLocaleTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val composer = ReportPdfComposer(context)

    @Test
    fun `the report keeps the words of its dates in the interface language`() {
        val text = composer.compose(report(), ZONE).documentText()

        assertTrue("the period must be named in Russian: $text", text.contains("Период: 1 — 30 сентября 2026"))
        assertTrue(
            "the moment of formation must be named in Russian: $text",
            text.contains("Сформирован: 30 сентября 2026, 10:00"),
        )
        assertFalse("no English month may reach the doctor: $text", text.contains("September"))
    }

    @Test
    fun `the report keeps the plural rules of the interface language`() {
        val text = composer.compose(report(), ZONE).documentText()

        assertTrue("five tablets must take the Russian many form: $text", text.contains("5 таблеток"))
        assertFalse("no English plural rule may reach the doctor: $text", text.contains("5 таблетки"))
    }

    @Test
    fun `the sheet composition keeps the words of its dates in the interface language`() {
        val composition = composer.composition(report())

        assertTrue(
            "the sheet must name the period in Russian: $composition",
            composition.startsWith("Профиль: profile-a\nПериод: 1 — 30 сентября 2026\nПринято:"),
        )
        assertFalse("no English month may reach the sheet: $composition", composition.contains("September"))
    }

    private fun report(): DoctorReport {
        val slice = HistorySlice(
            profileId = PROFILE_ID,
            intakes = listOf(
                intake(LocalDate.of(2026, 9, 1).atTime(MORNING)),
            ),
            medications = listOf(medication()),
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

    private fun intake(at: LocalDateTime) = Intake(
        id = "intake-$at",
        medicationId = MEDICATION_ID,
        scheduledAt = at,
        status = IntakeStatus.TAKEN,
        actedAt = at.atZone(ZONE).toInstant(),
        source = IntakeSource.PHONE,
        actorProfileId = null,
    )

    private fun medication() = Medication(
        id = MEDICATION_ID,
        profileId = PROFILE_ID,
        name = "med-a",
        form = MedicationForm.TABLET,
        shape = null,
        // Пять таблеток — не украшение фикстуры: русское правило для пяти даёт «таблеток»,
        // английское — «таблетки», и на этом расходятся языки (НФТ-44).
        doseAmount = BigDecimal("5"),
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

    private companion object {
        const val PROFILE_ID = "11111111-1111-4111-8111-111111111111"
        const val MEDICATION_ID = "33333333-3333-4333-8333-333333333333"
        const val PROFILE_NAME = "profile-a"

        val ZONE: ZoneId = ZoneId.of("Europe/Moscow")
        val TODAY: LocalDate = LocalDate.of(2026, 9, 30)
        val MORNING: LocalTime = LocalTime.of(8, 0)
        val WINDOW: HistoryWindow = HistoryWindow.of(YearMonth.of(2026, 9))
        val GENERATED_AT: Instant = LocalDate.of(2026, 9, 30).atTime(10, 0).atZone(ZONE).toInstant()
    }
}
