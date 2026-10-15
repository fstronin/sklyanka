package ru.stohastika.sklyanka.domain.medication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.Fixtures
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.ScheduleRule
import ru.stohastika.sklyanka.domain.model.Stage
import ru.stohastika.sklyanka.domain.stock.StockForecast
import java.math.BigDecimal
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Суточный расход по виду расписания (ПР-13, ПР-16…ПР-19).
 *
 * До появления видов расписания расход считался как «доза × число времён», и для четырёх
 * новых видов это была неправда: у недельного курса он оказывался завышен в семь раз,
 * у циклов и ступеней — занижен. Здесь закреплены числа всех четырёх видов и то, что
 * прогноз запаса и порог покупки считают **один и тот же** расход — расход экрана лекарства.
 */
class MedicationDailyUseTest {

    @Test
    fun `an everyday course spends its dose for every time of the day`() {
        val medication = medication(times = listOf(MORNING, EVENING), dose = "2")

        assertMoney("4", MedicationDailyUse.of(medication))
        // Тем же расходом считает прогноз запаса: 9 таблеток при 4 в сутки — два дня.
        assertEquals(2, MedicationListRules.daysOfStockLeft(medication.copy(stockLeft = money("9"))))
    }

    /**
     * Недельный курс: три дня из семи.
     *
     * Расход — доля дней, а не число времён: 21 таблетка при трёх приёмах в неделю
     * кончается через семь недель, то есть через 49 суток, а не через неделю.
     */
    @Test
    fun `a weekday course spends the share of the chosen days`() {
        val medication = medication(rule = ScheduleRule.Weekdays(MON_WED_FRI), stock = "21")

        assertMoney("0.428571", MedicationDailyUse.of(medication))
        assertEquals(49, MedicationListRules.daysOfStockLeft(medication))
        assertEquals(StockForecast.Days(49), StockForecast.of(medication))
    }

    /** Один день недели — база (Р-24): расход — седьмая часть суточной дозы. */
    @Test
    fun `a single weekday course spends one day out of seven`() {
        val medication = medication(rule = ScheduleRule.Weekdays(setOf(DayOfWeek.WEDNESDAY)), stock = "9")

        assertMoney("0.142857", MedicationDailyUse.of(medication))
        assertEquals(63, MedicationListRules.daysOfStockLeft(medication))
    }

    /** «Через два дня» — каждый второй день (ПР-17): расход вдвое меньше суточной дозы. */
    @Test
    fun `an interval course spends one day out of its interval`() {
        val medication = medication(rule = ScheduleRule.EveryNDays(2), stock = "18")

        assertMoney("0.5", MedicationDailyUse.of(medication))
        assertEquals(36, MedicationListRules.daysOfStockLeft(medication))
        assertEquals(StockForecast.Days(36), StockForecast.of(medication))
    }

    /** «Через каждый день» вырождается в ежедневное: расход тот же, что у базового вида (ПР-15). */
    @Test
    fun `an interval of one day spends as much as an everyday course`() {
        assertEquals(
            MedicationDailyUse.of(medication(stock = "9")),
            MedicationDailyUse.of(medication(rule = ScheduleRule.EveryNDays(ONE_DAY), stock = "9")),
        )
    }

    /**
     * Цикл «21 день приёма, 7 перерыва» (ПР-18): перерыв входит в расход.
     *
     * Без него расход был бы завышен на четверть, и «пора покупать» приходило бы
     * до конца упаковки.
     */
    @Test
    fun `a cyclic course spends its share of the cycle`() {
        val medication = medication(rule = ScheduleRule.Cycles(onDays = 21, offDays = 7), stock = "9")

        assertMoney("0.75", MedicationDailyUse.of(medication))
        assertEquals(12, MedicationListRules.daysOfStockLeft(medication))
        assertEquals(StockForecast.Days(12), StockForecast.of(medication))
    }

    /**
     * Ступени: расход считает весь курс, а не дозу первого этапа.
     *
     * Доза лекарства у ступенчатого курса — доза **первого** этапа (её показывает карточка),
     * и считать по ней расход значило бы обещать упаковку на 18 дней вместо 12 (SKL-117, F2).
     */
    @Test
    fun `a staged course spends the average of its stages`() {
        val medication = medication(
            rule = ScheduleRule.Staged(listOf(Stage(money("0.5"), days = 7), Stage(money("1"), days = 7))),
            dose = "0.5",
            stock = "9",
        )

        assertMoney("0.75", MedicationDailyUse.of(medication))
        assertEquals(12, MedicationListRules.daysOfStockLeft(medication))
        assertEquals(StockForecast.Days(12), StockForecast.of(medication))
    }

