package ru.stohastika.sklyanka.domain.medication

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import java.math.BigDecimal

/**
 * Единицы измерения на границе черновика и записи (Р-45, SKL-126).
 *
 * Черновик держит единицу дозировки значением закрытого списка, а запись — кодом строки:
 * так единица переживает и файл переноса данных, и более старую версию приложения,
 * код которой форма не знает (МГ-6). Здесь проверяется именно этот переход — в обе стороны.
 */
class MedicationDraftUnitsTest {

    @Test
    fun `the chosen unit is written as its code`() {
        val draft = Drafts.valid().copy(strengthUnit = StrengthUnit.INTERNATIONAL_UNIT)

        assertEquals(StrengthUnit.INTERNATIONAL_UNIT.code, draft.toMedication(Drafts.NEW_ID).strengthUnit)
    }

    @Test
    fun `a dose unit is written as its own code`() {
        val draft = Drafts.valid().copy(form = MedicationForm.DROPS)

        assertEquals(DoseUnit.DROP.code, draft.toMedication(Drafts.NEW_ID).doseUnit)
    }

    @Test
    fun `a known unit code is read back into the draft`() {
        val medication = record(strengthUnit = StrengthUnit.MICROGRAM.code)

        assertEquals(StrengthUnit.MICROGRAM, MedicationDraft.of(medication).strengthUnit)
    }

    @Test
    fun `an unknown unit code stays in the record and gives no unit to the draft`() {
        // Чужая единица — из файла переноса или из более старой версии. Запись её хранит
        // и показывает как есть; форма показывает выбор заново, а не подставляет миллиграмм
        // молча: подмена переписала бы медицинские данные. Код латиницей: русские написания
        // остаются текстами норматива (НФТ-42).
        val medication = record(strengthUnit = UNKNOWN_UNIT)

        assertEquals(UNKNOWN_UNIT, medication.strengthUnit)
        assertNull(MedicationDraft.of(medication).strengthUnit)
        assertEquals(BigDecimal("48"), MedicationDraft.of(medication).strength)
    }

    private fun record(strengthUnit: String?): Medication = Medication(
        id = Drafts.MEDICATION_ID,
        profileId = Drafts.PROFILE_ID,
        name = "med-a",
        form = MedicationForm.TABLET,
        shape = null,
        doseAmount = BigDecimal("2"),
        doseUnit = DoseUnit.TABLET.code,
        strength = BigDecimal("48"),
        strengthUnit = strengthUnit,
        foodRelation = FoodRelation.ANY,
        scheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay = null,
        times = listOf(Drafts.MORNING),
        startDate = Drafts.TODAY,
        endDate = null,
        stockLeft = null,
        packageSize = null,
        stockThresholdDays = null,
        archivedAt = null,
        archiveReason = null,
    )

    private companion object {

        /** Единица, которой нет в закрытом списке: чужая запись, а не наша форма. */
        const val UNKNOWN_UNIT = "old-build-unit"
    }
}
