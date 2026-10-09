package ru.stohastika.sklyanka.domain.medication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.domain.model.TabletShape

/**
 * Применимость блоков формы: шесть форм выпуска на два режима приёма.
 *
 * Отдельно проверяется сброс: спрятанное, но сохранённое значение — источник записей,
 * нарушающих ИНВ-16, причём незаметно для человека.
 */
@DisplayName("Field applicability decides which blocks exist and clears what does not")
class FieldApplicabilityTest {

    @ParameterizedTest
    @EnumSource(MedicationForm::class)
    fun `the tablet shape block exists only for tablets`(form: MedicationForm) {
        val expected = form == MedicationForm.TABLET

        assertEquals(expected, Drafts.valid().copy(form = form).applicability().shapeVisible)
    }

    @Test
    fun `a scheduled course shows the times and hides the daily limit`() {
        val applicability = Drafts.valid().applicability()

        assertTrue(applicability.timesVisible)
        assertFalse(applicability.maxPerDayVisible)
    }

    @Test
    fun `an as needed course shows the daily limit and hides the times`() {
        val applicability = Drafts.validPrn().applicability()

        assertFalse(applicability.timesVisible)
        assertTrue(applicability.maxPerDayVisible)
    }

    @Test
    fun `the food relation block exists only for a scheduled course`() {
        // ОВ-83: приём «по потребности» связью с едой не управляет.
        assertTrue(Drafts.valid().applicability().foodRelationVisible)
        assertFalse(Drafts.validPrn().applicability().foodRelationVisible)
    }

    @ParameterizedTest
    @EnumSource(ScheduleMode::class)
    fun `the schedule blocks and the limit never show at the same time`(mode: ScheduleMode) {
        val applicability = Drafts.valid().copy(scheduleMode = mode).applicability()

        assertFalse(applicability.timesVisible && applicability.maxPerDayVisible)
    }

    @Test
    fun `an open ended course keeps the end date row switched off`() {
        assertFalse(Drafts.valid().copy(endDate = null).applicability().endDateEnabled)
        assertTrue(Drafts.valid().copy(endDate = Drafts.TODAY).applicability().endDateEnabled)
    }

    @Test
    fun `the purchase reminder follows the threshold being set at all`() {
        assertTrue(Drafts.valid().copy(stockThresholdDays = 5).applicability().stockThresholdEnabled)
        assertFalse(Drafts.valid().copy(stockThresholdDays = null).applicability().stockThresholdEnabled)
    }

    // --- Единица дозы ---------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(MedicationForm::class)
    fun `every release form yields a dose unit`(form: MedicationForm) {
        assertEquals(form.doseUnit(), Drafts.valid().copy(form = form).applicability().doseUnit)
    }

    @Test
    fun `every unit of the decision follows its release form`() {
        assertEquals(DoseUnit.TABLET, MedicationForm.TABLET.doseUnit())
        assertEquals(DoseUnit.CAPSULE, MedicationForm.CAPSULE.doseUnit())
        assertEquals(DoseUnit.MILLILITRE, MedicationForm.SYRUP.doseUnit())
        assertEquals(DoseUnit.DROP, MedicationForm.DROPS.doseUnit())
        assertEquals(DoseUnit.SPRAY, MedicationForm.SPRAY.doseUnit())
        assertEquals(DoseUnit.INJECTION, MedicationForm.INJECTION.doseUnit())
    }

    @Test
    fun `every release form yields a dose unit of its own`() {
        val units = MedicationForm.entries.map { it.doseUnit() }
        assertEquals(MedicationForm.entries.size, units.toSet().size)
    }

    @Test
    fun `a dose unit survives a round trip through its code`() {
        DoseUnit.entries.forEach { unit -> assertEquals(unit, DoseUnit.ofCode(unit.code)) }
        assertNull(DoseUnit.ofCode("no-such-unit"))
    }

    @Test
    fun `the retired dose value is not a unit any more`() {
        assertNull(DoseUnit.ofCode("dose"))
        assertTrue(DoseUnit.entries.all { it.code != "dose" })
    }

    @Test
    fun `a strength unit survives a round trip through its code`() {
        StrengthUnit.entries.forEach { unit -> assertEquals(unit, StrengthUnit.ofCode(unit.code)) }
        // Незнакомая единица в коде латиницей: русские написания («мгр») живут
        // в текстах норматива, а в Kotlin их нет вовсе (НФТ-42).
        assertNull(StrengthUnit.ofCode("mgm"))
    }

    // --- Сброс неприменимого --------------------------------------------------------------

    @Test
    fun `changing the release form to a spray clears the tablet shape`() {
        val draft = Drafts.valid().copy(shape = TabletShape.OVAL, form = MedicationForm.SPRAY)

        assertNull(draft.normalized().shape)
    }

    @Test
    fun `switching to as needed clears the times and keeps the limit`() {
        val draft = Drafts.valid().copy(scheduleMode = ScheduleMode.PRN, maxPerDay = 3)

        val normalized = draft.normalized()

        assertEquals(emptyList<Nothing>(), normalized.times)
        assertEquals(3, normalized.maxPerDay)
    }

    @Test
    fun `switching to as needed keeps the chosen food relation`() {
        // Режим делает выбор нередактируемым, а не отменяет его: возврат к расписанию
        // обязан показать прежнюю связь с едой (SKL-49).
        val draft = Drafts.valid().copy(scheduleMode = ScheduleMode.PRN, foodRelation = FoodRelation.BEFORE_MEAL)

        assertEquals(FoodRelation.BEFORE_MEAL, draft.normalized().foodRelation)
    }

    @Test
    fun `switching back to a schedule clears the daily limit`() {
        val draft = Drafts.validPrn().copy(scheduleMode = ScheduleMode.SCHEDULED, times = listOf(Drafts.MORNING))

        val normalized = draft.normalized()

        assertNull(normalized.maxPerDay)
        assertEquals(listOf(Drafts.MORNING), normalized.times)
    }

    @Test
    fun `normalising an applicable draft changes nothing`() {
        val draft = Drafts.valid().copy(shape = TabletShape.ROUND_SCORED)

        assertEquals(draft, draft.normalized())
    }

    @Test
    fun `the intake count is derived from the list of times and is not stored`() {
        assertEquals(2, Drafts.valid().intakesPerDay)
        assertEquals(0, Drafts.validPrn().intakesPerDay)
    }
}
