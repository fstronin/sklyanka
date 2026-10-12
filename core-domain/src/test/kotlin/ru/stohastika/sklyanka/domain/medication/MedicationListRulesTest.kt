package ru.stohastika.sklyanka.domain.medication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.ArchiveReason
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Правила списка лекарств: счётчик активных (ЭКР-3) и правая часть строки расписания (ЭКР-4).
 *
 * Оба выведены из расхождений макета `design/screens/Meds.dc.html` с текстовой
 * спецификацией, поэтому и проверяются отдельно: правило, живущее только в разметке,
 * разойдётся с экраном «Сегодня», который пользуется тем же числом.
 */
@DisplayName("List rules count the active courses and choose what the schedule line shows")
class MedicationListRulesTest {

    @Test
    fun `the active counter ignores as needed courses`() {
        // Макет: чип «Активные · 5» при шести нарисованных карточках, шестая — «по потребности».
        val medications = List(5) { index -> scheduled(id = "scheduled-$index") } + prn()

        assertEquals(5, MedicationListRules.activeCount(medications))
    }

    @Test
    fun `the active counter ignores archived courses`() {
        val medications = listOf(
            scheduled(id = "active"),
            scheduled(id = "archived").copy(
                archivedAt = Instant.parse("2026-09-01T00:00:00Z"),
                archiveReason = ArchiveReason.MANUAL,
            ),
        )

        assertEquals(1, MedicationListRules.activeCount(medications))
    }

    @Test
    fun `an empty list counts to zero`() {
        assertEquals(0, MedicationListRules.activeCount(emptyList()))
    }

    // --- Строка расписания ----------------------------------------------------------------

    @Test
    fun `a triggered stock threshold replaces the course period with the stock left`() {
        // 9 таблеток по 2 штуки дважды в сутки — это два дня, а порог напоминания пять.
        val medication = scheduled(
            times = listOf(MORNING, EVENING),
            dose = "2",
            stockLeft = "9",
            thresholdDays = 5,
        )

        val line = MedicationListRules.trailingLineOf(medication)

        assertEquals(TrailingLine.Stock(BigDecimal("9"), DoseUnit.TABLET), line)
    }

    @Test
    fun `a stock above the threshold keeps the course period`() {
        val medication = scheduled(
            times = listOf(MORNING, EVENING),
            dose = "2",
            stockLeft = "60",
            thresholdDays = 5,
        )

        assertEquals(TrailingLine.Period(START_DATE, null), MedicationListRules.trailingLineOf(medication))
    }

    @Test
    fun `a course without a purchase reminder never shows the stock line`() {
        val medication = scheduled(
            times = listOf(MORNING, EVENING),
            dose = "2",
            stockLeft = "1",
            thresholdDays = null,
        )

        assertFalse(MedicationListRules.stockThresholdReached(medication))
        assertTrue(MedicationListRules.trailingLineOf(medication) is TrailingLine.Period)
    }

    @Test
    fun `a course whose stock is not tracked shows the period`() {
        val medication = scheduled(stockLeft = null, thresholdDays = 5)

        assertEquals(TrailingLine.Period(START_DATE, null), MedicationListRules.trailingLineOf(medication))
    }

    @Test
    fun `a course with an end date carries it into the period line`() {
        val end = LocalDate.of(2026, 9, 30)

        assertEquals(
            TrailingLine.Period(START_DATE, end),
            MedicationListRules.trailingLineOf(scheduled(stockLeft = null).copy(endDate = end)),
        )
    }

    // --- «Хватит на N дней» ---------------------------------------------------------------

    @Test
    fun `days of stock left round down`() {
        // 9 таблеток по 2 штуки дважды в сутки — 4 в сутки, хватает на два дня с хвостом.
        val medication = scheduled(times = listOf(MORNING, EVENING), dose = "2", stockLeft = "9")

        assertEquals(2, MedicationListRules.daysOfStockLeft(medication))
    }

    @Test
    fun `an as needed course has no defined daily consumption`() {
        // Открытый вопрос docs/norm/04-behavior.md §3: расход по потребности не определён,
        // поэтому порог для такого курса не срабатывает вовсе.
        val medication = prn().copy(stockLeft = BigDecimal("1"), stockThresholdDays = 5)

        assertNull(MedicationListRules.daysOfStockLeft(medication))
        assertFalse(MedicationListRules.stockThresholdReached(medication))
    }

    private companion object {
        val START_DATE: LocalDate = LocalDate.of(2026, 3, 12)
        val MORNING: LocalTime = LocalTime.of(8, 0)
        val EVENING: LocalTime = LocalTime.of(20, 0)

        @Suppress("LongParameterList")
        fun scheduled(
            id: String = "scheduled",
            times: List<LocalTime> = listOf(MORNING),
            dose: String = "1",
            stockLeft: String? = null,
            thresholdDays: Int? = null,
        ) = Medication(
            id = id,
            profileId = Drafts.PROFILE_ID,
            name = "med-$id",
            form = MedicationForm.TABLET,
            shape = null,
            doseAmount = BigDecimal(dose),
            doseUnit = DoseUnit.TABLET.code,
            strength = null,
            strengthUnit = null,
            foodRelation = FoodRelation.ANY,
            scheduleMode = ScheduleMode.SCHEDULED,
            maxPerDay = null,
            times = times,
            startDate = START_DATE,
            endDate = null,
            stockLeft = stockLeft?.let { BigDecimal(it) },
            stockThresholdDays = thresholdDays,
            archivedAt = null,
            archiveReason = null,
        )

        fun prn() = scheduled(id = "as-needed").copy(
            scheduleMode = ScheduleMode.PRN,
            times = emptyList(),
            maxPerDay = 3,
        )
    }
}