    /** Этап за концом курса — нарушение ИНВ-24, и в расход он не входит: курс короче этапа. */
    @Test
    fun `a stage beyond the course end does not count`() {
        val medication = medication(
            rule = ScheduleRule.Staged(listOf(Stage(money("0.5"), days = 7), Stage(money("1"), days = 24))),
            dose = "0.5",
            endDate = Fixtures.START_DATE.plusDays(29),
        )

        assertMoney("0.88333333", MedicationDailyUse.doseOfAnIntakeDay(medication))
    }

    /** У бессрочного ступенчатого курса этапы не обрезаются: среднее по назначенным (ПР-6). */
    @Test
    fun `an open ended staged course averages the stages as assigned`() {
        val medication = medication(
            rule = ScheduleRule.Staged(listOf(Stage(money("0.5"), days = 7), Stage(money("1"), days = 24))),
            dose = "0.5",
        )

        assertMoney("0.88709677", MedicationDailyUse.doseOfAnIntakeDay(medication))
    }

    /** Несколько времён в сутки сохраняются у любого вида: умножается доля, а не время (ПР-19). */
    @Test
    fun `every kind keeps the number of times of the day`() {
        val medication = medication(
            rule = ScheduleRule.Cycles(onDays = 21, offDays = 7),
            times = listOf(MORNING, EVENING),
        )

        assertMoney("1.5", MedicationDailyUse.of(medication))
    }

    // --- Ответа нет — и это не ноль -----------------------------------------------------------------

    @Test
    fun `an as needed course has no daily use`() {
        assertNull(MedicationDailyUse.of(Fixtures.prnMedication().copy(stockLeft = money("10"))))
    }

    @Test
    fun `a schedule without times has no daily use`() {
        assertNull(MedicationDailyUse.of(medication(times = emptyList())))
    }

    /**
     * Испорченные параметры вида не дают нулевого расхода.
     *
     * Ноль означал бы «упаковка не кончается никогда», а испорченное значение — это
     * отсутствие ответа (МГ-6): правило читается буквально, и приёмов по нему нет.
     */
    @Test
    fun `a broken kind has no daily use at all`() {
        assertNull(MedicationDailyUse.of(medication(rule = ScheduleRule.Weekdays(emptySet()))))
        assertNull(MedicationDailyUse.of(medication(rule = ScheduleRule.Cycles(onDays = 0, offDays = 7))))
        assertNull(MedicationDailyUse.of(medication(rule = ScheduleRule.Staged(emptyList()))))
    }

    /** Порог покупки при неизвестном расходе не срабатывает: считать его нечем (ПР-13). */
    @Test
    fun `a purchase threshold never triggers without a daily use`() {
        val medication = medication(rule = ScheduleRule.Cycles(onDays = 0, offDays = 7), stock = "1")
            .copy(stockThresholdDays = 30)

        assertNull(MedicationListRules.daysOfStockLeft(medication))
        assertFalse(MedicationListRules.stockThresholdReached(medication))
    }

    private fun medication(
        rule: ScheduleRule = ScheduleRule.Daily,
        times: List<LocalTime> = listOf(MORNING),
        dose: String = "1",
        stock: String = "30",
        endDate: LocalDate? = null,
    ): Medication = Fixtures.scheduledMedication().copy(
        schedule = rule,
        times = times,
        doseAmount = money(dose),
        stockLeft = money(stock),
        endDate = endDate,
    )

    /** Расход — дробь известной длины, и сверять его надо значением, а не масштабом записи. */
    private fun assertMoney(expected: String, actual: BigDecimal?) {
        requireNotNull(actual) { "the daily use must be defined, expected $expected" }
        assertEquals(0, BigDecimal(expected).compareTo(actual), "expected $expected, got $actual")
    }

    private fun money(value: String): BigDecimal = BigDecimal(value)

    private companion object {
        val MORNING: LocalTime = LocalTime.of(8, 0)
        val EVENING: LocalTime = LocalTime.of(20, 0)
        val MON_WED_FRI: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        const val ONE_DAY = 1
    }
}
