package ru.stohastika.sklyanka.feature.today

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.medication.DoseUnit
import ru.stohastika.sklyanka.domain.model.FoodRelation
import ru.stohastika.sklyanka.domain.model.Medication
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.domain.model.ScheduleMode
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Единицы дозы и дозировки в подписи приёма на «Сегодня» (Р-45, SKL-126).
 *
 * Доза называлась знаком «шт» или выдуманной «дозой», а единица дозировки печаталась кодом
 * («48 mg»). Здесь проверяется, что каждая из шести единиц дозы печатается своим словом,
 * склонённым по числу, а единица дозировки — словом языка.
 *
 * Ошибка «капля показана миллилитром» ловится именно перебором единиц: `when` без `else`
 * не даёт забыть значение, но не мешает поставить ему чужую строку.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class TodayUnitTextTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyDoseUnitIsPrintedWithItsOwnWord() {
        // Число и его единица не разрываются: между ними неразрывный пробел (SKL-59 TDD §2.2,
        // страж `LocalizationResourcesTest`), поэтому в ожидании стоит \u00A0, а не пробел.
        val lines = listOf(
            MedicationForm.TABLET to DoseUnit.TABLET to "20\u00A0таблеток",
            MedicationForm.CAPSULE to DoseUnit.CAPSULE to "20\u00A0капсул",
            MedicationForm.SYRUP to DoseUnit.MILLILITRE to "20\u00A0мл",
            MedicationForm.DROPS to DoseUnit.DROP to "20\u00A0капель",
            MedicationForm.SPRAY to DoseUnit.SPRAY to "20\u00A0впрысков",
            MedicationForm.INJECTION to DoseUnit.INJECTION to "20\u00A0инъекций",
        )

        composeRule.setContent {
            SklyankaTheme {
                Column {
                    lines.forEach { (formAndUnit, _) ->
                        val (form, unit) = formAndUnit
                        Text(medication(form, unit).doseCaption())
                    }
                }
            }
        }

        lines.forEach { (_, expected) ->
            composeRule.onNodeWithText(expected).assertExists()
        }
    }

    @Test
    fun theStrengthUnitIsPrintedByItsWordNotByItsCode() {
        composeRule.setContent {
            SklyankaTheme {
                Text(medication(MedicationForm.TABLET, DoseUnit.TABLET, strengthUnit = "mcg").doseCaption())
            }
        }

        composeRule.onNodeWithText("20\u00A0таблеток · 250 мкг").assertExists()
    }

    /**
     * Дробная доза берёт форму дроби: «0,5 таблетки», а не «0,5 таблеток» (SKL-138).
     *
     * Проверяется слово, а не вся строка: число печатается своей формой, а разделитель дроби —
     * отдельный дефект формата числа (НФТ-43), и утверждение всей строки закрепило бы его здесь.
     */
    @Test
    fun aFractionalDoseTakesTheFractionForm() {
        composeRule.setContent {
            SklyankaTheme {
                Text(
                    medication(MedicationForm.TABLET, DoseUnit.TABLET, dose = BigDecimal("0.5")).doseCaption(),
                )
            }
        }

        composeRule.onNodeWithText("таблетки", substring = true).assertExists()
    }

    private fun medication(
        form: MedicationForm,
        unit: DoseUnit,
        strengthUnit: String? = null,
        dose: BigDecimal = BigDecimal("20"),
    ): Medication = Medication(
        id = "med-1",
        profileId = "profile-1",
        name = "med-a",
        form = form,
        shape = null,
        doseAmount = dose,
        doseUnit = unit.code,
        strength = strengthUnit?.let { BigDecimal("250") },
        strengthUnit = strengthUnit,
        foodRelation = FoodRelation.ANY,
        scheduleMode = ScheduleMode.SCHEDULED,
        maxPerDay = null,
        times = listOf(java.time.LocalTime.of(8, 0)),
        startDate = LocalDate.of(2026, 9, 1),
        endDate = null,
        stockLeft = null,
        packageSize = null,
        stockThresholdDays = null,
        archivedAt = null,
        archiveReason = null,
    )
}
