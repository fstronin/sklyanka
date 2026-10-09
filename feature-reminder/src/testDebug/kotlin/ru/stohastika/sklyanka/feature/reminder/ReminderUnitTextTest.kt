package ru.stohastika.sklyanka.feature.reminder

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import ru.stohastika.sklyanka.domain.model.MedicationForm
import ru.stohastika.sklyanka.ui.theme.SklyankaTheme
import java.math.BigDecimal

/**
 * Единицы дозы и дозировки в строке напоминания (Р-45, SKL-126).
 *
 * Правило то же, что на «Сегодня» и в карточке лекарства: доза печатается словом своей
 * формы со склонением по числу, а единица дозировки — словом языка, а не кодом.
 * У модулей свои копии строк намеренно (`feature-*` не зависят друг от друга), поэтому
 * и проверка у каждого своя: опечатка в копии иначе не видна до кадра.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "ru-rRU-w390dp-h2400dp-mdpi")
class ReminderUnitTextTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyDoseUnitIsPrintedWithItsOwnWord() {
        val lines = listOf(
            MedicationForm.TABLET to "2 таблетки",
            MedicationForm.CAPSULE to "2 капсулы",
            MedicationForm.SYRUP to "5 мл",
            MedicationForm.DROPS to "3 капли",
            MedicationForm.SPRAY to "2 впрыска",
            MedicationForm.INJECTION to "2 инъекции",
        )
        val amounts = listOf("2", "2", "5", "3", "2", "2")

        composeRule.setContent {
            SklyankaTheme {
                Column {
                    lines.forEachIndexed { index, (form, _) ->
                        Text(row(form, BigDecimal(amounts[index])).doseLine())
                    }
                }
            }
        }

        lines.forEach { (_, expected) -> composeRule.onNodeWithText(expected).assertExists() }
    }

    @Test
    fun theStrengthUnitIsPrintedByItsWordNotByItsCode() {
        composeRule.setContent {
            SklyankaTheme { Text(row(MedicationForm.TABLET, BigDecimal("2"), strengthUnit = "mcg").doseLine()) }
        }

        composeRule.onNodeWithText("2 таблетки · 50 мкг").assertExists()
    }

    private fun row(form: MedicationForm, amount: BigDecimal, strengthUnit: String? = null): ReminderRow = ReminderRow(
        intakeId = "intake-1",
        name = "med-a",
        form = form,
        doseAmount = amount,
        strength = strengthUnit?.let { BigDecimal("50") },
        strengthUnit = strengthUnit,
    )
}
