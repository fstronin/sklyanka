package ru.stohastika.sklyanka.domain.medication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Девять правил формы по отдельности и вместе (`acceptance.feature`, группа `@validation`).
 *
 * Проверяется именно состав списка, а не сам факт отказа: правило, вернувшее «что-то не так»,
 * экрану бесполезно — ему нужно поле, к которому прокручиваться.
 */
@DisplayName("Form rules reject a meaningless course and name the field")
class ValidateMedicationDraftTest {

    private val validate = ValidateMedicationDraft()

    @Test
    fun `a filled in course passes every rule`() {
        assertEquals(emptyList<DraftViolation>(), validate(Drafts.valid()))
    }

    @Test
    fun `an as needed course with a limit passes every rule`() {
        assertEquals(emptyList<DraftViolation>(), validate(Drafts.validPrn()))
    }

    // --- Название -------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "\t"])
    fun `a name made of whitespace counts as empty`(name: String) {
        val violations = validate(Drafts.valid().copy(name = name))

        assertEquals(listOf(DraftViolation.NameEmpty), violations)
        assertEquals(DraftField.NAME, violations.first().field)
    }

    // --- Доза и дозировка -----------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = ["0", "-2"])
    fun `a dose of zero or below is rejected`(dose: String) {
        assertEquals(listOf(DraftViolation.DoseNotPositive), validate(dose(dose)))
    }

    @Test
    fun `a missing dose is rejected the same way as a zero one`() {
        assertEquals(listOf(DraftViolation.DoseNotPositive), validate(Drafts.valid().copy(doseAmount = null)))
    }

    @Test
    fun `a strength without its unit is rejected`() {
        assertEquals(
            listOf(DraftViolation.StrengthWithoutUnit),
            validate(Drafts.valid().copy(strengthUnit = null)),
        )
    }

    @Test
    fun `a blank unit counts as no unit at all`() {
        assertEquals(
            listOf(DraftViolation.StrengthWithoutUnit),
            validate(Drafts.valid().copy(strengthUnit = "  ")),
        )
    }

    @Test
    fun `a unit without its strength is rejected`() {
        assertEquals(
            listOf(DraftViolation.UnitWithoutStrength),
            validate(Drafts.valid().copy(strength = null)),
        )
    }

    @Test
    fun `neither strength nor unit is a complete course`() {
        assertTrue(validate(Drafts.valid().copy(strength = null, strengthUnit = null)).isEmpty())
    }

    // --- Расписание -----------------------------------------------------------------------

    @Test
    fun `a scheduled course without a single time is rejected`() {
        val violations = validate(Drafts.valid().copy(times = emptyList()))

        assertEquals(listOf(DraftViolation.NoIntakeTimes), violations)
        assertEquals(DraftField.TIMES, violations.first().field)
    }

    @Test
    fun `a repeated time is rejected and the repeated value is named`() {
        // Два одинаковых времени породили бы два приёма на одну минуту и два напоминания подряд.
        val violations = validate(
            Drafts.valid().copy(times = listOf(Drafts.MORNING, Drafts.EVENING, Drafts.MORNING)),
        )

        assertEquals(listOf(DraftViolation.DuplicateTime(Drafts.MORNING)), violations)
    }

    // --- «По потребности» -----------------------------------------------------------------

    @Test
    fun `an as needed course without a daily limit is rejected`() {
        val violations = validate(Drafts.validPrn().copy(maxPerDay = null))

        assertEquals(listOf(DraftViolation.MaxPerDayMissing), violations)
        assertEquals(DraftField.MAX_PER_DAY, violations.first().field)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1])
    fun `a daily limit of zero or below is rejected`(limit: Int) {
        assertEquals(
            listOf(DraftViolation.MaxPerDayNotPositive),
            validate(Drafts.validPrn().copy(maxPerDay = limit)),
        )
    }

    // --- Период приёма --------------------------------------------------------------------

    @Test
    fun `an end date before the start date is rejected`() {
        val violations = validate(Drafts.valid().copy(endDate = LocalDate.of(2026, 9, 12)))

        assertEquals(listOf(DraftViolation.EndDateBeforeStart), violations)
        assertEquals(DraftField.PERIOD, violations.first().field)
    }

    @Test
    fun `an end date equal to the start date is a one day course and passes`() {
        assertTrue(validate(Drafts.valid().copy(endDate = Drafts.TODAY)).isEmpty())
    }

    @Test
    fun `an empty end date means an open ended course and is not a violation`() {
        assertTrue(validate(Drafts.valid().copy(endDate = null)).isEmpty())
    }

    // --- Остаток --------------------------------------------------------------------------

    @Test
    fun `a negative stock is rejected`() {
        val violations = validate(Drafts.valid().copy(stockLeft = BigDecimal("-5")))

        assertEquals(listOf(DraftViolation.StockNegative), violations)
        assertEquals(DraftField.STOCK, violations.first().field)
    }

    @Test
    fun `an empty stock means the person does not track it and is not a violation`() {
        assertTrue(validate(Drafts.valid().copy(stockLeft = null)).isEmpty())
    }

    // --- Вместе ---------------------------------------------------------------------------

    @Test
    fun `every broken rule is reported, not only the first one`() {
        val violations = validate(
            Drafts.valid().copy(
                name = " ",
                doseAmount = BigDecimal.ZERO,
                times = listOf(Drafts.MORNING, Drafts.MORNING),
                stockLeft = BigDecimal("-1"),
            ),
        )

        assertEquals(4, violations.size)
        assertTrue(DraftViolation.NameEmpty in violations)
        assertTrue(DraftViolation.DoseNotPositive in violations)
        assertTrue(DraftViolation.DuplicateTime(Drafts.MORNING) in violations)
        assertTrue(DraftViolation.StockNegative in violations)
    }

    @Test
    fun `the first violation is the topmost block of the form`() {
        // Экран прокручивается к первому нарушению списка, поэтому порядок здесь — контракт,
        // а не случайность реализации.
        val violations = validate(
            Drafts.valid().copy(name = "", doseAmount = BigDecimal.ZERO, stockLeft = BigDecimal("-1")),
        )

        assertEquals(DraftField.NAME, violations.first().field)
        assertEquals(DraftField.STOCK, violations.last().field)
    }

    @Test
    fun `rules of the other schedule mode never fire`() {
        // У курса по расписанию ограничение приёмов неприменимо, у курса по потребности —
        // список времён. Правило, сработавшее на неприменимом поле, называет человеку
        // блок, которого он на экране не видит.
        val scheduled = validate(Drafts.valid().normalized())
        val asNeeded = validate(Drafts.validPrn().normalized())

        assertFalse(DraftViolation.MaxPerDayMissing in scheduled)
        assertFalse(DraftViolation.NoIntakeTimes in asNeeded)
    }

    private fun dose(value: String) = Drafts.valid().copy(doseAmount = BigDecimal(value))
}
