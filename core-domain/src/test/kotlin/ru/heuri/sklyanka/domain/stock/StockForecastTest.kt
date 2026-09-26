package ru.heuri.sklyanka.domain.stock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import ru.heuri.sklyanka.domain.model.Fixtures
import ru.heuri.sklyanka.domain.model.Medication
import java.math.BigDecimal
import java.time.LocalTime

/**
 * «На сколько дней хватит остатка» — таблица `Примеры` структуры сценария из
 * `docs/tasks/SKL-12/acceptance.feature` целиком.
 *
 * Расхождение с макетом карточки лекарства зафиксировано отдельным тестом ниже:
 * числа `design/screens/MedCard.dc.html` между собой не сходятся, и за истину принята
 * формула, а не картинка.
 */
class StockForecastTest {

    @ParameterizedTest(name = "{0} intakes a day by {1} out of {2} last {3} days")
    @CsvSource(
        "2, 1,   9,  4",
        "2, 1,   10, 5",
        "1, 1,   9,  9",
        "3, 1,   9,  3",
        "2, 2,   9,  2",
        "2, 0.5, 9,  9",
        "2, 1,   1,  0",
        "2, 1,   0,  0",
    )
    fun `the forecast is the whole part of the division`(perDay: Int, dose: String, stock: String, expected: Int) {
        val forecast = StockForecast.of(medication(perDay = perDay, dose = dose, stock = stock))

        assertEquals(StockForecast.Days(expected), forecast)
    }

    @Test
    fun `a medication without stock tracking has no forecast at all`() {
        val medication = Fixtures.scheduledMedication().copy(stockLeft = null)

        assertEquals(StockForecast.NotTracked, StockForecast.of(medication))
    }

    @Test
    fun `an on-demand medication has no daily consumption to divide by`() {
        // ОВ-39: суточный расход приёма по потребности неизвестен, и число дней
        // приложение обещать не вправе.
        val medication = Fixtures.prnMedication().copy(stockLeft = BigDecimal("10"))

        assertEquals(StockForecast.Unknown, StockForecast.of(medication))
        assertEquals(null, StockForecast.dailyUse(medication))
    }

    @Test
    fun `a schedule without a single time has no daily consumption either`() {
        val medication = Fixtures.scheduledMedication().copy(times = emptyList())

        assertEquals(StockForecast.Unknown, StockForecast.of(medication))
    }

    @Test
    fun `the daily consumption is the number of intakes by the dose of one`() {
        val medication = medication(perDay = 3, dose = "0.5", stock = "9")

        assertEquals(BigDecimal("1.5"), StockForecast.dailyUse(medication))
    }

    @Test
    fun `the mock-up of the medication card does not add up and the formula wins`() {
        // ОВ-46. MedCard.dc.html показывает «2 приёма в сутки · по 2 таблетки» и тут же
        // «9 таблеток» с подписью «хватит на 4 дня». По формуле это два дня; четыре дня
        // получаются только при дозе в одну таблетку. Расхождение вынесено дизайну,
        // а в коде считается формула.
        val asDrawn = medication(perDay = 2, dose = "2", stock = "9")
        val asCaptioned = medication(perDay = 2, dose = "1", stock = "9")

        assertEquals(StockForecast.Days(2), StockForecast.of(asDrawn))
        assertEquals(StockForecast.Days(4), StockForecast.of(asCaptioned))
    }

    private fun medication(perDay: Int, dose: String, stock: String): Medication = Fixtures.scheduledMedication()
        .copy(
            doseAmount = BigDecimal(dose),
            times = (0 until perDay).map { LocalTime.of(MORNING_HOUR + it, 0) },
            stockLeft = BigDecimal(stock),
        )

    private companion object {
        const val MORNING_HOUR = 8
    }
}
